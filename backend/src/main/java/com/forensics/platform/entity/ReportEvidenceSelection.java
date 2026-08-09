package com.forensics.platform.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@Table(name = "report_evidence_selection", uniqueConstraints = {
        @UniqueConstraint(name = "uk_report_evidence_selection", columnNames = {"report_id", "evidence_id"})
})
public class ReportEvidenceSelection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "report_id", nullable = false)
    private Report report;

    @ManyToOne(optional = false)
    @JoinColumn(name = "evidence_id", nullable = false)
    private Evidence evidence;

    private Instant selectedAt = Instant.now();
}