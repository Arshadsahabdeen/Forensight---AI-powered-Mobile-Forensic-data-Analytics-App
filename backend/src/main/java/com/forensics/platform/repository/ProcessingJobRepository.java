package com.forensics.platform.repository;

import com.forensics.platform.entity.ProcessingJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProcessingJobRepository extends JpaRepository<ProcessingJob, Long>, JpaSpecificationExecutor<ProcessingJob> {

    @Override
    @EntityGraph(attributePaths = {"investigationCase", "device", "evidenceFile"})
    Page<ProcessingJob> findAll(Specification<ProcessingJob> specification, Pageable pageable);

    @EntityGraph(attributePaths = {"investigationCase", "device", "evidenceFile"})
    Optional<ProcessingJob> findWithDetailsById(Long id);

    List<ProcessingJob> findByStatusIn(Collection<ProcessingJob.Status> statuses);

    boolean existsByEvidenceFileIdAndStatusIn(Long evidenceFileId, Collection<ProcessingJob.Status> statuses);

    @EntityGraph(attributePaths = {"investigationCase", "device", "evidenceFile"})
    List<ProcessingJob> findByEvidenceFileIdOrderByCreatedAtDesc(Long evidenceFileId);
}
