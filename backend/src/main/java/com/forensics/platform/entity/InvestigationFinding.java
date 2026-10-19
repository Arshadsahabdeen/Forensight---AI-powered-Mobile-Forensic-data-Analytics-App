package com.forensics.platform.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * An investigator-curated finding. Each finding states its evidential basis and links the
 * evidence records that support it. AI-suggested findings must be reviewed before they are
 * presented as reviewed content in a report.
 */
@Entity
@Getter
@Setter
@Table(name = "investigation_finding", indexes = @Index(name = "idx_investigation_finding_case", columnList = "case_id"))
public class InvestigationFinding {

    public enum Basis { VERIFIED_FACT, INFERENCE, AI_SUGGESTED }

    public enum Significance { LOW, MEDIUM, HIGH }

    public enum ReviewStatus { DRAFT, NEEDS_REVIEW, REVIEWED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "case_id", nullable = false)
    private Long caseId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Basis basis = Basis.VERIFIED_FACT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Significance significance = Significance.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 20)
    private ReviewStatus reviewStatus = ReviewStatus.DRAFT;

    @Column(name = "ai_generated", nullable = false)
    private boolean aiGenerated;

    @Column(name = "ai_engine", length = 120)
    private String aiEngine;

    @Column(name = "include_in_report", nullable = false)
    private boolean includeInReport = true;

    @Column(name = "narrative_order")
    private Integer narrativeOrder;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "investigation_finding_evidence", joinColumns = @JoinColumn(name = "finding_id"))
    @Column(name = "evidence_id", nullable = false)
    private Set<Long> evidenceIds = new LinkedHashSet<>();

    @Column(name = "created_by", nullable = false, length = 120)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_by", length = 120)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "reviewed_by", length = 120)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_comment", length = 1000)
    private String reviewComment;
}
