package com.forensics.platform.dto;

import java.time.Instant;
import java.util.List;

public final class AuditDtos {
    private AuditDtos() {}

    public record AuditLogResponse(
            Long id, Long userId, String username, String action, Instant timestamp,
            String description, String ipAddress, String userAgent, boolean success,
            Long caseId, String entityType, String entityId, boolean chained
    ) {}

    public record AuditPage(List<AuditLogResponse> items, long totalElements, int totalPages, int page, int size) {}
}
