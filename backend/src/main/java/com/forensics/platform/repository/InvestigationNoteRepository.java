package com.forensics.platform.repository;

import com.forensics.platform.entity.InvestigationNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InvestigationNoteRepository extends JpaRepository<InvestigationNote, Long> {
    List<InvestigationNote> findByCaseIdOrderByUpdatedAtDesc(Long caseId);
}
