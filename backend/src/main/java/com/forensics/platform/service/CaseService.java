package com.forensics.platform.service;

import com.forensics.platform.dto.CaseDtos.*;
import com.forensics.platform.entity.CasePriority;
import com.forensics.platform.entity.CaseStatus;
import com.forensics.platform.entity.InvestigationCase;
import com.forensics.platform.mapper.CaseMapper;
import com.forensics.platform.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class CaseService {
    private final CaseRepository caseRepository;
    private final DeviceRepository deviceRepository;
    private final EvidenceRepository evidenceRepository;
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditService auditService;
    private final CaseMapper caseMapper;

    public CaseService(CaseRepository caseRepository, DeviceRepository deviceRepository, EvidenceRepository evidenceRepository, ReportRepository reportRepository, UserRepository userRepository, AuditLogRepository auditLogRepository, AuditService auditService, CaseMapper caseMapper) {
        this.caseRepository = caseRepository;
        this.deviceRepository = deviceRepository;
        this.evidenceRepository = evidenceRepository;
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.auditService = auditService;
        this.caseMapper = caseMapper;
    }

    @Transactional(readOnly = true)
    public CasePageResponse listCases(int page, int size, String sortBy, String sortDir, String search, String status, String priority, Authentication authentication) {
        var pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(size, 1), 100), sort(sortBy, sortDir));
        Specification<InvestigationCase> specification = filters(search, status, priority, authentication);
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

    @Transactional
    public CaseDetailResponse getCase(Long id, Authentication authentication) {
        InvestigationCase entity = caseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found"));
        enforceCanView(entity, authentication);
        auditService.record(authentication.getName(), "CASE_VIEWED", "Viewed case " + entity.getCaseNumber());

        var investigators = List.of(new InvestigatorAssignment(entity.getLeadInvestigator(), "Lead Investigator", "PRIMARY"));
        var devices = deviceRepository.findByInvestigationCaseId(id).stream()
                .map(item -> new DeviceItem(item.getId(), item.getDeviceName(), item.getOperatingSystem(), item.getAcquisitionStatus()))
                .toList();
        var evidence = evidenceRepository.findByDeviceInvestigationCaseId(id).stream()
                .map(item -> new EvidenceItem(item.getId(), item.getArtifactType(), item.getFileName(), item.getHashSha256(), item.getDevice() == null ? null : item.getDevice().getDeviceName(), item.getCreatedAt()))
                .toList();
        var reports = reportRepository.findByInvestigationCaseId(id).stream()
                .map(item -> new ReportItem(item.getId(), item.getReportNumber(), item.getStatus(), item.getGeneratedAt()))
                .toList();
        var activity = auditLogRepository.findTop100ByDescriptionContainingIgnoreCaseOrderByTimestampDesc(entity.getCaseNumber()).stream()
                .map(item -> new ActivityItem(item.getId(), item.getUsername(), item.getAction(), item.getDescription(), item.getTimestamp()))
                .toList();

        return new CaseDetailResponse(
                caseMapper.toResponse(entity),
                investigators,
                devices,
                evidence,
                activity,
                reports,
                entity.getNotes(),
                activity
        );
    }

    @Transactional
    public CaseResponse createCase(CaseRequest request, Authentication authentication) {
        if (caseRepository.existsByCaseNumberIgnoreCase(request.caseNumber().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Case number already exists");
        }

        String assignedLead = request.leadInvestigator();
        if (isInvestigatorOnly(authentication)) {
            assignedLead = currentInvestigatorName(authentication);
        }

        CaseRequest normalized = new CaseRequest(
                request.caseNumber(),
                request.title(),
                request.description(),
                request.status(),
                request.priority(),
                assignedLead,
                request.category(),
                request.caseType(),
                request.location(),
                request.notes()
        );

        InvestigationCase entity = caseMapper.toEntity(normalized);
        InvestigationCase saved = caseRepository.save(entity);
        auditService.record(authentication.getName(), "CASE_CREATED", "Created case " + saved.getCaseNumber());
        return caseMapper.toResponse(saved);
    }

    @Transactional
    public CaseResponse updateCase(Long id, CaseRequest request, Authentication authentication) {
        InvestigationCase entity = caseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found"));
        enforceCanEdit(entity, authentication);

        if (caseRepository.existsByCaseNumberIgnoreCaseAndIdNot(request.caseNumber().trim(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Case number already exists");
        }

        String assignedLead = isInvestigatorOnly(authentication) ? entity.getLeadInvestigator() : request.leadInvestigator();
        CaseRequest normalized = new CaseRequest(
                request.caseNumber(),
                request.title(),
                request.description(),
                request.status(),
                request.priority(),
                assignedLead,
                request.category(),
                request.caseType(),
                request.location(),
                request.notes()
        );

        caseMapper.applyRequest(entity, normalized);
        InvestigationCase saved = caseRepository.save(entity);
        auditService.record(authentication.getName(), "CASE_UPDATED", "Updated case " + saved.getCaseNumber());
        return caseMapper.toResponse(saved);
    }

    @Transactional
    public void deleteCase(Long id, Authentication authentication) {
        InvestigationCase entity = caseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found"));
        if (!hasRole(authentication, "ROLE_ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only administrators can delete cases");
        }

        evidenceRepository.deleteByDeviceInvestigationCaseId(id);
        reportRepository.deleteByInvestigationCaseId(id);
        deviceRepository.deleteByInvestigationCaseId(id);
        caseRepository.delete(entity);

        auditService.record(authentication.getName(), "CASE_DELETED", "Deleted case " + entity.getCaseNumber());
    }

    private Specification<InvestigationCase> filters(String search, String status, String priority, Authentication authentication) {
        return Specification.where(matchesSearch(search))
                .and(matchesStatus(status))
                .and(matchesPriority(priority))
                .and(assignedCasesFilter(authentication));
    }

    private Specification<InvestigationCase> matchesSearch(String search) {
        return (root, query, cb) -> {
            if (search == null || search.isBlank()) {
                return cb.conjunction();
            }
            String q = "%" + search.trim().toLowerCase() + "%";
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
        return (root, query, cb) -> {
            if (status == null || status.isBlank()) {
                return cb.conjunction();
            }
            try {
                return cb.equal(root.get("status"), CaseStatus.valueOf(status.trim().toUpperCase()));
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status filter");
            }
        };
    }

    private Specification<InvestigationCase> matchesPriority(String priority) {
        return (root, query, cb) -> {
            if (priority == null || priority.isBlank()) {
                return cb.conjunction();
            }
            try {
                return cb.equal(root.get("priority"), CasePriority.valueOf(priority.trim().toUpperCase()));
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid priority filter");
            }
        };
    }

    private Specification<InvestigationCase> assignedCasesFilter(Authentication authentication) {
        return (root, query, cb) -> {
            if (authentication == null || !isInvestigatorOnly(authentication)) {
                return cb.conjunction();
            }
            String principalName = authentication.getName();
            String investigatorName = currentInvestigatorName(authentication);
            return cb.or(
                    cb.equal(root.get("leadInvestigator"), principalName),
                    cb.equal(root.get("leadInvestigator"), investigatorName)
            );
        };
    }

    private void enforceCanView(InvestigationCase entity, Authentication authentication) {
        if (hasRole(authentication, "ROLE_ADMIN") || hasRole(authentication, "ROLE_REVIEWER")) {
            return;
        }
        if (!isAssignedToInvestigator(entity, authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Investigators can only access assigned cases");
        }
    }

    private void enforceCanEdit(InvestigationCase entity, Authentication authentication) {
        if (hasRole(authentication, "ROLE_ADMIN")) {
            return;
        }
        if (!hasRole(authentication, "ROLE_INVESTIGATOR")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient permission");
        }
        if (!isAssignedToInvestigator(entity, authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Investigators can only edit assigned cases");
        }
    }

    private boolean isAssignedToInvestigator(InvestigationCase entity, Authentication authentication) {
        String principalName = authentication.getName();
        String investigatorName = currentInvestigatorName(authentication);
        return principalName.equalsIgnoreCase(entity.getLeadInvestigator()) || investigatorName.equalsIgnoreCase(entity.getLeadInvestigator());
    }

    private String currentInvestigatorName(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName())
                .map(user -> user.getFullName())
                .orElse(authentication.getName());
    }

    private boolean isInvestigatorOnly(Authentication authentication) {
        return hasRole(authentication, "ROLE_INVESTIGATOR") && !hasRole(authentication, "ROLE_ADMIN");
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role::equals);
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
        return Sort.by(direction, property);
    }
}
