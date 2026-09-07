package com.forensics.platform.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@Table(
        name = "normalized_evidence",
        indexes = {
                @Index(name = "idx_normalized_evidence_device", columnList = "device_id"),
                @Index(name = "idx_normalized_evidence_case", columnList = "case_id"),
                @Index(name = "idx_normalized_evidence_artifact_type", columnList = "artifact_type"),
                @Index(name = "idx_normalized_evidence_category", columnList = "category"),
                @Index(name = "idx_normalized_evidence_event_time", columnList = "event_time"),
                @Index(name = "idx_normalized_evidence_source_tool", columnList = "source_tool")
        }
)
public class NormalizedEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "artifact_type", nullable = false, length = 120)
    private String artifactType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NormalizedEvidenceCategory category;

    @Column(name = "source_tool", nullable = false, length = 80)
    private String sourceTool;

    @Column(columnDefinition = "TEXT")
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "event_time")
    private Instant eventTime;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private InvestigationCase investigationCase;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_evidence_id", unique = true)
    private Evidence sourceEvidence;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
