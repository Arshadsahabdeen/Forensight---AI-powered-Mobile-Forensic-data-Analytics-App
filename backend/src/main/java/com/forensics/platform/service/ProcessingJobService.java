package com.forensics.platform.service;

import com.forensics.platform.dto.IngestionDtos.JobLogEntry;
import com.forensics.platform.dto.IngestionDtos.ProcessingJobDetail;
import com.forensics.platform.dto.IngestionDtos.ProcessingJobPage;
import com.forensics.platform.dto.IngestionDtos.ProcessingJobResponse;
import com.forensics.platform.entity.EvidenceSourceFile;
import com.forensics.platform.entity.ProcessingJob;
import com.forensics.platform.entity.ProcessingJobLog;
import com.forensics.platform.repository.EvidenceSourceFileRepository;
import com.forensics.platform.repository.ProcessingJobLogRepository;
import com.forensics.platform.repository.ProcessingJobRepository;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class ProcessingJobService {

    private static final Logger log = LoggerFactory.getLogger(ProcessingJobService.class);
    private static final Set<ProcessingJob.Status> ACTIVE = Set.of(ProcessingJob.Status.QUEUED, ProcessingJob.Status.RUNNING);

    private final ProcessingJobRepository jobRepository;
    private final ProcessingJobLogRepository logRepository;
    private final EvidenceSourceFileRepository fileRepository;
    private final CaseAccessService caseAccessService;
    private final AuditService auditService;

    public ProcessingJobService(ProcessingJobRepository jobRepository, ProcessingJobLogRepository logRepository,
                                EvidenceSourceFileRepository fileRepository, CaseAccessService caseAccessService,
                                AuditService auditService) {
        this.jobRepository = jobRepository;
        this.logRepository = logRepository;
        this.fileRepository = fileRepository;
        this.caseAccessService = caseAccessService;
        this.auditService = auditService;
    }

    // ---- lifecycle (called by the processor; each step commits immediately) ----

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void start(Long jobId) {
        ProcessingJob job = require(jobId);
        if (job.getStatus() != ProcessingJob.Status.QUEUED) {
            throw new IllegalStateException("Job " + jobId + " is not queued");
        }
        job.setStatus(ProcessingJob.Status.RUNNING);
        job.setAttempts(job.getAttempts() + 1);
        job.setStartedAt(Instant.now());
        job.setFinishedAt(null);
        job.setErrorMessage(null);
        job.setProgress(5);
        job.setStage("Started");
        if (job.getEvidenceFile() != null) {
            job.getEvidenceFile().setProcessingStatus(EvidenceSourceFile.ProcessingStatus.PROCESSING);
        }
        logRepository.save(new ProcessingJobLog(jobId, "INFO", "Attempt " + job.getAttempts() + " started."));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void progress(Long jobId, int progress, String stage) {
        ProcessingJob job = require(jobId);
        job.setProgress(Math.max(0, Math.min(100, progress)));
        job.setStage(stage);
        logRepository.save(new ProcessingJobLog(jobId, "INFO", stage));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(Long jobId, String level, String message) {
        logRepository.save(new ProcessingJobLog(jobId, level, message));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void succeed(Long jobId, String summary) {
        ProcessingJob job = require(jobId);
        job.setStatus(ProcessingJob.Status.SUCCEEDED);
        job.setProgress(100);
        job.setStage("Completed");
        job.setFinishedAt(Instant.now());
        logRepository.save(new ProcessingJobLog(jobId, "INFO", summary));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long jobId, String safeMessage) {
        ProcessingJob job = require(jobId);
        job.setStatus(ProcessingJob.Status.FAILED);
        job.setStage("Failed");
        job.setErrorMessage(safeMessage);
        job.setFinishedAt(Instant.now());
        if (job.getEvidenceFile() != null) {
            job.getEvidenceFile().setProcessingStatus(EvidenceSourceFile.ProcessingStatus.FAILED);
        }
        logRepository.save(new ProcessingJobLog(jobId, "ERROR", safeMessage));
        auditService.record(new AuditService.Entry(null, job.getCreatedBy(), "EVIDENCE_PROCESSING_FAILED",
                "Processing job #" + jobId + " failed: " + safeMessage, false,
                job.getInvestigationCase().getId(), "PROCESSING_JOB", String.valueOf(jobId), null));
    }

    /**
     * Jobs that were queued or running when the server stopped cannot resume mid-way.
     * They are marked failed so an investigator can retry them from the preserved original file.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recoverInterruptedJobs() {
        List<ProcessingJob> interrupted = jobRepository.findByStatusIn(ACTIVE);
        for (ProcessingJob job : interrupted) {
            job.setStatus(ProcessingJob.Status.FAILED);
            job.setStage("Interrupted");
            job.setErrorMessage("Interrupted by a server restart before completion. Retry to reprocess the stored original file.");
            job.setFinishedAt(Instant.now());
            if (job.getEvidenceFile() != null && job.getEvidenceFile().getProcessingStatus() != EvidenceSourceFile.ProcessingStatus.PROCESSED) {
                job.getEvidenceFile().setProcessingStatus(EvidenceSourceFile.ProcessingStatus.FAILED);
            }
            logRepository.save(new ProcessingJobLog(job.getId(), "WARN", job.getErrorMessage()));
        }
        if (!interrupted.isEmpty()) {
            log.warn("Marked {} interrupted processing job(s) as failed", interrupted.size());
        }
    }

    // ---- queries and user actions ----

    @Transactional(readOnly = true)
    public ProcessingJobPage list(Long caseId, String status, int page, int size, Authentication authentication) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be >= 0 and size between 1 and 100");
        }
        ProcessingJob.Status statusFilter = parseStatus(status);
        if (caseId != null) {
            caseAccessService.requireViewable(caseId, authentication);
        }
        Specification<ProcessingJob> base = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(caseAccessService.restrictToViewableCases(root.get("investigationCase"), cb, authentication));
            if (caseId != null) {
                predicates.add(cb.equal(root.get("investigationCase").get("id"), caseId));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Specification<ProcessingJob> filtered = statusFilter == null ? base : base.and((root, query, cb) -> cb.equal(root.get("status"), statusFilter));
        Page<ProcessingJob> result = jobRepository.findAll(filtered, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return new ProcessingJobPage(
                result.getContent().stream().map(this::toResponse).toList(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.getNumber(),
                result.getSize(),
                jobRepository.count(base.and(statusIs(ProcessingJob.Status.QUEUED))),
                jobRepository.count(base.and(statusIs(ProcessingJob.Status.RUNNING))),
                jobRepository.count(base.and(statusIs(ProcessingJob.Status.FAILED)))
        );
    }

    @Transactional(readOnly = true)
    public ProcessingJobDetail detail(Long jobId, Authentication authentication) {
        ProcessingJob job = jobRepository.findWithDetailsById(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Processing job not found"));
        caseAccessService.assertCanView(job.getInvestigationCase(), authentication);
        List<JobLogEntry> logs = logRepository.findByJobIdOrderByIdAsc(jobId).stream()
                .map(entry -> new JobLogEntry(entry.getId(), entry.getLoggedAt(), entry.getLevel(), entry.getMessage()))
                .toList();
        return new ProcessingJobDetail(toResponse(job), logs);
    }

    /**
     * Re-queues a failed job. Imports are all-or-nothing, so a failed attempt left no partial records.
     */
    @Transactional
    public ProcessingJobResponse retry(Long jobId, Authentication authentication) {
        ProcessingJob job = jobRepository.findWithDetailsById(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Processing job not found"));
        caseAccessService.assertCanEdit(job.getInvestigationCase(), authentication);
        if (job.getStatus() != ProcessingJob.Status.FAILED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only failed jobs can be retried");
        }
        if (job.getEvidenceFile() != null && job.getEvidenceFile().getProcessingStatus() == EvidenceSourceFile.ProcessingStatus.PROCESSED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This evidence file has already been processed successfully");
        }
        job.setStatus(ProcessingJob.Status.QUEUED);
        job.setProgress(0);
        job.setStage("Queued for retry");
        job.setErrorMessage(null);
        job.setFinishedAt(null);
        if (job.getEvidenceFile() != null) {
            job.getEvidenceFile().setProcessingStatus(EvidenceSourceFile.ProcessingStatus.RECEIVED);
        }
        logRepository.save(new ProcessingJobLog(jobId, "INFO", "Retry requested by " + authentication.getName() + "."));
        auditService.record(new AuditService.Entry(null, authentication.getName(), "PROCESSING_JOB_RETRIED",
                "Retried processing job #" + jobId, true, job.getInvestigationCase().getId(), "PROCESSING_JOB", String.valueOf(jobId), null));
        return toResponse(job);
    }

    @Transactional(readOnly = true)
    public ProcessingJobResponse latestForFile(Long fileId) {
        return jobRepository.findByEvidenceFileIdOrderByCreatedAtDesc(fileId).stream().findFirst().map(this::toResponse).orElse(null);
    }

    public boolean hasActiveJob(Long fileId) {
        return jobRepository.existsByEvidenceFileIdAndStatusIn(fileId, ACTIVE);
    }

    public ProcessingJobResponse toResponse(ProcessingJob job) {
        var investigationCase = job.getInvestigationCase();
        var device = job.getDevice();
        var file = job.getEvidenceFile();
        boolean canRetry = job.getStatus() == ProcessingJob.Status.FAILED
                && (file == null || file.getProcessingStatus() != EvidenceSourceFile.ProcessingStatus.PROCESSED);
        return new ProcessingJobResponse(
                job.getId(),
                job.getJobType(),
                job.getStatus(),
                job.getProgress(),
                job.getStage(),
                job.getAttempts(),
                job.getErrorMessage(),
                investigationCase.getId(),
                investigationCase.getCaseNumber(),
                device == null ? null : device.getId(),
                device == null ? null : device.getDeviceName(),
                file == null ? null : file.getId(),
                file == null ? null : file.getOriginalFileName(),
                job.getCreatedBy(),
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getFinishedAt(),
                canRetry
        );
    }

    private Specification<ProcessingJob> statusIs(ProcessingJob.Status status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    private ProcessingJob.Status parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return ProcessingJob.Status.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid job status filter");
        }
    }

    private ProcessingJob require(Long jobId) {
        return jobRepository.findById(jobId).orElseThrow(() -> new IllegalStateException("Processing job " + jobId + " not found"));
    }
}
