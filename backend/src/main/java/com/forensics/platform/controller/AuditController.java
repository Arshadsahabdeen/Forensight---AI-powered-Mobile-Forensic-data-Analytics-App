package com.forensics.platform.controller;

import com.forensics.platform.dto.AuditDtos;
import com.forensics.platform.repository.AuditLogRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/audit")
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {
    private final AuditLogRepository auditLogRepository;

    public AuditController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping
    public List<AuditDtos.AuditLogResponse> logs() {
        return auditLogRepository.findTop100ByOrderByTimestampDesc().stream()
                .map(log -> new AuditDtos.AuditLogResponse(
                        log.getId(), log.getUserId(), log.getUsername(), log.getAction(),
                        log.getTimestamp(), log.getDescription(), log.getIpAddress(),
                        log.getUserAgent(), log.isSuccess()))
                .toList();
    }
}