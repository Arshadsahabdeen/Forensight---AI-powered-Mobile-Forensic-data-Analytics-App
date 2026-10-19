package com.forensics.platform.repository;

import com.forensics.platform.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface DeviceRepository extends JpaRepository<Device, Long>, JpaSpecificationExecutor<Device> {
    List<Device> findByInvestigationCaseId(Long caseId);

    void deleteByInvestigationCaseId(Long caseId);

    Optional<Device> findByInvestigationCaseIdAndImei(Long caseId, String imei);

    boolean existsByInvestigationCaseIdAndImeiIgnoreCase(Long caseId, String imei);
}
