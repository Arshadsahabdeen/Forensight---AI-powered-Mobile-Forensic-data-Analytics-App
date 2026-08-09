package com.forensics.platform.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
public class TimelineEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Instant occurredAt;
    private String eventType;
    @Column(length = 1000)
    private String description;

    @ManyToOne
    private InvestigationCase investigationCase;
}
