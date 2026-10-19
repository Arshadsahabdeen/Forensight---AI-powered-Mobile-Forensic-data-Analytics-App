package com.forensics.platform.service;

import com.forensics.platform.dto.DashboardDtos.*;
import com.forensics.platform.entity.*;
import com.forensics.platform.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class DashboardService {
    private final CaseRepository caseRepository;
    private final DeviceRepository deviceRepository;
    private final EvidenceRepository evidenceRepository;
    private final EvidenceSourceFileRepository fileRepository;
    private final ReportRepository reportRepository;
    private final AuditLogRepository auditLogRepository;
    private final InvestigationFindingRepository findingRepository;
    private final ProcessingJobService jobService;
    private final CaseAccessService caseAccessService;

    public DashboardService(CaseRepository caseRepository, DeviceRepository deviceRepository, EvidenceRepository evidenceRepository,
                            EvidenceSourceFileRepository fileRepository, ReportRepository reportRepository,
                            AuditLogRepository auditLogRepository, InvestigationFindingRepository findingRepository,
                            ProcessingJobService jobService, CaseAccessService caseAccessService) {
        this.caseRepository = caseRepository;
        this.deviceRepository = deviceRepository;
        this.evidenceRepository = evidenceRepository;
        this.fileRepository = fileRepository;
        this.reportRepository = reportRepository;
        this.auditLogRepository = auditLogRepository;
        this.findingRepository = findingRepository;
        this.jobService = jobService;
        this.caseAccessService = caseAccessService;
    }

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(Authentication authentication) {
        Optional<Set<Long>> scope = caseAccessService.accessibleCaseIds(authentication);
        boolean all = scope.isEmpty();
        Set<Long> caseIds = scope.orElse(Set.of());
        boolean none = !all && caseIds.isEmpty();

        Specification<InvestigationCase> caseScope = (root, query, cb) -> all ? cb.conjunction()
                : none ? cb.disjunction() : root.get("id").in(caseIds);

        List<ChartMetric> byStatus = new ArrayList<>();
        for (CaseStatus status : CaseStatus.values()) {
            byStatus.add(new ChartMetric(status.name(), caseRepository.count(caseScope.and((root, query, cb) -> cb.equal(root.get("status"), status)))));
        }
        List<ChartMetric> byPriority = new ArrayList<>();
        for (CasePriority priority : CasePriority.values()) {
            byPriority.add(new ChartMetric(priority.name(), caseRepository.count(caseScope.and((root, query, cb) -> cb.equal(root.get("priority"), priority)))));
        }
        long total = caseRepository.count(caseScope);
        long active = byStatus.stream().filter(m -> m.label().equals("ACTIVE") || m.label().equals("OPEN")).mapToLong(ChartMetric::value).sum();
        long openHigh = caseRepository.count(caseScope
                .and((root, query, cb) -> root.get("status").in(CaseStatus.OPEN, CaseStatus.ACTIVE, CaseStatus.ON_HOLD))
                .and((root, query, cb) -> root.get("priority").in(CasePriority.HIGH, CasePriority.CRITICAL)));
        CaseStats cases = new CaseStats(total, active, openHigh, byStatus, byPriority);

        long devices = all ? deviceRepository.count() : none ? 0 : deviceRepository.count((root, query, cb) -> root.get("investigationCase").get("id").in(caseIds));
        long records = all ? evidenceRepository.count() : none ? 0 : evidenceRepository.countForCases(caseIds);
        Map<String, Long> integrity = new LinkedHashMap<>();
        for (EvidenceSourceFile.IntegrityStatus status : EvidenceSourceFile.IntegrityStatus.values()) {
            integrity.put(status.name(), 0L);
        }
        List<Object[]> integrityRows = all ? fileRepository.countByIntegrityStatus() : none ? List.of() : fileRepository.countByIntegrityStatusForCases(caseIds);
        long files = 0;
        for (Object[] row : integrityRows) {
            long count = ((Number) row[1]).longValue();
            integrity.put(String.valueOf(row[0]), count);
            files += count;
        }
        List<ChartMetric> byType = new ArrayList<>();
        if (all) {
            for (Object[] row : evidenceRepository.countByArtifactType()) {
                byType.add(new ChartMetric(row[0] == null ? "UNKNOWN" : row[0].toString(), ((Number) row[1]).longValue()));
            }
        } else if (!none) {
            Map<String, Long> merged = new TreeMap<>();
            for (Long caseId : caseIds) {
                for (Object[] row : evidenceRepository.countByArtifactTypeForCase(caseId)) {
                    merged.merge(row[0] == null ? "UNKNOWN" : row[0].toString(), ((Number) row[1]).longValue(), Long::sum);
                }
            }
            merged.forEach((label, value) -> byType.add(new ChartMetric(label, value)));
            byType.sort(Comparator.comparingLong(ChartMetric::value).reversed());
        }
        EvidenceStats evidence = new EvidenceStats(devices, records, files, integrity, byType.stream().limit(10).toList());

        var jobs = jobService.list(null, null, 0, 8, authentication);
        ProcessingStats processing = new ProcessingStats(jobs.queued(), jobs.running(), jobs.failed(), jobs.items());

        long findingsNeedingReview = all ? findingRepository.countByReviewStatus(InvestigationFinding.ReviewStatus.NEEDS_REVIEW)
                : none ? 0 : findingRepository.countByReviewStatusAndCaseIdIn(InvestigationFinding.ReviewStatus.NEEDS_REVIEW, caseIds);
        List<Report> reports = reportRepository.findTop200ByOrderByGeneratedAtDesc().stream()
                .filter(r -> r.getInvestigationCase() != null && (all || caseIds.contains(r.getInvestigationCase().getId())))
                .toList();
        long reportsAwaiting = reports.stream().filter(r -> r.getSnapshotJson() != null && !"REVIEWED".equals(r.getStatus())).count();
        ReviewStats review = new ReviewStats(findingsNeedingReview, reports.size(), reportsAwaiting);

        List<RecentCase> recentCases = caseRepository.findAll(caseScope, PageRequest.of(0, 8, Sort.by(Sort.Direction.DESC, "updatedAt"))).stream()
                .map(c -> new RecentCase(c.getId(), c.getCaseNumber(), c.getTitle(), c.getStatus().name(), c.getPriority().name(),
                        c.getLeadInvestigator(), c.getUpdatedAt()))
                .toList();

        List<AuditLog> activity;
        if (all) {
            activity = auditLogRepository.findAll(PageRequest.of(0, 15, Sort.by(Sort.Direction.DESC, "id"))).getContent();
        } else if (none) {
            activity = List.of();
        } else {
            activity = auditLogRepository.findByCaseIdInOrderByIdDesc(caseIds, PageRequest.of(0, 15)).getContent();
        }
        List<Activity> recentActivities = activity.stream()
                .map(item -> new Activity(item.getId(), item.getUsername(), item.getAction(), item.getDescription(), item.getTimestamp(),
                        item.getCaseId(), item.isSuccess()))
                .toList();

        return new DashboardResponse(all ? "ALL_CASES" : "ASSIGNED_CASES", Instant.now(), cases, evidence, processing, review,
                recentCases, recentActivities);
    }
}
