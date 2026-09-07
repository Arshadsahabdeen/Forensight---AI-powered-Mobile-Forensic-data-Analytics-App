package com.forensics.platform.dto;

import java.time.Instant;

public final class AuditDtos {
    private AuditDtos() {}

    public record AuditLogResponse(
            Long id, Long userId, String username, String action, Instant timestamp,
            String description, String ipAddress, String userAgent, boolean success
    ) {}
}