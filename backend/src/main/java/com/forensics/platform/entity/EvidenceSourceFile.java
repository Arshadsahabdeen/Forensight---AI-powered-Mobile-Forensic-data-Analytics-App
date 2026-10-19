package com.forensics.platform.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * An original evidence file as received by the platform. The bytes are stored read-only under
 * {@link #storageKey}; {@link #sha256} is computed while the upload is streamed to storage.
 */
@Entity
@Getter
@Setter
@Table(
        name = "evidence_source_file",
        uniqueConstraints = @UniqueConstraint(name = "uk_evidence_file_case_hash", columnNames = {"case_id", "sha256"}),
        indexes = {
                @Index(name = "idx_evidence_file_case", columnList = "case_id"),
                @Index(name = "idx_evidence_file_device", columnList = "device_id")
        }
)
public class EvidenceSourceFile {

    public enum ProcessingStatus { RECEIVED, PROCESSING, PROCESSED, FAILED }

    public enum IntegrityStatus { NOT_VERIFIED, VERIFIED, MISMATCH, MISSING }

    public enum SourceFormat { XML, UFDR, ZIP }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private InvestigationCase investigationCase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private Device device;

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    @Column(name = "storage_key", nullable = false, length = 512)
    private String storageKey;

    @Column(name = "content_type", length = 120)
    private String contentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_format", nullable = false, length = 20)
    private SourceFormat sourceFormat;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "imported_by", nullable = false, length = 120)
    private String importedBy;

    @Column(name = "imported_at", nullable = false)
    private Instant importedAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 20)
    private ProcessingStatus processingStatus = ProcessingStatus.RECEIVED;

    @Enumerated(EnumType.STRING)
    @Column(name = "integrity_status", nullable = false, length = 20)
    private IntegrityStatus integrityStatus = IntegrityStatus.NOT_VERIFIED;

    @Column(name = "last_verified_at")
    private Instant lastVerifiedAt;

    @Column(name = "last_verified_by", length = 120)
    private String lastVerifiedBy;

    @Column(name = "records_extracted", nullable = false)
    private int recordsExtracted;

    @Column(name = "duplicates_skipped", nullable = false)
    private int duplicatesSkipped;

    @Column(name = "timestamp_issues", nullable = false)
    private int timestampIssues;
}
