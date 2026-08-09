package com.forensics.platform.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "report")
public class Report {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String reportNumber;
    private String reportTitle;
    private String reportType;
    private String version;
    private String status;
    private Instant generatedAt;
    private String preparedBy;
    private String reviewedBy;
    private String approvedBy;
    private String generatedBy;
    @Column(columnDefinition = "text")
    private String notesHtml;
    @Column(columnDefinition = "text")
    private String aiSummary;
    @Column(columnDefinition = "text")
    private String timelineSummary;
    @Column(columnDefinition = "text")
    private String extractedKeywords;
    @Column(columnDefinition = "text")
    private String namedEntities;
    @Column(columnDefinition = "text")
    private String suspiciousFindings;
    private String riskLevel;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    @ManyToOne
    private InvestigationCase investigationCase;

    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReportDeviceSelection> deviceSelections = new ArrayList<>();

    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReportEvidenceSelection> evidenceSelections = new ArrayList<>();

    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReportSectionSelection> sectionSelections = new ArrayList<>();

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
