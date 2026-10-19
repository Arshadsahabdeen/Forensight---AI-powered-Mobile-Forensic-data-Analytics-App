package com.forensics.platform.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityAndCrudIntegrationTest extends IntegrationTestSupport {

    @Test
    void protectedEndpointsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/v1/cases")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/evidence")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/cases").header("Authorization", "Bearer not-a-jwt")).andExpect(status().isUnauthorized());
    }

    @Test
    void legacyUploadPathIsNoLongerAnonymous() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.xml", "application/xml", sample("sample-basic.xml"));
        mvc.perform(multipart("/api/v1/ufdr/upload").file(file).param("caseId", "1")).andExpect(status().isUnauthorized());
    }

    @Test
    void methodSecurityDenialsAreForbiddenNotServerErrors() throws Exception {
        String reviewer = reviewer();
        Map<String, Object> request = Map.of("caseNumber", "RV-1", "title", "t", "description", "d", "status", "OPEN",
                "priority", "LOW", "leadInvestigator", "x", "category", "c", "caseType", "t");
        mvc.perform(auth(post("/api/v1/cases"), reviewer).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/v1/audit"), investigator())).andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/v1/users"), reviewer)).andExpect(status().isForbidden());
    }

    @Test
    void investigatorsOnlyAccessAssignedCases() throws Exception {
        String admin = admin();
        long assigned = createCase(admin, "Aarav Sharma");
        long unassigned = createCase(admin, "Someone Else");
        String investigator = investigator();

        mvc.perform(auth(get("/api/v1/cases/" + assigned), investigator)).andExpect(status().isOk())
                .andExpect(jsonPath("$.canEdit").value(true));
        mvc.perform(auth(get("/api/v1/cases/" + unassigned), investigator)).andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/v1/timeline").param("caseId", String.valueOf(unassigned)), investigator)).andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/v1/cases/" + unassigned + "/correlations"), investigator)).andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/v1/cases/" + unassigned + "/workspace"), investigator)).andExpect(status().isForbidden());

        var list = body(mvc.perform(auth(get("/api/v1/cases").param("size", "100"), investigator)).andReturn());
        assertThat(list.get("items").findValuesAsText("leadInvestigator")).allMatch(lead -> lead.equalsIgnoreCase("Aarav Sharma"));

        // Reviewers can read every case but not change it.
        String reviewer = reviewer();
        mvc.perform(auth(get("/api/v1/cases/" + unassigned), reviewer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.canEdit").value(false));
    }

    @Test
    void caseCrudWithValidation() throws Exception {
        String admin = admin();
        mvc.perform(auth(post("/api/v1/cases"), admin).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.caseNumber").exists());

        long id = createCase(admin, "Aarav Sharma");
        var detail = body(mvc.perform(auth(get("/api/v1/cases/" + id), admin)).andReturn());
        String number = detail.get("overview").get("caseNumber").asText();

        Map<String, Object> update = Map.of("caseNumber", number, "title", "Renamed", "description", "Updated", "status", "ON_HOLD",
                "priority", "HIGH", "leadInvestigator", "Aarav Sharma", "category", "Test", "caseType", "Mobile Forensics");
        mvc.perform(auth(put("/api/v1/cases/" + id), admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(update)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Renamed")).andExpect(jsonPath("$.status").value("ON_HOLD"));

        mvc.perform(auth(get("/api/v1/cases").param("status", "NOT_A_STATUS"), admin)).andExpect(status().isBadRequest());

        mvc.perform(auth(delete("/api/v1/cases/" + id), investigator())).andExpect(status().isForbidden());
        mvc.perform(auth(delete("/api/v1/cases/" + id), admin)).andExpect(status().isOk());
        mvc.perform(auth(get("/api/v1/cases/" + id), admin)).andExpect(status().isNotFound());
    }

    @Test
    void deviceCrudIsCaseScoped() throws Exception {
        String admin = admin();
        long caseId = createCase(admin, "Aarav Sharma");
        long otherCase = createCase(admin, "Someone Else");
        String investigator = investigator();

        Map<String, Object> device = new java.util.HashMap<>(Map.of("caseId", caseId, "manufacturer", "Google", "model", "Pixel 8",
                "deviceName", "Seized Pixel", "operatingSystem", "Android", "acquisitionMethod", "LOGICAL_EXTRACTION",
                "acquisitionStatus", "SEIZED"));
        device.put("imei", "12345");
        mvc.perform(auth(post("/api/v1/devices"), investigator).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(device)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.imei").exists());

        device.put("imei", "");
        var created = body(mvc.perform(auth(post("/api/v1/devices"), investigator).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(device))).andExpect(status().isOk()).andReturn());
        long deviceId = created.get("id").asLong();
        assertThat(created.get("imei").isNull()).isTrue();

        device.put("caseId", otherCase);
        mvc.perform(auth(post("/api/v1/devices"), investigator).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(device)))
                .andExpect(status().isForbidden());

        mvc.perform(auth(get("/api/v1/devices/" + deviceId), investigator)).andExpect(status().isOk());
        mvc.perform(auth(get("/api/v1/devices/" + deviceId), otherInvestigator())).andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/v1/devices").param("status", "BOGUS"), admin)).andExpect(status().isBadRequest());

        mvc.perform(auth(delete("/api/v1/devices/" + deviceId), admin)).andExpect(status().isOk());
    }
}
