package com.forensics.platform.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.forensics.platform.service.EvidenceStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EvidenceLifecycleIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private EvidenceStorageService storageService;

    @Test
    void uploadHashesStoresAndImportsWithProvenance() throws Exception {
        String admin = admin();
        long caseId = createCase(admin, "Aarav Sharma");
        byte[] content = sample("sample-basic.xml");

        var response = upload(admin, caseId, "sample-basic.xml", content);
        assertThat(response.getResponse().getStatus()).isEqualTo(202);
        JsonNode uploaded = body(response);
        JsonNode file = uploaded.get("file");
        assertThat(file.get("sha256").asText()).isEqualTo(sha256(content));
        assertThat(file.get("sizeBytes").asLong()).isEqualTo(content.length);
        assertThat(file.get("processingStatus").asText()).isEqualTo("PROCESSED");
        assertThat(file.get("recordsExtracted").asInt()).isEqualTo(5);
        assertThat(uploaded.get("job").get("status").asText()).isEqualTo("SUCCEEDED");
        long fileId = file.get("id").asLong();

        JsonNode page = body(mvc.perform(auth(get("/api/v1/evidence").param("caseId", String.valueOf(caseId)).param("size", "50"), admin)).andReturn());
        assertThat(page.get("totalElements").asInt()).isEqualTo(5);
        JsonNode first = page.get("items").get(0);
        assertThat(first.get("sourceFileId").asLong()).isEqualTo(fileId);
        assertThat(first.get("hashSha256").asText()).isEqualTo(sha256(content));
        assertThat(first.get("sourceRecordRef").asText()).startsWith("/UFDR/");
        assertThat(first.get("recordHash").asText()).hasSize(64);

        long evidenceId = first.get("id").asLong();
        mvc.perform(auth(get("/api/v1/evidence/" + evidenceId), admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.evidence.id").value(evidenceId))
                .andExpect(jsonPath("$.normalized").exists());

        // The same file cannot be imported twice into the same case.
        assertThat(upload(admin, caseId, "copy.xml", content).getResponse().getStatus()).isEqualTo(409);

        // Integrity verification succeeds, then detects tampering with the stored original.
        mvc.perform(auth(post("/api/v1/evidence-files/" + fileId + "/verify"), admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"));
        Path stored = storageService.resolve("case-" + caseId + "/" + sha256(content) + ".xml");
        stored.toFile().setWritable(true);
        Files.writeString(stored, "tampered", StandardCharsets.UTF_8);
        mvc.perform(auth(post("/api/v1/evidence-files/" + fileId + "/verify"), admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MISMATCH"));
        JsonNode custody = body(mvc.perform(auth(get("/api/v1/evidence-files/" + fileId + "/custody"), admin)).andReturn());
        List<String> actions = new ArrayList<>();
        custody.forEach(event -> actions.add(event.get("action").asText()));
        assertThat(actions).contains("EVIDENCE_FILE_RECEIVED", "EVIDENCE_IMPORTED", "EVIDENCE_FILE_VERIFIED");

        // Cases holding evidence cannot be deleted.
        mvc.perform(auth(delete("/api/v1/cases/" + caseId), admin)).andExpect(status().isConflict());
    }

    @Test
    void invalidFilesAreRejectedOrFailWithoutPartialRecords() throws Exception {
        String admin = admin();
        long caseId = createCase(admin, "Aarav Sharma");

        assertThat(upload(admin, caseId, "notes.txt", "hello".getBytes(StandardCharsets.UTF_8)).getResponse().getStatus()).isEqualTo(400);
        assertThat(upload(admin, caseId, "fake.xml", new byte[]{0x50, 0x4B, 0x03, 0x04, 1, 2}).getResponse().getStatus()).isEqualTo(400);
        assertThat(upload(admin, caseId, "empty.xml", new byte[0]).getResponse().getStatus()).isEqualTo(400);

        byte[] xxe = """
                <?xml version="1.0"?>
                <!DOCTYPE foo [ <!ENTITY xxe SYSTEM "file:///etc/hosts"> ]>
                <UFDR><sms><sender>1</sender><message>&xxe;</message></sms></UFDR>
                """.getBytes(StandardCharsets.UTF_8);
        JsonNode xxeJob = body(upload(admin, caseId, "xxe.xml", xxe)).get("job");
        assertThat(xxeJob.get("status").asText()).isEqualTo("FAILED");
        assertThat(xxeJob.get("errorMessage").asText()).contains("DOCTYPE");
        assertThat(xxeJob.get("canRetry").asBoolean()).isTrue();

        byte[] malformed = "<UFDR><sms><sender>1</sender><message>x</sms>".getBytes(StandardCharsets.UTF_8);
        JsonNode malformedJob = body(upload(admin, caseId, "broken.xml", malformed)).get("job");
        assertThat(malformedJob.get("status").asText()).isEqualTo("FAILED");
        assertThat(malformedJob.get("errorMessage").asText()).startsWith("Malformed XML");

        JsonNode count = body(mvc.perform(auth(get("/api/v1/evidence/count").param("caseId", String.valueOf(caseId)), admin)).andReturn());
        assertThat(count.get("totalElements").asInt()).isZero();

        // Retrying the malformed job runs again from the preserved original and fails the same way.
        long jobId = malformedJob.get("id").asLong();
        mvc.perform(auth(post("/api/v1/processing/jobs/" + jobId + "/retry"), admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.attempts").value(2));
        JsonNode detail = body(mvc.perform(auth(get("/api/v1/processing/jobs/" + jobId), admin)).andReturn());
        assertThat(detail.get("logs").size()).isGreaterThan(2);
    }

    @Test
    void archivesAreReadWithoutExtractionAndPreferReportXml() throws Exception {
        String admin = admin();
        long caseId = createCase(admin, "Aarav Sharma");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("../../outside.xml"));
            zip.write("<UFDR><sms><sender>1</sender><message>decoy</message></sms></UFDR>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("extraction/report.xml"));
            zip.write(sample("sample-basic.xml"));
            zip.closeEntry();
        }
        JsonNode uploaded = body(upload(admin, caseId, "phone.ufdr", bytes.toByteArray()));
        assertThat(uploaded.get("job").get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(uploaded.get("file").get("recordsExtracted").asInt()).isEqualTo(5);
        assertThat(uploaded.get("file").get("sourceFormat").asText()).isEqualTo("UFDR");
        assertThat(Files.exists(Path.of(System.getProperty("java.io.tmpdir"), "outside.xml"))).isFalse();
    }

    @Test
    void timelineCorrelationWorkspaceReportsAndAssistantUseRealRecords() throws Exception {
        String admin = admin();
        long caseId = createCase(admin, "Aarav Sharma");
        String investigator = investigator();
        assertThat(body(upload(investigator, caseId, "device-a-android.xml", sample("device-a-android.xml"))).get("job").get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(body(upload(investigator, caseId, "device-b-iphone.xml", sample("device-b-iphone.xml"))).get("job").get("status").asText()).isEqualTo("SUCCEEDED");

        // Timeline: chronological, undated kept separately, original timestamps preserved.
        JsonNode timeline = body(mvc.perform(auth(get("/api/v1/timeline").param("caseId", String.valueOf(caseId)), investigator)).andReturn());
        List<String> times = new ArrayList<>();
        timeline.get("events").forEach(event -> times.add(event.get("eventTime").asText()));
        assertThat(times).isSortedAccordingTo(String::compareTo);
        // 4 contacts (no timestamps) + 1 unparseable call + 1 SMS without a timestamp.
        assertThat(timeline.get("totalUndated").asInt()).isEqualTo(6);
        assertThat(timeline.get("events").findValuesAsText("timestampStatus")).contains("EXACT", "ASSUMED_UTC");
        assertThat(timeline.toString()).contains("CORROBORATED");

        // Correlation: shared identifiers across the two devices are inferred and cite records.
        JsonNode graph = body(mvc.perform(auth(get("/api/v1/cases/" + caseId + "/correlations"), investigator)).andReturn());
        JsonNode shared = null;
        for (JsonNode relationship : graph.get("relationships")) {
            assertThat(relationship.get("evidenceIds").size()).isPositive();
            if (relationship.get("type").asText().equals("SHARED_IDENTIFIER")) {
                shared = relationship;
            }
        }
        assertThat(shared).isNotNull();
        assertThat(shared.get("basis").asText()).isEqualTo("INFERRED");

        // Related records for one SMS include the same message captured on the other device.
        JsonNode evidence = body(mvc.perform(auth(get("/api/v1/evidence").param("caseId", String.valueOf(caseId))
                .param("search", "invoice copies").param("size", "10"), investigator)).andReturn());
        long smsId = evidence.get("items").get(0).get("id").asLong();
        JsonNode related = body(mvc.perform(auth(get("/api/v1/evidence/" + smsId + "/related"), investigator)).andReturn());
        assertThat(related.findValuesAsText("relationshipType")).contains("SAME_COMMUNICATION_OTHER_DEVICE");

        // Workspace: evidence from another case is rejected; AI findings require review by someone else.
        long otherCase = createCase(admin, "Someone Else");
        byte[] otherFile = sample("sample-basic.xml");
        long foreignEvidence = body(upload(admin, otherCase, "sample-basic.xml", otherFile)).get("file").get("id").asLong();
        JsonNode foreign = body(mvc.perform(auth(get("/api/v1/evidence").param("fileId", String.valueOf(foreignEvidence)), admin)).andReturn());
        long foreignId = foreign.get("items").get(0).get("id").asLong();
        Map<String, Object> badFinding = Map.of("title", "x", "description", "y", "basis", "VERIFIED_FACT", "significance", "LOW",
                "evidenceIds", List.of(foreignId));
        mvc.perform(auth(post("/api/v1/cases/" + caseId + "/workspace/findings"), investigator).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(badFinding))).andExpect(status().isBadRequest());
        mvc.perform(auth(get("/api/v1/evidence/" + foreignId), investigator)).andExpect(status().isForbidden());

        Map<String, Object> aiFinding = Map.of("title", "Pickup coordination", "description", "Messages arrange an 11:00 pickup.",
                "basis", "VERIFIED_FACT", "significance", "MEDIUM", "evidenceIds", List.of(smsId), "aiGenerated", true, "aiEngine", "extractive");
        JsonNode finding = body(mvc.perform(auth(post("/api/v1/cases/" + caseId + "/workspace/findings"), investigator)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(aiFinding))).andExpect(status().isOk()).andReturn());
        assertThat(finding.get("basis").asText()).isEqualTo("AI_SUGGESTED");
        assertThat(finding.get("reviewStatus").asText()).isEqualTo("NEEDS_REVIEW");
        long findingId = finding.get("id").asLong();
        mvc.perform(auth(post("/api/v1/cases/" + caseId + "/workspace/findings/" + findingId + "/review"), investigator)
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"REVIEWED\"}")).andExpect(status().isForbidden());

        // Report: snapshot stored with its hash; PDF rendered from it; unreviewed AI content is disclosed.
        JsonNode report = body(mvc.perform(auth(post("/api/v1/cases/" + caseId + "/reports"), investigator)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk()).andReturn());
        long reportId = report.get("id").asLong();
        assertThat(report.get("contentSha256").asText()).hasSize(64);
        assertThat(report.get("unreviewedAiFindings").asInt()).isEqualTo(1);
        JsonNode reportDetail = body(mvc.perform(auth(get("/api/v1/reports/" + reportId), investigator)).andReturn());
        assertThat(reportDetail.get("snapshotVerified").asBoolean()).isTrue();
        assertThat(reportDetail.get("snapshot").get("evidenceSources").size()).isEqualTo(2);
        assertThat(reportDetail.get("snapshot").get("limitations").toString()).contains("AI-assisted finding(s) have not been reviewed");
        byte[] pdf = mvc.perform(auth(get("/api/v1/reports/" + reportId + "/pdf"), investigator)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        mvc.perform(auth(post("/api/v1/reports/" + reportId + "/review"), reviewer()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REVIEWED"));

        // Assistant: the AI service is unreachable in tests, so a labelled fallback citing case records is returned.
        JsonNode answer = body(mvc.perform(auth(post("/api/v1/cases/" + caseId + "/assistant"), investigator)
                .contentType(MediaType.APPLICATION_JSON).content("{\"task\":\"SEARCH\",\"question\":\"north gate\"}"))
                .andExpect(status().isOk()).andReturn());
        assertThat(answer.get("status").asText()).isEqualTo("FALLBACK");
        assertThat(answer.get("citations").size()).isPositive();
        for (JsonNode citation : answer.get("citations")) {
            mvc.perform(auth(get("/api/v1/evidence/" + citation.get("evidenceId").asLong()), investigator)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.evidence.caseId").value(caseId));
        }
        JsonNode noMatch = body(mvc.perform(auth(post("/api/v1/cases/" + caseId + "/assistant"), investigator)
                .contentType(MediaType.APPLICATION_JSON).content("{\"task\":\"ASK\",\"question\":\"zebra helicopter\"}")).andReturn());
        assertThat(noMatch.get("status").asText()).isEqualTo("NO_EVIDENCE");
    }

    @Test
    void auditChainVerifiesAndDashboardIsScoped() throws Exception {
        String admin = admin();
        createCase(admin, "Aarav Sharma");
        mvc.perform(auth(post("/api/v1/audit/verify"), admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INTACT"));
        mvc.perform(auth(get("/api/v1/dashboard"), investigator())).andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("ASSIGNED_CASES"));
        mvc.perform(auth(get("/api/v1/dashboard"), admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("ALL_CASES"));
    }

    private static String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }
}
