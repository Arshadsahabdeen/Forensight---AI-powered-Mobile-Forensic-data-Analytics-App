package com.forensics.platform.repository;

import com.forensics.platform.entity.Evidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface EvidenceRepository
        extends JpaRepository<Evidence, Long> {

    @EntityGraph(attributePaths = "device")
    List<Evidence> findByDeviceInvestigationCaseId(Long caseId);

    long countByDeviceId(Long deviceId);

    List<Evidence> findByDeviceId(Long deviceId);

    List<Evidence> findByArtifactTypeIgnoreCase(String artifactType);

    List<Evidence> findByEventTimeBetween(
            Instant start,
            Instant end
    );

    List<Evidence> findByDeviceIdOrderByEventTimeAsc(
            Long deviceId
    );

    List<Evidence> findByDeviceInvestigationCaseIdOrderByEventTimeAsc(
            Long caseId
    );

    List<Evidence> findByCreatedAtGreaterThanEqual(
            Instant since
    );

    long countByCreatedAtBetween(
            Instant start,
            Instant end
    );

    long countByHashSha256IsNotNullAndHashSha256Not(
            String hashSha256
    );

    @Query(value = """
    SELECT EXISTS (
        SELECT 1
        FROM evidence e
        WHERE e.device_id = :deviceId
          AND e.artifact_type = :artifactType

          AND COALESCE(e.title, '') =
              COALESCE(CAST(:title AS TEXT), '')

          AND COALESCE(e.content, '') =
              COALESCE(CAST(:content AS TEXT), '')

          AND COALESCE(e.source, '') =
              COALESCE(CAST(:source AS TEXT), '')

          AND COALESCE(e.target, '') =
              COALESCE(CAST(:target AS TEXT), '')

          AND COALESCE(e.phone_number, '') =
              COALESCE(CAST(:phoneNumber AS TEXT), '')

          AND COALESCE(e.contact_name, '') =
              COALESCE(CAST(:contactName AS TEXT), '')

          AND COALESCE(e.location, '') =
              COALESCE(CAST(:location AS TEXT), '')

          AND (
              e.event_time = CAST(:eventTime AS TIMESTAMP WITH TIME ZONE)
              OR (
                  e.event_time IS NULL
                  AND CAST(:eventTime AS TIMESTAMP WITH TIME ZONE) IS NULL
              )
          )
    )
    """,
    nativeQuery = true)
boolean existsDuplicate(
        @Param("deviceId") Long deviceId,
        @Param("artifactType") String artifactType,
        @Param("title") String title,
        @Param("content") String content,
        @Param("source") String source,
        @Param("target") String target,
        @Param("phoneNumber") String phoneNumber,
        @Param("contactName") String contactName,
        @Param("location") String location,
        @Param("eventTime") Instant eventTime
);

    long countByArtifactTypeIgnoreCase(
            String artifactType
    );

    @Query("""
        select e.artifactType, count(e)
        from Evidence e
        group by e.artifactType
        order by count(e) desc
    """)
    List<Object[]> countByArtifactType();

    @Query(value = """
        select coalesce(
            nullif(trim(artifact_type), ''),
            'Unknown'
        ) as label,
        count(*) as value
        from evidence
        group by coalesce(
            nullif(trim(artifact_type), ''),
            'Unknown'
        )
        order by value desc, label asc
        """,
        nativeQuery = true)
    List<Object[]> countEvidenceByArtifactType();

    @Query(value = """
        select to_char(
            date_trunc('day', created_at),
            'Mon FMDD'
        ) as label,
        count(*) as value
        from evidence
        where created_at >= :since
        group by date_trunc('day', created_at)
        order by date_trunc('day', created_at)
        """,
        nativeQuery = true)
    List<Object[]> countEvidenceByDaySince(
            @Param("since") Instant since
    );

    void deleteByDeviceInvestigationCaseId(
            Long caseId
    );
}