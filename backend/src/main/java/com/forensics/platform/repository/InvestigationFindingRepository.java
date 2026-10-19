package com.forensics.platform.repository;

import com.forensics.platform.entity.InvestigationFinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface InvestigationFindingRepository extends JpaRepository<InvestigationFinding, Long> {

    List<InvestigationFinding> findByCaseIdOrderByCreatedAtAsc(Long caseId);

    long countByReviewStatus(InvestigationFinding.ReviewStatus status);

    @Query("select count(f) from InvestigationFinding f where f.reviewStatus = :status and f.caseId in :caseIds")
    long countByReviewStatusAndCaseIdIn(InvestigationFinding.ReviewStatus status, Collection<Long> caseIds);
}
