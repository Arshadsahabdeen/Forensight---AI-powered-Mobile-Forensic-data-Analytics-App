package com.forensics.platform.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class ReportDtos {
    private ReportDtos() {
    }

    public record ReportRequest(@Size(max = 200) String title, Boolean includeFullTimeline, Boolean includeAuditTrail) {}

    public record ReviewRequest(@Size(max = 1000) String comment) {}

    public record ReportSummary(
            Long id,
            String reportNumber,
            String title,
            String reportType,
            Long caseId,
            String caseNumber,
            String status,
            Instant generatedAt,
            String generatedBy,
            String preparedBy,
            String reviewedBy,
            Instant reviewedAt,
            String contentSha256,
            boolean hasSnapshot,
            int findingsCount,
            int aiFindingsCount,
            int unreviewedAiFindings
    ) {}

    /**
     * {@code snapshot} is the stored JSON exactly as hashed; null for legacy reports created before snapshots existed.
     * {@code snapshotVerified} reports whether the stored JSON still matches {@code contentSha256}.
     */
    public record ReportDetail(ReportSummary summary, JsonNode snapshot, Boolean snapshotVerified, LegacyContent legacy) {}

    public record LegacyContent(String aiSummary, String timelineSummary, String extractedKeywords, String namedEntities,
                                String suspiciousFindings, String riskLevel, String notice) {}
}
