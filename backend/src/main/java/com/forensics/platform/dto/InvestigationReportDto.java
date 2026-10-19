package com.forensics.platform.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Immutable data snapshot of a case report. It is serialized once at generation time, stored with its
 * SHA-256, and every export (view or PDF) is rendered from the stored snapshot.
 */
public record InvestigationReportDto(
        String schemaVersion,
        String reportNumber,
        String title,
        Instant generatedAt,
        String generatedBy,
        String preparedBy,
        CaseSection caseDetails,
        List<DeviceSection> devices,
        List<SourceFileSection> evidenceSources,
        EvidenceOverview evidenceOverview,
        List<FindingSection> findings,
        List<EventEntry> keyEvents,
        boolean fullTimelineIncluded,
        List<String> methodology,
        List<String> limitations,
        List<AuditEntry> auditTrail,
        AuditChainStatus auditChain,
        String disclaimer
) {

    public record CaseSection(Long id, String caseNumber, String title, String description, String status, String priority,
                              String category, String caseType, String location, String leadInvestigator,
                              Instant createdAt, Instant updatedAt) {}

    public record DeviceSection(Long id, String deviceName, String manufacturer, String model, String imei, String serialNumber,
                                String operatingSystem, String osVersion, String acquisitionMethod, String acquisitionStatus,
                                LocalDate seizedDate, LocalDate receivedDate, long evidenceCount) {}

    public record SourceFileSection(Long id, String originalFileName, String sourceFormat, long sizeBytes, String sha256,
                                    String importedBy, Instant importedAt, String processingStatus, String integrityStatus,
                                    Instant lastVerifiedAt, String lastVerifiedBy, int recordsExtracted, int duplicatesSkipped,
                                    int timestampIssues, String deviceName) {}

    public record EvidenceOverview(long totalRecords, Map<String, Long> recordsByType, Map<String, Long> recordsByTimestampStatus,
                                   Instant firstEventTime, Instant lastEventTime, long undatedRecords,
                                   long recordsWithoutSourceFile) {}

    public record EvidenceRef(Long evidenceId, String artifactType, Instant eventTime, String originalTimestamp,
                              String originalTimezone, String timestampStatus, String summary, String participants,
                              String deviceName, Long sourceFileId, String sourceRecordRef) {}

    public record FindingSection(Long id, String title, String description, String basis, String significance,
                                 String reviewStatus, boolean aiGenerated, String aiEngine, String createdBy,
                                 String reviewedBy, Instant reviewedAt, String reviewComment,
                                 Instant earliestEventTime, List<EvidenceRef> evidence) {}

    public record EventEntry(Instant eventTime, String timestampStatus, String flags, EvidenceRef evidence) {}

    public record AuditEntry(Long id, Instant timestamp, String actor, String action, String description, boolean success) {}

    public record AuditChainStatus(String status, long recordsChecked, long legacyUnchainedRecords, Long firstBrokenRecordId,
                                   Instant verifiedAt) {}
}
