package com.forensics.platform.repository;

import com.forensics.platform.entity.InvestigationCase;
import com.forensics.platform.entity.CasePriority;
import com.forensics.platform.entity.CaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.time.Instant;

public interface CaseRepository extends JpaRepository<InvestigationCase, Long>, JpaSpecificationExecutor<InvestigationCase> {
    long countByStatus(CaseStatus status);
    long countByPriority(CasePriority priority);
    long countByCreatedAtBetween(Instant start, Instant end);
    List<InvestigationCase> findTop10ByOrderByUpdatedAtDesc();

    @Query(value = """
            select coalesce(nullif(trim(lead_investigator), ''), 'Unknown') as label, count(*) as value
            from investigation_case
            group by coalesce(nullif(trim(lead_investigator), ''), 'Unknown')
            order by value desc, label asc
            limit 10
            """, nativeQuery = true)
    List<Object[]> countCasesByLeadInvestigator();

    boolean existsByCaseNumberIgnoreCase(String caseNumber);
    boolean existsByCaseNumberIgnoreCaseAndIdNot(String caseNumber, Long id);
}
