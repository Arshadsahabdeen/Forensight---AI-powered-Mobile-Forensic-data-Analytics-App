package com.forensics.platform.repository;

import com.forensics.platform.entity.NormalizedEvidence;
import com.forensics.platform.entity.NormalizedEvidenceCategory;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NormalizedEvidenceRepository extends JpaRepository<NormalizedEvidence, Long> {

    @EntityGraph(attributePaths = {"device", "investigationCase"})
    List<NormalizedEvidence> findByDeviceId(Long deviceId);

    @EntityGraph(attributePaths = {"device", "investigationCase"})
    List<NormalizedEvidence> findByDeviceIdOrderByEventTimeDescCreatedAtDesc(Long deviceId);

    @EntityGraph(attributePaths = {"device", "investigationCase"})
    List<NormalizedEvidence> findByArtifactTypeIgnoreCase(String artifactType);

    @EntityGraph(attributePaths = {"device", "investigationCase"})
    List<NormalizedEvidence> findByArtifactTypeIgnoreCaseOrderByEventTimeDescCreatedAtDesc(String artifactType);

    @EntityGraph(attributePaths = {"device", "investigationCase"})
    List<NormalizedEvidence> findByCategory(NormalizedEvidenceCategory category);

    @EntityGraph(attributePaths = {"device", "investigationCase"})
    List<NormalizedEvidence> findByCategoryOrderByEventTimeDescCreatedAtDesc(NormalizedEvidenceCategory category);

    @EntityGraph(attributePaths = {"device", "investigationCase"})
    List<NormalizedEvidence> findByDeviceIdAndCategoryOrderByEventTimeDescCreatedAtDesc(
            Long deviceId,
            NormalizedEvidenceCategory category
    );

    @EntityGraph(attributePaths = {"device", "investigationCase"})
    Optional<NormalizedEvidence> findById(Long id);

    Optional<NormalizedEvidence> findBySourceEvidenceId(Long sourceEvidenceId);

    @Query("""
            select ne
            from NormalizedEvidence ne
            where lower(coalesce(ne.title, '')) like lower(concat('%', :keyword, '%'))
               or lower(coalesce(ne.content, '')) like lower(concat('%', :keyword, '%'))
               or lower(coalesce(ne.metadataJson, '')) like lower(concat('%', :keyword, '%'))
            order by ne.eventTime desc, ne.createdAt desc
            """)
    List<NormalizedEvidence> searchByKeyword(@Param("keyword") String keyword);

    void deleteByDeviceId(Long deviceId);
}
