package com.forensics.platform.service;

import com.forensics.platform.entity.AuditLog;
import com.forensics.platform.entity.User;
import com.forensics.platform.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void record(String username, String action, String description) {
        auditLogRepository.save(new AuditLog(username, action, description));
    }

    public void record(User user, String action, String description, HttpServletRequest request, boolean success) {
        auditLogRepository.save(new AuditLog(
                user == null ? null : user.getId(),
                user == null ? null : user.getUsername(),
                action,
                description,
                clientIp(request),
                request == null ? null : request.getHeader("User-Agent"),
                success
        ));
    }

    public void record(String username, String action, String description, HttpServletRequest request, boolean success) {
        AuditLog log = new AuditLog(username, action, description);
        log.setIpAddress(clientIp(request));
        log.setUserAgent(request == null ? null : request.getHeader("User-Agent"));
        log.setSuccess(success);
        auditLogRepository.save(log);
    }

    private String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
