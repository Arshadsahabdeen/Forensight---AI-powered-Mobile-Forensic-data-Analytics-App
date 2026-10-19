package com.forensics.platform.dto;

import com.forensics.platform.dto.IngestionDtos.ProcessingJobResponse;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Dashboard figures computed directly from stored records. All counts are limited to the cases the
 * caller may view ({@code scope} = ALL_CASES or ASSIGNED_CASES).
 */
public class DashboardDtos {
    public record ChartMetric(String label, long value) {}

    public record Activity(Long id, String actor, String action, String description, Instant timestamp, Long caseId, boolean success) {}

    public record RecentCase(Long id, String caseNumber, String title, String status, String priority, String leadInvestigator, Instant updatedAt) {}

    public record CaseStats(long total, long active, long openHighPriority, List<ChartMetric> byStatus, List<ChartMetric> byPriority) {}

    public record EvidenceStats(long devices, long records, long files, Map<String, Long> filesByIntegrity, List<ChartMetric> recordsByType) {}

    public record ProcessingStats(long queued, long running, long failed, List<ProcessingJobResponse> recentJobs) {}

    public record ReviewStats(long findingsNeedingReview, long reportsTotal, long reportsAwaitingReview) {}

    public record DashboardResponse(
            String scope,
            Instant generatedAt,
            CaseStats cases,
            EvidenceStats evidence,
            ProcessingStats processing,
            ReviewStats review,
            List<RecentCase> recentCases,
            List<Activity> recentActivities
    ) {}
}
