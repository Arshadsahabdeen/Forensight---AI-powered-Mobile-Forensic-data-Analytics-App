package com.forensics.platform.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Append-only audit record. Rows are written once by {@code AuditService}; the database blocks
 * UPDATE/DELETE (see migration V10) and each row carries a SHA-256 hash chained to the previous row.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "audit_log",
        indexes = {
                @Index(name = "idx_audit_log_case_id", columnList = "case_id"),
                @Index(name = "idx_audit_log_entity", columnList = "entity_type, entity_id"),
                @Index(name = "idx_audit_log_action", columnList = "action")
        }
)
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    private String username;
    private String action;
    private Instant timestamp = Instant.now();
    @Column(length = 1000)
    private String description;
    private String ipAddress;
    @Column(length = 512)
    private String userAgent;
    private boolean success = true;

    @Column(name = "case_id")
    private Long caseId;

    @Column(name = "entity_type", length = 60)
    private String entityType;

    @Column(name = "entity_id", length = 80)
    private String entityId;

    @Column(name = "previous_hash", length = 64)
    private String previousHash;

    @Column(name = "entry_hash", length = 64)
    private String entryHash;

    public AuditLog(String username, String action, String description) {
        this.username = username;
        this.action = action;
        this.description = description;
    }

    public AuditLog(Long userId, String username, String action, String description, String ipAddress, String userAgent, boolean success) {
        this.userId = userId;
        this.username = username;
        this.action = action;
        this.description = description;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.success = success;
    }
}
