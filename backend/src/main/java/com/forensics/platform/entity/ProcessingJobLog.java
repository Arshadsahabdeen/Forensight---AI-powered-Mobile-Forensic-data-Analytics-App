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
@Table(name = "processing_job_log", indexes = @Index(name = "idx_processing_job_log_job", columnList = "job_id"))
public class ProcessingJobLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "logged_at", nullable = false)
    private Instant loggedAt = Instant.now();

    @Column(nullable = false, length = 10)
    private String level;

    @Column(nullable = false, length = 2000)
    private String message;

    public ProcessingJobLog(Long jobId, String level, String message) {
        this.jobId = jobId;
        this.level = level;
        this.message = message.length() > 2000 ? message.substring(0, 2000) : message;
    }
}
