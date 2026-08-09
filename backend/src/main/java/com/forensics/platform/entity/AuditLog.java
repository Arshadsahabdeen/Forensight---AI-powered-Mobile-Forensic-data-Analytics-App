package com.forensics.platform.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
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
