package com.forensics.platform.service;

import com.forensics.platform.dto.CaseDtos.*;
import com.forensics.platform.entity.CasePriority;
import com.forensics.platform.entity.CaseStatus;
import com.forensics.platform.entity.EvidenceSourceFile;
import com.forensics.platform.entity.InvestigationCase;
import com.forensics.platform.entity.InvestigationFinding;
import com.forensics.platform.mapper.CaseMapper;
import com.forensics.platform.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
public class CaseService {
    private final CaseRepository caseRepository;
    private final DeviceRepository deviceRepository;
    private final EvidenceRepository evidenceRepository;
    private final EvidenceSourceFileRepository fileRepository;
    private final ReportRepository reportRepository;
    private final AuditLogRepository auditLogRepository;
    private final InvestigationFindingRepository findingRepository;
    private final AuditService auditService;
    private final CaseAccessService caseAccessService;
    private final CaseMapper caseMapper;

    public CaseService(CaseRepository caseRepository, DeviceRepository deviceRepository, EvidenceRepository evidenceRepository,
                       EvidenceSourceFileRepository fileRepository, ReportRepository reportRepository, AuditLogRepository auditLogRepository,
                       InvestigationFindingRepository findingRepository, AuditService auditService, CaseAccessService caseAccessService,
                       CaseMapper caseMapper) {
        this.caseRepository = caseRepository;
        this.deviceRepository = deviceRepository;
        this.evidenceRepository = evidenceRepository;
        this.fileRepository = fileRepository;
        this.reportRepository = reportRepository;
        this.auditLogRepository = auditLogRepository;
        this.findingRepository = findingRepository;
        this.auditService = auditService;
        this.caseAccessService = caseAccessService;
        this.caseMapper = caseMapper;
    }

    @Transactional(readOnly = true)
    public CasePageResponse listCases(int page, int size, String sortBy, String sortDir, String search, String status, String priority, Authentication authentication) {
        var pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(size, 1), 100), sort(sortBy, sortDir));
        Specification<InvestigationCase> specification = Specification.where(matchesSearch(search))
                .and(matchesStatus(status))
                .and(matchesPriority(priority))
                .and((root, query, cb) -> caseAccessService.restrictToViewableCases(root, cb, authentication));
        var result = caseRepository.findAll(specification, pageable);
        return new CasePageResponse(
                result.getContent().stream().map(caseMapper::toListItem).toList(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.getNumber(),
                result.getSize(),
                result.isFirst(),
                result.isLast()
        );
    }

    @Transactional(readOnly = true)
    public CaseDetailResponse getCase(Long id, Authentication authentication) {
        InvestigationCase entity = caseAccessService.requireViewable(id, authentication);
        CaseAccessService.Actor actor = caseAccessService.actor(authentication);
        auditService.record(new AuditService.Entry(null, authentication.getName(), "CASE_VIEWED",
                "Viewed case " + entity.getCaseNumber(), true, id, "CASE", String.valueOf(id), null));

        var investigators = List.of(new InvestigatorAssignment(entity.getLeadInvestigator(), "Lead Investigator", "PRIMARY"));
        var devices = deviceRepository.findByInvestigationCaseId(id).stream()
                .map(item -> new DeviceItem(item.getId(), item.getDeviceName(), item.getOperatingSystem(), item.getAcquisitionStatus(),
                        item.getImei(), evidenceRepository.countByDeviceId(item.getId())))
                .toList();

        Map<String, Long> byType = new LinkedHashMap<>();
        long totalRecords = 0;
        for (Object[] row : evidenceRepository.countByArtifactTypeForCase(id)) {
            long count = ((Number) row[1]).longValue();
            byType.put(row[0] == null ? "UNKNOWN" : row[0].toString(), count);
            totalRecords += count;
        }
        var files = fileRepository.findByInvestigationCaseIdOrderByImportedAtDesc(id);
        long verified = files.stream().filter(f -> f.getIntegrityStatus() == EvidenceSourceFile.IntegrityStatus.VERIFIED).count();
        long integrityIssues = files.stream().filter(f -> f.getIntegrityStatus() == EvidenceSourceFile.IntegrityStatus.MISMATCH
                || f.getIntegrityStatus() == EvidenceSourceFile.IntegrityStatus.MISSING).count();
        long failed = files.stream().filter(f -> f.getProcessingStatus() == EvidenceSourceFile.ProcessingStatus.FAILED).count();
        var evidenceSummary = new EvidenceSummary(totalRecords, files.size(), byType, verified, integrityIssues, failed);

        var findings = findingRepository.findByCaseIdOrderByCreatedAtAsc(id);
        var findingSummary = new FindingSummary(
                findings.size(),
                findings.stream().filter(f -> f.getReviewStatus() == InvestigationFinding.ReviewStatus.NEEDS_REVIEW).count(),
                findings.stream().filter(f -> f.getReviewStatus() == InvestigationFinding.ReviewStatus.REVIEWED).count(),
                findings.stream().filter(InvestigationFinding::isAiGenerated).count()
        );

        var reports = reportRepository.findByInvestigationCaseId(id).stream()
                .sorted(Comparator.comparing((com.forensics.platform.entity.Report r) -> r.getGeneratedAt() == null ? java.time.Instant.EPOCH : r.getGeneratedAt()).reversed())
                .map(item -> new ReportItem(item.getId(), item.getReportNumber(), item.getReportTitle(), item.getStatus(), item.getGeneratedAt(),
                        item.getSnapshotJson() != null))
                .toList();

        // Case-linked audit entries, plus older entries that only mention the case number in their description.
        Map<Long, ActivityItem> activity = new TreeMap<>(Comparator.reverseOrder());
        auditLogRepository.findTop200ByCaseIdOrderByIdDesc(id).forEach(item -> activity.put(item.getId(), toActivity(item)));
        auditLogRepository.findTop100ByDescriptionContainingIgnoreCaseOrderByTimestampDesc(entity.getCaseNumber())
                .forEach(item -> activity.putIfAbsent(item.getId(), toActivity(item)));

        return new CaseDetailResponse(
                caseMapper.toResponse(entity),
                investigators,
                devices,
                evidenceSummary,
                findingSummary,
                reports,
                activity.values().stream().limit(200).toList(),
                caseAccessService.canEdit(entity, actor),
                actor.admin() && totalRecords == 0 && files.isEmpty()
        );
    }

    @Transactional
    public CaseResponse createCase(CaseRequest request, Authentication authentication) {
        CaseAccessService.Actor actor = caseAccessService.actor(authentication);
        if (!actor.canWrite()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient permission");
        }
        if (caseRepository.existsByCaseNumberIgnoreCase(request.caseNumber().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Case number already exists");
        }
        // Investigators always lead the cases they open; administrators may assign anyone.
        String assignedLead = actor.admin() ? request.leadInvestigator() : actor.fullName();
        InvestigationCase entity = caseMapper.toEntity(withLead(request, assignedLead));
        InvestigationCase saved = caseRepository.save(entity);
        auditService.record(new AuditService.Entry(null, authentication.getName(), "CASE_CREATED",
                "Created case " + saved.getCaseNumber(), true, saved.getId(), "CASE", String.valueOf(saved.getId()), null));
        return caseMapper.toResponse(saved);
    }

    @Transactional
    public CaseResponse updateCase(Long id, CaseRequest request, Authentication authentication) {
        InvestigationCase entity = caseAccessService.requireEditable(id, authentication);
        if (caseRepository.existsByCaseNumberIgnoreCaseAndIdNot(request.caseNumber().trim(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Case number already exists");
        }
        // Only administrators can reassign the lead investigator.
        String assignedLead = caseAccessService.actor(authentication).admin() ? request.leadInvestigator() : entity.getLeadInvestigator();
        String previousStatus = entity.getStatus().name();
        caseMapper.applyRequest(entity, withLead(request, assignedLead));
        InvestigationCase saved = caseRepository.save(entity);
        String change = previousStatus.equals(saved.getStatus().name()) ? "" : " (status " + previousStatus + " → " + saved.getStatus() + ")";
        auditService.record(new AuditService.Entry(null, authentication.getName(), "CASE_UPDATED",
                "Updated case " + saved.getCaseNumber() + change, true, id, "CASE", String.valueOf(id), null));
        return caseMapper.toResponse(saved);
    }

    /**
     * Deletes a case only when it holds no evidence. Cases with evidence must be archived instead,
     * so original evidence, derived records and their audit trail are never destroyed.
     */
    @Transactional
    public void deleteCase(Long id, Authentication authentication) {
        if (!caseAccessService.actor(authentication).admin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only administrators can delete cases");
        }
        InvestigationCase entity = caseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found"));
        if (fileRepository.countByInvestigationCaseId(id) > 0 || evidenceRepository.countByDeviceInvestigationCaseId(id) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This case contains evidence and cannot be deleted. Set its status to ARCHIVED to preserve the evidence and audit trail.");
        }
        reportRepository.deleteByInvestigationCaseId(id);
        deviceRepository.deleteByInvestigationCaseId(id);
        caseRepository.delete(entity);
        auditService.record(new AuditService.Entry(null, authentication.getName(), "CASE_DELETED",
                "Deleted case " + entity.getCaseNumber() + " (no evidence was attached)", true, id, "CASE", String.valueOf(id), null));
    }

    private CaseRequest withLead(CaseRequest request, String lead) {
        return new CaseRequest(request.caseNumber(), request.title(), request.description(), request.status(), request.priority(),
                lead, request.category(), request.caseType(), request.location(), request.notes());
    }

    private ActivityItem toActivity(com.forensics.platform.entity.AuditLog item) {
        return new ActivityItem(item.getId(), item.getUsername(), item.getAction(), item.getDescription(), item.getTimestamp(), item.isSuccess());
    }

    private Specification<InvestigationCase> matchesSearch(String search) {
        return (root, query, cb) -> {
            if (search == null || search.isBlank()) {
                return cb.conjunction();
            }
            String q = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("caseNumber")), q),
                    cb.like(cb.lower(root.get("title")), q),
                    cb.like(cb.lower(root.get("description")), q),
                    cb.like(cb.lower(root.get("leadInvestigator")), q),
                    cb.like(cb.lower(root.get("category")), q),
                    cb.like(cb.lower(root.get("caseType")), q),
                    cb.like(cb.lower(root.get("location")), q)
            );
        };
    }

    private Specification<InvestigationCase> matchesStatus(String status) {
        if (status == null || status.isBlank()) {
            return (root, query, cb) -> cb.conjunction();
        }
        CaseStatus parsed;
        try {
            parsed = CaseStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status filter");
        }
        return (root, query, cb) -> cb.equal(root.get("status"), parsed);
    }

    private Specification<InvestigationCase> matchesPriority(String priority) {
        if (priority == null || priority.isBlank()) {
            return (root, query, cb) -> cb.conjunction();
        }
        CasePriority parsed;
        try {
            parsed = CasePriority.valueOf(priority.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid priority filter");
        }
        return (root, query, cb) -> cb.equal(root.get("priority"), parsed);
    }

    private Sort sort(String sortBy, String sortDir) {
        String property = switch (sortBy == null ? "updatedAt" : sortBy) {
            case "caseNumber" -> "caseNumber";
            case "title" -> "title";
            case "status" -> "status";
            case "priority" -> "priority";
            case "leadInvestigator" -> "leadInvestigator";
            case "createdAt" -> "createdAt";
            default -> "updatedAt";
        };
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, property).and(Sort.by(Sort.Direction.ASC, "id"));
    }
}
