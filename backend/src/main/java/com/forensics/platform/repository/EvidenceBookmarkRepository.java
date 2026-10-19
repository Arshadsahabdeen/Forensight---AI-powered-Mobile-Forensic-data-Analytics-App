package com.forensics.platform.repository;

import com.forensics.platform.entity.EvidenceBookmark;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EvidenceBookmarkRepository extends JpaRepository<EvidenceBookmark, Long> {

    @EntityGraph(attributePaths = {"evidence", "evidence.device"})
    List<EvidenceBookmark> findByCaseIdOrderByCreatedAtDesc(Long caseId);

    Optional<EvidenceBookmark> findByCaseIdAndEvidenceId(Long caseId, Long evidenceId);
}
