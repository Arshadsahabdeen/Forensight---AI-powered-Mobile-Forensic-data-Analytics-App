package com.forensics.platform.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@Table(
        name = "processing_job",
        indexes = {
                @Index(name = "idx_processing_job_case", columnList = "case_id"),
                @Index(name = "idx_processing_job_status", columnList = "status"),
                @Index(name = "idx_processing_job_file", columnList = "evidence_file_id")
        }
)
public class ProcessingJob {

    public enum JobType { EVIDENCE_IMPORT }

    public enum Status { QUEUED, RUNNING, SUCCEEDED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 40)
    private JobType jobType = JobType.EVIDENCE_IMPORT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.QUEUED;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private InvestigationCase investigationCase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private Device device;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evidence_file_id")
    private EvidenceSourceFile evidenceFile;

    @Column(nullable = false)
    private int progress;

    @Column(length = 160)
    private String stage;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "created_by", nullable = false, length = 120)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;
}
