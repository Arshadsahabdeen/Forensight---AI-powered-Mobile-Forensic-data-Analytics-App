package com.forensics.platform.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "report_section_selection", uniqueConstraints = {
        @UniqueConstraint(name = "uk_report_section_selection", columnNames = {"report_id", "section_key"})
})
public class ReportSectionSelection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "report_id", nullable = false)
    private Report report;

    @Column(name = "section_key", nullable = false, length = 80)
    private String sectionKey;

    @Column(name = "section_label", nullable = false, length = 120)
    private String sectionLabel;

    @Column(name = "section_order")
    private Integer sectionOrder;

    private boolean enabled = true;
}