package com.forensics.platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.forensics.platform.dto.InvestigationReportDto;
import com.forensics.platform.dto.InvestigationReportDto.*;
import com.forensics.platform.dto.ReportDtos.*;
import com.forensics.platform.dto.TimelineEventDto;
import com.forensics.platform.dto.WorkspaceDtos.FindingResponse;
import com.forensics.platform.dto.EvidenceDtos.EvidenceResponse;
import com.forensics.platform.entity.*;
import com.forensics.platform.mapper.EvidenceMapper;
import com.forensics.platform.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates case reports from stored data. Each report keeps the exact JSON snapshot it was built from
 * and that snapshot's SHA-256; views and PDF exports are rendered from the stored snapshot, so a report
 * can be reproduced and checked for alteration at any time.
 */
@Service
public class ReportService {

    public static final String SCHEMA_VERSION = "forensight-report/1";
    private static final int MAX_EVENTS = 1000;
    private static final DateTimeFormatter NUMBER_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    static final List<String> METHODOLOGY = List.of(
            "Original extraction files were received through the platform's upload function. A SHA-256 digest was computed while each file was streamed to storage, and the file was stored read-only under a name derived from that digest.",
            "Before each file was parsed, its stored SHA-256 was recomputed and compared with the value recorded at import. Investigators can repeat this verification at any time; results are recorded in the audit trail.",
            "XML content was parsed with DOCTYPE declarations, external entities and external DTD access disabled. Records were extracted from the platform's supported XML schema (SMS/messages, calls, contacts, locations, photos, videos, browser history, email and chat).",
            "Each extracted record keeps a reference to its source file and its location within that file, together with a SHA-256 hash of its extracted fields. Identical records were skipped per device.",
            "Timestamps were normalized to UTC. The original timestamp text and any declared timezone were preserved. Timestamps without a timezone were interpreted as UTC and labelled ASSUMED_UTC; unparseable or missing timestamps were left undated and labelled.",
            "Relationships between identifiers were computed with deterministic, documented rules. Each relationship records the rule applied, whether it is stated directly by records or inferred, and the supporting record IDs.",
            "Findings were written by investigators and link the evidence records that support them. Findings generated with AI assistance are labelled as such together with their review status."
    );

    private final CaseAccessService caseAccessService;
    private final CaseRepository caseRepository;
    private final DeviceRepository deviceRepository;
    private final EvidenceRepository evidenceRepository;
    private final EvidenceSourceFileRepository fileRepository;
    private final EvidenceBookmarkRepository bookmarkRepository;
    private final ReportRepository reportRepository;
    private final AuditLogRepository auditLogRepository;
    private final WorkspaceService workspaceService;
    private final AuditService auditService;
    private final ReportPdfRenderer pdfRenderer;
    private final ObjectMapper snapshotMapper;

    public ReportService(CaseAccessService caseAccessService, CaseRepository caseRepository, DeviceRepository deviceRepository,
                         EvidenceRepository evidenceRepository, EvidenceSourceFileRepository fileRepository,
                         EvidenceBookmarkRepository bookmarkRepository, ReportRepository reportRepository,
                         AuditLogRepository auditLogRepository, WorkspaceService workspaceService, AuditService auditService,
                         ReportPdfRenderer pdfRenderer) {
        this.caseAccessService = caseAccessService;
        this.caseRepository = caseRepository;
        this.deviceRepository = deviceRepository;
        this.evidenceRepository = evidenceRepository;
        this.fileRepository = fileRepository;
        this.bookmarkRepository = bookmarkRepository;
        this.reportRepository = reportRepository;
        this.auditLogRepository = auditLogRepository;
        this.workspaceService = workspaceService;
        this.auditService = auditService;
        this.pdfRenderer = pdfRenderer;
        // Dedicated mapper: stable ISO-8601 timestamps so the stored snapshot and its hash are deterministic.
        this.snapshotMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    @Transactional
    public ReportSummary generate(Long caseId, ReportRequest request, Authentication authentication) {
        InvestigationCase investigationCase = caseAccessService.requireEditable(caseId, authentication);
        CaseAccessService.Actor actor = caseAccessService.actor(authentication);
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        String reportNumber = "RPT-" + investigationCase.getCaseNumber() + "-" + NUMBER_TIME.format(now);
        String title = request == null || request.title() == null || request.title().isBlank()
                ? investigationCase.getTitle() + " - Investigation Report" : request.title().trim();
        boolean fullTimeline = request != null && Boolean.TRUE.equals(request.includeFullTimeline());
        boolean includeAudit = request == null || request.includeAuditTrail() == null || request.includeAuditTrail();

        InvestigationReportDto snapshot = buildSnapshot(investigationCase, reportNumber, title, now, actor, fullTimeline, includeAudit);
        String json;
        try {
            json = snapshotMapper.writeValueAsString(snapshot);
        } catch (Exception ex) {
            throw new IllegalStateException("Report snapshot could not be serialized", ex);
        }
        String sha256 = sha256(json);

        long aiFindings = snapshot.findings().stream().filter(FindingSection::aiGenerated).count();
        long aiReviewed = snapshot.findings().stream().filter(f -> f.aiGenerated() && "REVIEWED".equals(f.reviewStatus())).count();

        Report report = new Report();
        report.setInvestigationCase(investigationCase);
        report.setReportNumber(reportNumber);
        report.setReportTitle(title.length() > 255 ? title.substring(0, 255) : title);
        report.setReportType("CASE_INVESTIGATION");
        report.setVersion("1");
        report.setStatus("GENERATED");
        report.setGeneratedAt(now);
        report.setGeneratedBy(authentication.getName());
        report.setPreparedBy(actor.fullName());
        report.setReviewedBy("Pending review");
        report.setApprovedBy("Not approved");
        report.setNotesHtml("");
        report.setAiSummary("AI-assisted findings included: " + aiFindings + " (" + aiReviewed + " reviewed)");
        report.setTimelineSummary(snapshot.evidenceOverview().totalRecords() + " evidence record(s); "
                + snapshot.keyEvents().size() + " event(s) in report");
        report.setExtractedKeywords("");
        report.setNamedEntities("");
        report.setSuspiciousFindings("");
        report.setRiskLevel("NOT_ASSESSED");
        report.setSnapshotJson(json);
        report.setContentSha256(sha256);
        report = reportRepository.save(report);

        auditService.record(new AuditService.Entry(null, authentication.getName(), "REPORT_GENERATED",
                "Generated report " + reportNumber + " (snapshot SHA-256 " + sha256 + ")", true, caseId, "REPORT",
                String.valueOf(report.getId()), null));
        return toSummary(report);
    }

    InvestigationReportDto buildSnapshot(InvestigationCase c, String reportNumber, String title, Instant now,
                                         CaseAccessService.Actor actor, boolean fullTimeline, boolean includeAudit) {
        Long caseId = c.getId();
        List<Evidence> evidence = evidenceRepository.findAllForCase(caseId).stream().sorted(Comparator.comparing(Evidence::getId)).toList();
        Map<Long, Evidence> byId = evidence.stream().collect(Collectors.toMap(Evidence::getId, e -> e));

        CaseSection caseSection = new CaseSection(c.getId(), c.getCaseNumber(), c.getTitle(), c.getDescription(), c.getStatus().name(),
                c.getPriority().name(), c.getCategory(), c.getCaseType(), c.getLocation(), c.getLeadInvestigator(), c.getCreatedAt(), c.getUpdatedAt());

        Map<Long, Long> perDevice = evidence.stream().collect(Collectors.groupingBy(e -> e.getDevice().getId(), Collectors.counting()));
        List<DeviceSection> devices = deviceRepository.findByInvestigationCaseId(caseId).stream()
                .sorted(Comparator.comparing(Device::getId))
                .map(d -> new DeviceSection(d.getId(), d.getDeviceName(), d.getManufacturer(), d.getModel(), d.getImei(), d.getSerialNumber(),
                        d.getOperatingSystem(), d.getOsVersion(), d.getAcquisitionMethod() == null ? null : d.getAcquisitionMethod().name(),
                        d.getAcquisitionStatus() == null ? null : d.getAcquisitionStatus().name(), d.getSeizedDate(), d.getReceivedDate(),
                        perDevice.getOrDefault(d.getId(), 0L)))
                .toList();

        List<EvidenceSourceFile> files = fileRepository.findByInvestigationCaseIdOrderByImportedAtDesc(caseId).stream()
                .sorted(Comparator.comparing(EvidenceSourceFile::getId)).toList();
        List<SourceFileSection> sources = files.stream().map(f -> new SourceFileSection(f.getId(), f.getOriginalFileName(),
                f.getSourceFormat().name(), f.getSizeBytes(), f.getSha256(), f.getImportedBy(), f.getImportedAt(),
                f.getProcessingStatus().name(), f.getIntegrityStatus().name(), f.getLastVerifiedAt(), f.getLastVerifiedBy(),
                f.getRecordsExtracted(), f.getDuplicatesSkipped(), f.getTimestampIssues(),
                f.getDevice() == null ? null : f.getDevice().getDeviceName())).toList();

        Map<String, Long> byType = new TreeMap<>();
        Map<String, Long> byStatus = new TreeMap<>();
        for (Evidence e : evidence) {
            byType.merge(EvidenceCategories.normalizeType(e.getArtifactType()), 1L, Long::sum);
            byStatus.merge(statusOf(e), 1L, Long::sum);
        }
        Instant first = evidence.stream().map(Evidence::getEventTime).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
        Instant last = evidence.stream().map(Evidence::getEventTime).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
        long undated = evidence.stream().filter(e -> e.getEventTime() == null).count();
        long withoutSource = evidence.stream().filter(e -> e.getSourceFile() == null).count();
        EvidenceOverview overview = new EvidenceOverview(evidence.size(), byType, byStatus, first, last, undated, withoutSource);

        List<FindingResponse> narrative = WorkspaceService.narrative(workspaceService.findingsFor(caseId));
        List<FindingSection> findings = narrative.stream().map(f -> new FindingSection(f.id(), f.title(), f.description(),
                f.basis().name(), f.significance().name(), f.reviewStatus().name(), f.aiGenerated(), f.aiEngine(), f.createdBy(),
                f.reviewedBy(), f.reviewedAt(), f.reviewComment(), f.earliestEventTime(),
                f.evidence().stream().map(ReportService::ref).toList())).toList();

        // Key events: every record cited by a reported finding or bookmarked; or the full dated timeline on request.
        Map<Long, List<TimelineEventDto.Flag>> flags = TimelineService.computeFlags(evidence, now);
        Set<Long> eventIds = new TreeSet<>();
        if (fullTimeline) {
            evidence.forEach(e -> eventIds.add(e.getId()));
        } else {
            findings.forEach(f -> f.evidence().forEach(ref -> eventIds.add(ref.evidenceId())));
            bookmarkRepository.findByCaseIdOrderByCreatedAtDesc(caseId).forEach(b -> eventIds.add(b.getEvidence().getId()));
        }
        List<EventEntry> events = eventIds.stream().map(byId::get).filter(Objects::nonNull)
                .sorted(Comparator.comparing((Evidence e) -> e.getEventTime() == null ? Instant.MAX : e.getEventTime()).thenComparing(Evidence::getId))
                .limit(MAX_EVENTS)
                .map(e -> new EventEntry(e.getEventTime(), statusOf(e),
                        flags.getOrDefault(e.getId(), List.of()).stream().map(TimelineEventDto.Flag::code).collect(Collectors.joining(", ")),
                        ref(EvidenceMapper.toResponse(e))))
                .toList();

        List<AuditEntry> auditTrail = List.of();
        if (includeAudit) {
            List<AuditLog> logs = new ArrayList<>(auditLogRepository.findTop200ByCaseIdOrderByIdDesc(caseId));
            Collections.reverse(logs);
            auditTrail = logs.stream().map(l -> new AuditEntry(l.getId(), l.getTimestamp(), l.getUsername(), l.getAction(),
                    l.getDescription(), l.isSuccess())).toList();
        }
        AuditService.ChainVerification chain = auditService.verifyChain();
        AuditChainStatus chainStatus = new AuditChainStatus(chain.status(), chain.recordsChecked(), chain.legacyUnchainedRecords(),
                chain.firstBrokenRecordId(), chain.verifiedAt().truncatedTo(ChronoUnit.SECONDS));

        List<String> limitations = limitations(evidence, files, findings, byStatus, withoutSource, events.size() == MAX_EVENTS, chain);

        return new InvestigationReportDto(SCHEMA_VERSION, reportNumber, title, now, actor.username(), actor.fullName(), caseSection,
                devices, sources, overview, findings, events, fullTimeline, METHODOLOGY, limitations, auditTrail, chainStatus,
                "This report presents records extracted by the platform and findings recorded by investigators. Correlations and "
                        + "AI-assisted content are interpretations that require verification against the cited source records. "
                        + "Hash verification shows that stored files are unchanged since import within this platform; it does not by "
                        + "itself establish legal admissibility.");
    }

    private static List<String> limitations(List<Evidence> evidence, List<EvidenceSourceFile> files, List<FindingSection> findings,
                                            Map<String, Long> byStatus, long withoutSource, boolean eventsTruncated,
                                            AuditService.ChainVerification chain) {
        List<String> items = new ArrayList<>();
        items.add("Only the platform's documented XML schema is decoded. Proprietary vendor report structures inside archives are not interpreted natively.");
        long assumed = byStatus.getOrDefault("ASSUMED_UTC", 0L) + byStatus.getOrDefault("DATE_ONLY", 0L);
        long undated = byStatus.getOrDefault("MISSING", 0L) + byStatus.getOrDefault("UNPARSEABLE", 0L);
        long legacy = byStatus.getOrDefault("LEGACY_IMPORT", 0L);
        if (assumed > 0) items.add(assumed + " record(s) have timestamps without a timezone or time of day; their UTC placement is assumed.");
        if (undated > 0) items.add(undated + " record(s) have missing or unparseable timestamps and cannot be placed chronologically.");
        if (legacy > 0) items.add(legacy + " record(s) were imported before original timestamps were preserved; their timestamp derivation is unknown.");
        if (withoutSource > 0) items.add(withoutSource + " record(s) are not linked to a source file (legacy or demo data) and have no integrity hash.");
        long unverified = files.stream().filter(f -> f.getIntegrityStatus() == EvidenceSourceFile.IntegrityStatus.NOT_VERIFIED).count();
        long problems = files.stream().filter(f -> f.getIntegrityStatus() == EvidenceSourceFile.IntegrityStatus.MISMATCH
                || f.getIntegrityStatus() == EvidenceSourceFile.IntegrityStatus.MISSING).count();
        long failed = files.stream().filter(f -> f.getProcessingStatus() == EvidenceSourceFile.ProcessingStatus.FAILED).count();
        if (unverified > 0) items.add(unverified + " evidence file(s) have not been re-verified since import.");
        if (problems > 0) items.add(problems + " evidence file(s) FAILED integrity verification or are missing. Records from these files must not be relied on until resolved.");
        if (failed > 0) items.add(failed + " evidence file(s) failed processing; their records are not included.");
        long pending = findings.stream().filter(f -> !"REVIEWED".equals(f.reviewStatus())).count();
        long aiPending = findings.stream().filter(f -> f.aiGenerated() && !"REVIEWED".equals(f.reviewStatus())).count();
        if (pending > 0) items.add(pending + " finding(s) in this report have not been reviewed.");
        if (aiPending > 0) items.add(aiPending + " AI-assisted finding(s) have not been reviewed by a second person and are shown for transparency only.");
        if (findings.isEmpty()) items.add("No findings were recorded for this case at the time of generation.");
        if (eventsTruncated) items.add("The event list was limited to " + MAX_EVENTS + " entries.");
        if (!"INTACT".equals(chain.status())) items.add("The audit-log hash chain did not verify (first break at record " + chain.firstBrokenRecordId() + ").");
        if (chain.legacyUnchainedRecords() > 0) items.add(chain.legacyUnchainedRecords() + " older audit record(s) predate the hash chain and cannot be verified.");
        if (evidence.isEmpty()) items.add("The case had no evidence records at the time of generation.");
        return items;
    }

    @Transactional(readOnly = true)
    public List<ReportSummary> list(Long caseId, Authentication authentication) {
        if (caseId != null) {
            caseAccessService.requireViewable(caseId, authentication);
            return reportRepository.findByInvestigationCaseId(caseId).stream()
                    .sorted(Comparator.comparing((Report r) -> r.getGeneratedAt() == null ? Instant.EPOCH : r.getGeneratedAt()).reversed())
                    .map(this::toSummary).toList();
        }
        Optional<Set<Long>> accessible = caseAccessService.accessibleCaseIds(authentication);
        return reportRepository.findTop200ByOrderByGeneratedAtDesc().stream()
                .filter(r -> r.getInvestigationCase() != null)
                .filter(r -> accessible.isEmpty() || accessible.get().contains(r.getInvestigationCase().getId()))
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReportDetail detail(Long reportId, Authentication authentication) {
        Report report = requireViewable(reportId, authentication);
        if (report.getSnapshotJson() == null) {
            return new ReportDetail(toSummary(report), null, null, new LegacyContent(report.getAiSummary(), report.getTimelineSummary(),
                    report.getExtractedKeywords(), report.getNamedEntities(), report.getSuspiciousFindings(), report.getRiskLevel(),
                    "Legacy report created before data snapshots existed. Its content cannot be reproduced or verified; generate a new report."));
        }
        try {
            JsonNode snapshot = snapshotMapper.readTree(report.getSnapshotJson());
            return new ReportDetail(toSummary(report), snapshot, sha256(report.getSnapshotJson()).equals(report.getContentSha256()), null);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Stored report snapshot could not be read");
        }
    }

    @Transactional(readOnly = true)
    public byte[] pdf(Long reportId, Authentication authentication) {
        Report report = requireViewable(reportId, authentication);
        if (report.getSnapshotJson() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This legacy report has no data snapshot and cannot be exported. Generate a new report.");
        }
        boolean verified = sha256(report.getSnapshotJson()).equals(report.getContentSha256());
        InvestigationReportDto snapshot;
        try {
            snapshot = snapshotMapper.readValue(report.getSnapshotJson(), InvestigationReportDto.class);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Stored report snapshot could not be read");
        }
        byte[] pdf = pdfRenderer.render(snapshot, report.getContentSha256(), verified, report.getStatus(), report.getReviewedBy(), report.getReviewedAt());
        auditService.record(new AuditService.Entry(null, authentication.getName(), "REPORT_EXPORTED",
                "Exported report " + report.getReportNumber() + " as PDF (snapshot hash " + (verified ? "verified" : "MISMATCH") + ")",
                true, report.getInvestigationCase().getId(), "REPORT", String.valueOf(reportId), null));
        return pdf;
    }

    @Transactional
    public ReportSummary review(Long reportId, ReviewRequest request, Authentication authentication) {
        Report report = requireViewable(reportId, authentication);
        CaseAccessService.Actor actor = caseAccessService.actor(authentication);
        if (!actor.admin() && !actor.reviewer()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only reviewers or administrators can review reports");
        }
        if (report.getSnapshotJson() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Legacy reports without a snapshot cannot be reviewed");
        }
        if (authentication.getName().equalsIgnoreCase(report.getGeneratedBy())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A report cannot be reviewed by the person who generated it");
        }
        if ("REVIEWED".equals(report.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Report is already reviewed");
        }
        report.setStatus("REVIEWED");
        report.setReviewedBy(actor.fullName());
        report.setReviewedAt(Instant.now());
        String comment = request == null || request.comment() == null || request.comment().isBlank() ? "" : " Comment: " + request.comment().trim();
        auditService.record(new AuditService.Entry(null, authentication.getName(), "REPORT_REVIEWED",
                "Reviewed report " + report.getReportNumber() + "." + comment, true, report.getInvestigationCase().getId(), "REPORT",
                String.valueOf(reportId), null));
        return toSummary(report);
    }

    @Transactional
    public void delete(Long reportId, Authentication authentication) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found"));
        if (report.getInvestigationCase() == null) {
            if (!caseAccessService.actor(authentication).admin()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient permission");
            }
        } else {
            caseAccessService.assertCanEdit(report.getInvestigationCase(), authentication);
        }
        if ("REVIEWED".equals(report.getStatus()) && !caseAccessService.actor(authentication).admin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reviewed reports can only be deleted by an administrator");
        }
        Long caseId = report.getInvestigationCase() == null ? null : report.getInvestigationCase().getId();
        reportRepository.delete(report);
        auditService.record(new AuditService.Entry(null, authentication.getName(), "REPORT_DELETED",
                "Deleted report " + report.getReportNumber(), true, caseId, "REPORT", String.valueOf(reportId), null));
    }

    private Report requireViewable(Long reportId, Authentication authentication) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found"));
        if (report.getInvestigationCase() == null) {
            if (!caseAccessService.actor(authentication).admin()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient permission");
            }
        } else {
            caseAccessService.assertCanView(report.getInvestigationCase(), authentication);
        }
        return report;
    }

    private ReportSummary toSummary(Report report) {
        int findings = 0;
        int ai = 0;
        int aiUnreviewed = 0;
        if (report.getSnapshotJson() != null) {
            try {
                for (JsonNode finding : snapshotMapper.readTree(report.getSnapshotJson()).path("findings")) {
                    findings++;
                    if (finding.path("aiGenerated").asBoolean(false)) {
                        ai++;
                        if (!"REVIEWED".equals(finding.path("reviewStatus").asText())) {
                            aiUnreviewed++;
                        }
                    }
                }
            } catch (Exception ignored) {
                // summary counts are informational
            }
        }
        InvestigationCase c = report.getInvestigationCase();
        return new ReportSummary(report.getId(), report.getReportNumber(), report.getReportTitle(), report.getReportType(),
                c == null ? null : c.getId(), c == null ? null : c.getCaseNumber(), report.getStatus(), report.getGeneratedAt(),
                report.getGeneratedBy(), report.getPreparedBy(), report.getReviewedAt() == null ? null : report.getReviewedBy(),
                report.getReviewedAt(), report.getContentSha256(), report.getSnapshotJson() != null, findings, ai, aiUnreviewed);
    }

    private static EvidenceRef ref(EvidenceResponse e) {
        String body = e.content() != null ? e.content() : e.title();
        if (body != null && body.length() > 300) {
            body = body.substring(0, 297) + "...";
        }
        List<String> parts = new ArrayList<>();
        if (e.source() != null) parts.add("from " + e.source());
        if (e.target() != null) parts.add("to " + e.target());
        if (e.phoneNumber() != null && !Objects.equals(e.phoneNumber(), e.source()) && !Objects.equals(e.phoneNumber(), e.target())) parts.add("number " + e.phoneNumber());
        if (e.contactName() != null) parts.add("contact " + e.contactName());
        if (e.location() != null) parts.add("location " + e.location());
        else if (e.latitude() != null && e.longitude() != null) parts.add("coordinates " + e.latitude() + ", " + e.longitude());
        return new EvidenceRef(e.id(), e.artifactType(), e.eventTime(), e.originalTimestamp(), e.originalTimezone(),
                e.timestampStatus() == null ? null : e.timestampStatus().name(), body, String.join("; ", parts), e.deviceName(),
                e.sourceFileId(), e.sourceRecordRef());
    }

    private static String statusOf(Evidence e) {
        if (e.getTimestampStatus() != null) return e.getTimestampStatus().name();
        return e.getEventTime() == null ? TimestampStatus.MISSING.name() : TimestampStatus.LEGACY_IMPORT.name();
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
