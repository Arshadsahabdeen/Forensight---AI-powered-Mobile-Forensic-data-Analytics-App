package com.forensics.platform.ufdr;

import com.forensics.platform.entity.EvidenceSourceFile;
import com.forensics.platform.entity.ProcessingJob;
import com.forensics.platform.repository.ProcessingJobRepository;
import com.forensics.platform.service.AuditService;
import com.forensics.platform.service.EvidenceImportService;
import com.forensics.platform.service.EvidenceStorageService;
import com.forensics.platform.service.ProcessingJobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Runs one evidence import job: verify the stored original, parse it, persist records.
 * Every outcome is written to the job log; error messages shown to users never contain evidence content.
 */
@Service
public class UFDRService {

    private static final Logger log = LoggerFactory.getLogger(UFDRService.class);

    private final ProcessingJobService jobService;
    private final ProcessingJobRepository jobRepository;
    private final EvidenceStorageService storageService;
    private final UFDRParser parser;
    private final EvidenceImportService importService;
    private final AuditService auditService;
    private final TransactionTemplate readOnly;

    public UFDRService(ProcessingJobService jobService, ProcessingJobRepository jobRepository, EvidenceStorageService storageService,
                       UFDRParser parser, EvidenceImportService importService, AuditService auditService,
                       PlatformTransactionManager transactionManager) {
        this.jobService = jobService;
        this.jobRepository = jobRepository;
        this.storageService = storageService;
        this.parser = parser;
        this.importService = importService;
        this.auditService = auditService;
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    private record JobContext(Long jobId, Long caseId, Long fileId, String storageKey, String sha256,
                              EvidenceSourceFile.SourceFormat format, String fileName, String actor) {}

    public void process(Long jobId) {
        JobContext context;
        try {
            jobService.start(jobId);
            context = readOnly.execute(status -> {
                ProcessingJob job = jobRepository.findWithDetailsById(jobId).orElseThrow();
                EvidenceSourceFile file = job.getEvidenceFile();
                return new JobContext(jobId, job.getInvestigationCase().getId(), file.getId(), file.getStorageKey(),
                        file.getSha256(), file.getSourceFormat(), file.getOriginalFileName(), job.getCreatedBy());
            });
        } catch (RuntimeException ex) {
            log.error("Could not start processing job {}", jobId, ex);
            safeFail(jobId, "The job could not be started: " + ex.getClass().getSimpleName());
            return;
        }

        try {
            jobService.progress(jobId, 10, "Verifying SHA-256 of the stored original file");
            if (!storageService.exists(context.storageKey())) {
                throw new EvidenceFormatException("The stored original file is missing. It cannot be processed.");
            }
            String actual = storageService.sha256(context.storageKey());
            if (!actual.equals(context.sha256())) {
                throw new EvidenceFormatException("The stored file's SHA-256 does not match the hash recorded at import. Processing was stopped.");
            }
            jobService.log(jobId, "INFO", "Integrity verified: SHA-256 " + actual);

            jobService.progress(jobId, 25, "Parsing " + context.format() + " content");
            Path path = storageService.resolve(context.storageKey());
            UFDRParser.ParsedSource parsed = parser.parse(path, context.format());
            for (String note : parsed.notes()) {
                jobService.log(jobId, "INFO", note);
            }
            for (String warning : parsed.extraction().warnings()) {
                jobService.log(jobId, "WARN", warning);
            }
            jobService.log(jobId, "INFO", "Found " + parsed.extraction().records().size() + " candidate record(s) under root <"
                    + parsed.extraction().rootElement() + ">.");

            jobService.progress(jobId, 60, "Saving records and normalizing evidence");
            EvidenceImportService.ImportOutcome outcome = importService.persist(context.fileId(), parsed.extraction(), context.actor());

            String summary = "Imported " + outcome.recordsCreated() + " record(s) to device '" + outcome.deviceName() + "'"
                    + (outcome.deviceCreated() ? " (device registered from file metadata)" : "")
                    + "; " + outcome.duplicatesSkipped() + " duplicate(s) skipped; "
                    + outcome.timestampIssues() + " record(s) with missing, assumed or unparseable timestamps.";
            jobService.succeed(jobId, summary);
            auditService.record(new AuditService.Entry(null, context.actor(), "EVIDENCE_IMPORTED", summary, true,
                    context.caseId(), "EVIDENCE_FILE", String.valueOf(context.fileId()), null));
            setJobDevice(jobId, outcome.deviceId());
        } catch (EvidenceFormatException ex) {
            safeFail(jobId, ex.getMessage());
        } catch (UFDRImportService.DeviceConflictException ex) {
            safeFail(jobId, ex.getMessage());
        } catch (UFDRParser.SizeLimitExceededException ex) {
            safeFail(jobId, "The XML content exceeds the maximum supported size.");
        } catch (IOException ex) {
            log.error("I/O error in processing job {}", jobId, ex);
            safeFail(jobId, "The stored file could not be read (" + ex.getClass().getSimpleName() + ").");
        } catch (RuntimeException ex) {
            log.error("Unexpected error in processing job {}", jobId, ex);
            safeFail(jobId, "Unexpected processing error (" + ex.getClass().getSimpleName() + "). No records were saved.");
        }
    }

    private void setJobDevice(Long jobId, Long deviceId) {
        new TransactionTemplate(readOnly.getTransactionManager()).executeWithoutResult(status ->
                jobRepository.findById(jobId).ifPresent(job -> {
                    if (job.getDevice() == null && job.getEvidenceFile() != null) {
                        job.setDevice(job.getEvidenceFile().getDevice());
                    }
                }));
    }

    private void safeFail(Long jobId, String message) {
        try {
            jobService.fail(jobId, message);
        } catch (RuntimeException ex) {
            log.error("Could not record failure for processing job {}", jobId, ex);
        }
    }
}
