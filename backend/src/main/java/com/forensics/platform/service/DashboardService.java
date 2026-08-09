package com.forensics.platform.service;

import com.forensics.platform.dto.DashboardDtos.Activity;
import com.forensics.platform.dto.DashboardDtos.ChartMetric;
import com.forensics.platform.dto.DashboardDtos.DashboardResponse;
import com.forensics.platform.dto.DashboardDtos.SummaryCard;
import com.forensics.platform.entity.CasePriority;
import com.forensics.platform.entity.CaseStatus;
import com.forensics.platform.entity.RoleName;
import com.forensics.platform.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DashboardService {
    private final CaseRepository caseRepository;
    private final DeviceRepository deviceRepository;
    private final EvidenceRepository evidenceRepository;
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;

    public DashboardService(CaseRepository caseRepository, DeviceRepository deviceRepository, EvidenceRepository evidenceRepository, ReportRepository reportRepository, UserRepository userRepository, AuditLogRepository auditLogRepository) {
        this.caseRepository = caseRepository;
        this.deviceRepository = deviceRepository;
        this.evidenceRepository = evidenceRepository;
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard() {
        Instant now = Instant.now();
        Instant startOfToday = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant startOfCurrent30 = LocalDate.now(ZoneOffset.UTC).minusDays(29).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant startOfPrevious30 = LocalDate.now(ZoneOffset.UTC).minusDays(59).atStartOfDay().toInstant(ZoneOffset.UTC);

        long totalCases = caseRepository.count();
        long activeCases = caseRepository.countByStatus(CaseStatus.ACTIVE);
        long closedCases = caseRepository.countByStatus(CaseStatus.CLOSED);
        long highPriorityCases = caseRepository.countByPriority(CasePriority.HIGH) + caseRepository.countByPriority(CasePriority.CRITICAL);
        long totalDevices = deviceRepository.count();
        long totalEvidence = evidenceRepository.count();
        long investigators = userRepository.countByRoles_Name(RoleName.INVESTIGATOR);
        long reportsGenerated = reportRepository.count();
        long todaysActivities = auditLogRepository.countByTimestampBetween(startOfToday, now);
        long aiProcessedEvidence = totalEvidence == 0 ? 0 : evidenceRepository.countByHashSha256IsNotNullAndHashSha256Not("");
        String aiProcessingStatus = totalEvidence == 0 ? "0%" : Math.min(100, (int) Math.round((aiProcessedEvidence * 100.0) / totalEvidence)) + "%";

        long currentPeriodCases = caseRepository.countByCreatedAtBetween(startOfCurrent30, now);
        long previousPeriodCases = caseRepository.countByCreatedAtBetween(startOfPrevious30, startOfCurrent30);
        long currentPeriodEvidence = evidenceRepository.countByCreatedAtBetween(startOfCurrent30, now);
        long previousPeriodEvidence = evidenceRepository.countByCreatedAtBetween(startOfPrevious30, startOfCurrent30);
        long currentPeriodActivities = auditLogRepository.countByTimestampBetween(startOfCurrent30, now);
        long previousPeriodActivities = auditLogRepository.countByTimestampBetween(startOfPrevious30, startOfCurrent30);

        var summaryCards = List.of(
                new SummaryCard("totalCases", "Total Cases", totalCases, percentageChange(currentPeriodCases, previousPeriodCases), trend(currentPeriodCases, previousPeriodCases), "cyan", "ClipboardList"),
                new SummaryCard("activeCases", "Active Cases", activeCases, percentageChange(activeCases, Math.max(0, totalCases - activeCases)), trend(activeCases, Math.max(0, totalCases - activeCases)), "blue", "ShieldCheck"),
                new SummaryCard("closedCases", "Closed Cases", closedCases, percentageChange(closedCases, Math.max(0, totalCases - closedCases)), trend(closedCases, Math.max(0, totalCases - closedCases)), "green", "CheckCircle2"),
                new SummaryCard("highPriorityCases", "High Priority Cases", highPriorityCases, percentageChange(highPriorityCases, Math.max(0, totalCases - highPriorityCases)), trend(highPriorityCases, Math.max(0, totalCases - highPriorityCases)), "red", "AlertTriangle"),
                new SummaryCard("totalDevices", "Total Devices", totalDevices, percentageChange(totalDevices, Math.max(0, totalDevices - currentPeriodCases)), trend(totalDevices, Math.max(0, totalDevices - currentPeriodCases)), "slate", "HardDrive"),
                new SummaryCard("totalEvidence", "Total Evidence", totalEvidence, percentageChange(currentPeriodEvidence, previousPeriodEvidence), trend(currentPeriodEvidence, previousPeriodEvidence), "amber", "Database"),
                new SummaryCard("totalInvestigators", "Total Investigators", investigators, percentageChange(investigators, Math.max(0, investigators - 1)), trend(investigators, Math.max(0, investigators - 1)), "violet", "Users"),
                new SummaryCard("reportsGenerated", "Reports Generated", reportsGenerated, percentageChange(reportsGenerated, Math.max(0, reportsGenerated - currentPeriodCases)), trend(reportsGenerated, Math.max(0, reportsGenerated - currentPeriodCases)), "teal", "FileText"),
                new SummaryCard("todaysActivities", "Today's Activities", todaysActivities, percentageChange(currentPeriodActivities, previousPeriodActivities), trend(currentPeriodActivities, previousPeriodActivities), "orange", "Activity"),
                new SummaryCard("aiProcessingStatus", "AI Processing Status", Math.round(totalEvidence == 0 ? 0 : (aiProcessedEvidence * 100.0f) / totalEvidence), percentageChange(aiProcessedEvidence, Math.max(0, totalEvidence - aiProcessedEvidence)), trend(aiProcessedEvidence, Math.max(0, totalEvidence - aiProcessedEvidence)), "emerald", "Bot")
        );

        var casesByStatus = List.of(
                new ChartMetric("OPEN", caseRepository.countByStatus(CaseStatus.OPEN)),
                new ChartMetric("ACTIVE", caseRepository.countByStatus(CaseStatus.ACTIVE)),
                new ChartMetric("ON_HOLD", caseRepository.countByStatus(CaseStatus.ON_HOLD)),
                new ChartMetric("CLOSED", caseRepository.countByStatus(CaseStatus.CLOSED)),
                new ChartMetric("ARCHIVED", caseRepository.countByStatus(CaseStatus.ARCHIVED))
        );

        var casesByPriority = List.of(
                new ChartMetric("LOW", caseRepository.countByPriority(CasePriority.LOW)),
                new ChartMetric("MEDIUM", caseRepository.countByPriority(CasePriority.MEDIUM)),
                new ChartMetric("HIGH", caseRepository.countByPriority(CasePriority.HIGH)),
                new ChartMetric("CRITICAL", caseRepository.countByPriority(CasePriority.CRITICAL))
        );

        var evidenceByType = evidenceRepository.countEvidenceByArtifactType().stream()
            .map(row -> new ChartMetric(normalizeLabel(asLabel(row[0])), asLong(row[1])))
            .toList();

        var monthlyInvestigationActivity = monthlyActivity(now);
        var recentEvidenceUploadTrend = recentEvidenceTrend(now);
        var investigatorWorkload = caseRepository.countCasesByLeadInvestigator().stream()
            .map(row -> new ChartMetric(normalizeLabel(asLabel(row[0])), asLong(row[1])))
            .toList();

        var recentActivities = auditLogRepository.findTop100ByOrderByTimestampDesc().stream()
                .map(item -> new Activity(item.getId(), item.getUsername(), item.getAction(), item.getDescription(), item.getTimestamp()))
                .toList();

        return new DashboardResponse(
                summaryCards,
                casesByStatus,
                casesByPriority,
                evidenceByType,
                monthlyInvestigationActivity,
                recentEvidenceUploadTrend,
                investigatorWorkload,
                recentActivities,
                aiProcessingStatus
        );
    }

    private List<ChartMetric> monthlyActivity(Instant now) {
        Map<String, Long> monthly = new LinkedHashMap<>();
        LocalDate cursor = LocalDate.ofInstant(now, ZoneOffset.UTC).withDayOfMonth(1).minusMonths(5);
        for (int i = 0; i < 6; i++) {
            String label = cursor.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
            monthly.put(label, 0L);
            cursor = cursor.plusMonths(1);
        }

        Instant rangeStart = LocalDate.ofInstant(now, ZoneOffset.UTC).withDayOfMonth(1).minusMonths(5).atStartOfDay().toInstant(ZoneOffset.UTC);
        auditLogRepository.countAuditLogByMonthSince(rangeStart).forEach(row -> monthly.put(normalizeLabel(asLabel(row[0])), asLong(row[1])));

        return monthly.entrySet().stream().map(item -> new ChartMetric(item.getKey(), item.getValue())).toList();
    }

    private List<ChartMetric> recentEvidenceTrend(Instant now) {
        Map<String, Long> daily = new LinkedHashMap<>();
        LocalDate start = LocalDate.ofInstant(now, ZoneOffset.UTC).minusDays(6);
        for (int i = 0; i < 7; i++) {
            LocalDate date = start.plusDays(i);
            daily.put(date.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + date.getDayOfMonth(), 0L);
        }

        Instant rangeStart = start.atStartOfDay().toInstant(ZoneOffset.UTC);
        evidenceRepository.countEvidenceByDaySince(rangeStart).forEach(row -> daily.put(normalizeLabel(asLabel(row[0])), asLong(row[1])));

        return daily.entrySet().stream().map(item -> new ChartMetric(item.getKey(), item.getValue())).toList();
    }

    private String normalizeLabel(String value) {
        return value == null || value.isBlank() ? "Unknown" : value;
    }

    private String asLabel(Object value) {
        return value == null ? null : value.toString();
    }

    private long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }

    private double percentageChange(long current, long previous) {
        if (previous == 0) {
            return current == 0 ? 0.0 : 100.0;
        }
        return Math.round(((current - previous) * 10000.0 / previous)) / 100.0;
    }

    private String trend(long current, long previous) {
        if (current == previous) {
            return "flat";
        }
        return current > previous ? "up" : "down";
    }
}
