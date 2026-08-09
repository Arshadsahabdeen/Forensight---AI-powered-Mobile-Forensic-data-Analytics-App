package com.forensics.platform.repository;

import com.forensics.platform.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findTop5ByOrderByTimestampDesc();
    List<AuditLog> findTop100ByOrderByTimestampDesc();
    List<AuditLog> findTop100ByDescriptionContainingIgnoreCaseOrderByTimestampDesc(String text);
    List<AuditLog> findByTimestampGreaterThanEqual(Instant timestamp);
    long countByTimestampBetween(Instant start, Instant end);

    @Query(value = """
            select to_char(date_trunc('month', timestamp), 'Mon') as label, count(*) as value
            from audit_log
            where timestamp >= :since
            group by date_trunc('month', timestamp)
            order by date_trunc('month', timestamp)
            """, nativeQuery = true)
    List<Object[]> countAuditLogByMonthSince(Instant since);
}
