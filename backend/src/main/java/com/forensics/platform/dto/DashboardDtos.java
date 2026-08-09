package com.forensics.platform.dto;

import java.time.Instant;
import java.util.List;

public class DashboardDtos {
    public record SummaryCard(String key, String label, long count, double percentageChange, String trend, String color, String icon) {}
    public record ChartMetric(String label, long value) {}
    public record Activity(Long id, String actor, String action, String description, Instant timestamp) {}
    public record DashboardResponse(
            List<SummaryCard> summaryCards,
            List<ChartMetric> casesByStatus,
            List<ChartMetric> casesByPriority,
            List<ChartMetric> evidenceByType,
            List<ChartMetric> monthlyInvestigationActivity,
            List<ChartMetric> recentEvidenceUploadTrend,
            List<ChartMetric> investigatorWorkload,
            List<Activity> recentActivities,
            String aiProcessingStatus
    ) {}
}
