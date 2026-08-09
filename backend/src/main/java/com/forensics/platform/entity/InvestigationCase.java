package com.forensics.platform.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "investigation_case", indexes = {
    @Index(name = "idx_case_number", columnList = "case_number", unique = true),
    @Index(name = "idx_case_status", columnList = "status"),
    @Index(name = "idx_case_priority", columnList = "priority"),
    @Index(name = "idx_case_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
public class InvestigationCase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 80)
    private String caseNumber;
    @Column(nullable = false, length = 180)
    private String title;
    @Column(nullable = false, length = 2000)
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CaseStatus status = CaseStatus.OPEN;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CasePriority priority = CasePriority.MEDIUM;
    @Column(nullable = false, length = 120)
    private String category;
    @Column(nullable = false, length = 120)
    private String caseType;
    @Column(length = 255)
    private String location;
    @Column(length = 4000)
    private String notes;
    @Column(nullable = false, length = 120)
    private String leadInvestigator;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();
    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @OneToMany(mappedBy = "investigationCase")
    private List<Device> devices = new ArrayList<>();

    @OneToMany(mappedBy = "investigationCase")
    private List<Report> reports = new ArrayList<>();

    @PrePersist
    public void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }
}
