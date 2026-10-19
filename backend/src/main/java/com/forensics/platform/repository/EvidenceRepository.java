package com.forensics.platform.repository;

import com.forensics.platform.entity.Evidence;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EvidenceRepository
        extends JpaRepository<Evidence, Long>, JpaSpecificationExecutor<Evidence> {

    @Override
    @EntityGraph(attributePaths = {"device", "device.investigationCase", "sourceFile"})
    Page<Evidence> findAll(Specification<Evidence> specification, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"device", "device.investigationCase", "sourceFile"})
    List<Evidence> findAll(Specification<Evidence> specification);

    @EntityGraph(attributePaths = {"device", "device.investigationCase", "sourceFile"})
    Optional<Evidence> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"device", "device.investigationCase", "sourceFile"})
    List<Evidence> findByIdIn(Collection<Long> ids);

    @EntityGraph(attributePaths = "device")
    List<Evidence> findByDeviceInvestigationCaseId(Long caseId);

    /**
     * All records of a case with the associations needed for timeline/correlation building.
     */
    @Query("""
            select e from Evidence e
            join fetch e.device d
            join fetch d.investigationCase c
            left join fetch e.sourceFile f
            where c.id = :caseId
            """)
    List<Evidence> findAllForCase(@Param("caseId") Long caseId);

    long countByDeviceId(Long deviceId);

    long countByDeviceIdAndEventTimeIsNotNull(Long deviceId);

    long countByDeviceInvestigationCaseId(Long caseId);

    long countBySourceFileId(Long sourceFileId);

    List<Evidence> findByDeviceId(Long deviceId);

    boolean existsByDeviceIdAndRecordHash(Long deviceId, String recordHash);

    @Query("select e.recordHash from Evidence e where e.device.id = :deviceId and e.recordHash is not null")
    List<String> findRecordHashesForDevice(@Param("deviceId") Long deviceId);

    long countByCreatedAtBetween(Instant start, Instant end);

    @Query("select count(e) from Evidence e where e.device.investigationCase.id in :caseIds")
    long countForCases(@Param("caseIds") Collection<Long> caseIds);

    @Query("""
        select e.artifactType, count(e)
        from Evidence e
        group by e.artifactType
        order by count(e) desc
    """)
    List<Object[]> countByArtifactType();

    @Query("""
        select e.artifactType, count(e)
        from Evidence e
        where e.device.investigationCase.id = :caseId
        group by e.artifactType
        order by count(e) desc
    """)
    List<Object[]> countByArtifactTypeForCase(@Param("caseId") Long caseId);

    @Query("select distinct e.artifactType from Evidence e where e.artifactType is not null order by e.artifactType")
    List<String> findDistinctArtifactTypes();
}
