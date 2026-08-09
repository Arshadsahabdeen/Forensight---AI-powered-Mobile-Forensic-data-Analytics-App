package com.forensics.platform.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Getter
@Setter
@Table(name = "device", indexes = {
        @Index(name = "idx_device_imei", columnList = "imei", unique = true),
        @Index(name = "idx_device_status", columnList = "acquisition_status"),
        @Index(name = "idx_device_case", columnList = "investigation_case_id"),
        @Index(name = "idx_device_manufacturer", columnList = "manufacturer")
})
public class Device {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(length = 120)
    private String manufacturer;
    @Column(length = 120)
    private String brand;
    @Column(length = 120)
    private String model;
    @Column(length = 160)
    private String deviceName;
    @Column(length = 40, unique = true)
    private String imei;
    @Column(length = 120)
    private String serialNumber;
    @Column(length = 120)
    private String operatingSystem;
    @Column(length = 80)
    private String osVersion;
    @Column(length = 80)
    private String deviceType;
    @Column(length = 160)
    private String ownerName;
    @Column(length = 160)
    private String ownerContact;
    @Column(length = 120)
    private String investigator;
    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private AcquisitionMethod acquisitionMethod = AcquisitionMethod.LOGICAL_EXTRACTION;
    @Enumerated(EnumType.STRING)
    @Column(name = "acquisition_status", length = 40)
    private DeviceStatus acquisitionStatus = DeviceStatus.SEIZED;
    private LocalDate seizedDate;
    private LocalDate receivedDate;
    @Column(length = 4000)
    private String notes;
    @Column(length = 120)
    private String createdBy;
    @Column(length = 120)
    private String updatedBy;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();
    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @ManyToOne
    @JoinColumn(name = "investigation_case_id")
    private InvestigationCase investigationCase;

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
