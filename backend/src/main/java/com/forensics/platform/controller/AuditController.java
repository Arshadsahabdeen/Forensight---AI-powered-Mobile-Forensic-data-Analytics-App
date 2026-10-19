package com.forensics.platform.controller;

import com.forensics.platform.dto.AuditDtos;
import com.forensics.platform.entity.AuditLog;
import com.forensics.platform.repository.AuditLogRepository;
import com.forensics.platform.service.AuditService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Read-only access to the audit trail. There are deliberately no update or delete endpoints.
 */
@RestController
@RequestMapping("/api/v1/audit")
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {
    private final AuditLogRepository auditLogRepository;
    private final AuditService auditService;

    public AuditController(AuditLogRepository auditLogRepository, AuditService auditService) {
        this.auditLogRepository = auditLogRepository;
        this.auditService = auditService;
    }

    @GetMapping
    public AuditDtos.AuditPage logs(@RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "50") int size,
                                    @RequestParam(required = false) String action,
                                    @RequestParam(required = false) String username,
                                    @RequestParam(required = false) Boolean success,
                                    @RequestParam(required = false) Long caseId,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                    @RequestParam(required = false) String search) {
        if (page < 0 || size < 1 || size > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be >= 0 and size between 1 and 200");
        }
        Page<AuditLog> result = auditLogRepository.findAll(spec(action, username, success, caseId, from, to, search),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
        return new AuditDtos.AuditPage(result.getContent().stream().map(AuditController::toResponse).toList(),
                result.getTotalElements(), result.getTotalPages(), result.getNumber(), result.getSize());
    }

    @GetMapping("/actions")
    public List<String> actions() {
        return auditLogRepository.findDistinctActions();
    }

    @PostMapping("/verify")
    public AuditService.ChainVerification verify(Authentication authentication) {
        AuditService.ChainVerification result = auditService.verifyChain();
        auditService.record(new AuditService.Entry(null, authentication.getName(), "AUDIT_CHAIN_VERIFIED",
                "Audit hash chain verification: " + result.status() + " (" + result.recordsChecked() + " chained record(s))",
                "INTACT".equals(result.status()), null, null, null, null));
        return result;
    }

    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String action,
                                         @RequestParam(required = false) String username,
                                         @RequestParam(required = false) Boolean success,
                                         @RequestParam(required = false) Long caseId,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                         @RequestParam(required = false) String search,
                                         Authentication authentication) {
        List<AuditLog> rows = auditLogRepository.findAll(spec(action, username, success, caseId, from, to, search),
                PageRequest.of(0, 10_000, Sort.by(Sort.Direction.ASC, "id"))).getContent();
        StringBuilder csv = new StringBuilder("id,timestamp_utc,actor,action,success,case_id,entity_type,entity_id,ip_address,description,previous_hash,entry_hash\n");
        for (AuditLog log : rows) {
            csv.append(log.getId()).append(',')
                    .append(cell(log.getTimestamp() == null ? "" : log.getTimestamp().toString())).append(',')
                    .append(cell(log.getUsername())).append(',')
                    .append(cell(log.getAction())).append(',')
                    .append(log.isSuccess()).append(',')
                    .append(log.getCaseId() == null ? "" : log.getCaseId()).append(',')
                    .append(cell(log.getEntityType())).append(',')
                    .append(cell(log.getEntityId())).append(',')
                    .append(cell(log.getIpAddress())).append(',')
                    .append(cell(log.getDescription())).append(',')
                    .append(cell(log.getPreviousHash())).append(',')
                    .append(cell(log.getEntryHash())).append('\n');
        }
        auditService.record(new AuditService.Entry(null, authentication.getName(), "AUDIT_EXPORTED",
                "Exported " + rows.size() + " audit record(s) as CSV", true, caseId, null, null, null));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("audit-log.csv").build().toString())
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private Specification<AuditLog> spec(String action, String username, Boolean success, Long caseId, Instant from, Instant to, String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (action != null && !action.isBlank()) predicates.add(cb.equal(root.get("action"), action.trim()));
            if (username != null && !username.isBlank()) predicates.add(cb.equal(cb.lower(root.get("username")), username.trim().toLowerCase(Locale.ROOT)));
            if (success != null) predicates.add(cb.equal(root.get("success"), success));
            if (caseId != null) predicates.add(cb.equal(root.get("caseId"), caseId));
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), from));
            if (to != null) predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), to));
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("description")), like),
                        cb.like(cb.lower(root.get("username")), like),
                        cb.like(cb.lower(root.get("action")), like),
                        cb.like(cb.lower(root.get("ipAddress")), like)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static AuditDtos.AuditLogResponse toResponse(AuditLog log) {
        return new AuditDtos.AuditLogResponse(log.getId(), log.getUserId(), log.getUsername(), log.getAction(), log.getTimestamp(),
                log.getDescription(), log.getIpAddress(), log.getUserAgent(), log.isSuccess(), log.getCaseId(), log.getEntityType(),
                log.getEntityId(), log.getEntryHash() != null);
    }

    /** CSV cell with quoting and spreadsheet-formula neutralization. */
    private static String cell(String value) {
        if (value == null) {
            return "";
        }
        String safe = value;
        if (!safe.isEmpty() && "=+-@".indexOf(safe.charAt(0)) >= 0) {
            safe = "'" + safe;
        }
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }
}
