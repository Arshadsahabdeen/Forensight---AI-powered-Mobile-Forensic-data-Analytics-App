package com.forensics.platform.repository;

import com.forensics.platform.entity.Report;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReportRepository
        extends JpaRepository<Report, Long>,
        JpaSpecificationExecutor<Report> {

    List<Report> findByInvestigationCaseId(Long caseId);

    List<Report> findByDeviceIdOrderByGeneratedAtDesc(Long deviceId);

    Optional<Report> findTopByDeviceIdOrderByGeneratedAtDesc(Long deviceId);

    List<Report> findTop10ByOrderByGeneratedAtDesc();

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "investigationCase")
    List<Report> findTop200ByOrderByGeneratedAtDesc();

    long countByStatusIgnoreCase(String status);

    long countByGeneratedAtBetween(
            Instant start,
            Instant end
    );

    long countByGeneratedAtGreaterThanEqual(
            Instant start
    );

    long countByInvestigationCaseId(Long caseId);

    long countByDeviceId(Long deviceId);

    Page<Report> findAllByOrderByGeneratedAtDesc(
            Pageable pageable
    );

    void deleteByInvestigationCaseId(Long caseId);

    @Query(value = """
        select
            to_char(
                date_trunc('month', generated_at),
                'Mon'
            ) as label,
            count(*) as value
        from report
        where generated_at >= :since
        group by date_trunc('month', generated_at)
        order by date_trunc('month', generated_at)
        """,
        nativeQuery = true)
    List<Object[]> countReportsByMonthSince(
            Instant since
    );
}