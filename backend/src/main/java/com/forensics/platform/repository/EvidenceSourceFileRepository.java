package com.forensics.platform.repository;

import com.forensics.platform.entity.EvidenceSourceFile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface EvidenceSourceFileRepository extends JpaRepository<EvidenceSourceFile, Long> {

    @EntityGraph(attributePaths = {"investigationCase", "device"})
    List<EvidenceSourceFile> findByInvestigationCaseIdOrderByImportedAtDesc(Long caseId);

    @EntityGraph(attributePaths = {"investigationCase", "device"})
    List<EvidenceSourceFile> findByDeviceIdOrderByImportedAtDesc(Long deviceId);

    @EntityGraph(attributePaths = {"investigationCase", "device"})
    Optional<EvidenceSourceFile> findWithCaseById(Long id);

    Optional<EvidenceSourceFile> findByInvestigationCaseIdAndSha256(Long caseId, String sha256);

    long countByInvestigationCaseId(Long caseId);

    long countByDeviceId(Long deviceId);

    @Query("select f.integrityStatus, count(f) from EvidenceSourceFile f group by f.integrityStatus")
    List<Object[]> countByIntegrityStatus();

    @Query("select f.integrityStatus, count(f) from EvidenceSourceFile f where f.investigationCase.id in :caseIds group by f.integrityStatus")
    List<Object[]> countByIntegrityStatusForCases(java.util.Collection<Long> caseIds);
}
