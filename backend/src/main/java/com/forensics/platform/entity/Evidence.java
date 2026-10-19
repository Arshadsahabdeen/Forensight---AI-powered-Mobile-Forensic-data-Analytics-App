package com.forensics.platform.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@Table(
        name = "evidence",
        indexes = {
                @Index(name = "idx_evidence_artifact_type", columnList = "artifact_type"),
                @Index(name = "idx_evidence_device", columnList = "device_id"),
                @Index(name = "idx_evidence_created_at", columnList = "created_at"),
                @Index(name = "idx_evidence_source_file", columnList = "source_file_id"),
                @Index(name = "idx_evidence_device_record_hash", columnList = "device_id, record_hash")
        }
)
public class Evidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "artifact_type", length = 100)
    private String artifactType;

    @Column(length = 255)
    private String fileName;

    @Column(length = 64)
    private String hashSha256;

    /*
     * Generic forensic artifact information.
     */
    @Column(columnDefinition = "TEXT")
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(length = 255)
    private String source;

    @Column(length = 255)
    private String target;

    @Column(length = 255)
    private String phoneNumber;

    @Column(length = 255)
    private String contactName;

    @Column(length = 255)
    private String location;

    private Double latitude;

    private Double longitude;

    /*
     * Original artifact timestamp when available.
     */
    private Instant eventTime;

    /*
     * Timestamp exactly as it appeared in the source file, the timezone/offset it declared
     * (if any), and how the normalized eventTime was derived from it.
     */
    @Column(length = 120)
    private String originalTimestamp;

    @Column(length = 40)
    private String originalTimezone;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TimestampStatus timestampStatus;

    /*
     * Provenance: the original evidence file and the location of this record inside it.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_file_id")
    private EvidenceSourceFile sourceFile;

    @Column(length = 255)
    private String sourceRecordRef;

    /*
     * SHA-256 over the record's extracted fields; used to skip duplicate records per device.
     */
    @Column(length = 64)
    private String recordHash;

    /*
     * Database insertion timestamp.
     */
    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private Device device;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}