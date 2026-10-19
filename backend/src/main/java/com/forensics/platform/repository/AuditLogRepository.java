package com.forensics.platform.repository;

import com.forensics.platform.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {
    List<AuditLog> findTop5ByOrderByTimestampDesc();
    List<AuditLog> findTop100ByOrderByTimestampDesc();
    List<AuditLog> findTop100ByDescriptionContainingIgnoreCaseOrderByTimestampDesc(String text);
    List<AuditLog> findByTimestampGreaterThanEqual(Instant timestamp);
    long countByTimestampBetween(Instant start, Instant end);

    Optional<AuditLog> findTopByEntryHashIsNotNullOrderByIdDesc();

    List<AuditLog> findByIdGreaterThanOrderByIdAsc(Long id, Pageable pageable);

    List<AuditLog> findTop200ByCaseIdOrderByIdDesc(Long caseId);

    List<AuditLog> findByEntityTypeAndEntityIdOrderByIdAsc(String entityType, String entityId);

    Page<AuditLog> findByCaseIdInOrderByIdDesc(Collection<Long> caseIds, Pageable pageable);

    @Query("select distinct a.action from AuditLog a where a.action is not null order by a.action")
    List<String> findDistinctActions();
}
