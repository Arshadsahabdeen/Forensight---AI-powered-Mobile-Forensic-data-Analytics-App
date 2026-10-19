package com.forensics.platform.service;

import com.forensics.platform.dto.IngestionDtos.CustodyEvent;
import com.forensics.platform.dto.IngestionDtos.EvidenceFileResponse;
import com.forensics.platform.dto.IngestionDtos.UploadResponse;
import com.forensics.platform.dto.IngestionDtos.VerificationResponse;
import com.forensics.platform.entity.Device;
import com.forensics.platform.entity.EvidenceSourceFile;
import com.forensics.platform.entity.InvestigationCase;
import com.forensics.platform.entity.ProcessingJob;
import com.forensics.platform.entity.ProcessingJobLog;
import com.forensics.platform.repository.AuditLogRepository;
import com.forensics.platform.repository.ProcessingJobLogRepository;
import com.forensics.platform.repository.DeviceRepository;
import com.forensics.platform.repository.EvidenceSourceFileRepository;
import com.forensics.platform.repository.ProcessingJobRepository;
import com.forensics.platform.ufdr.EvidenceFormatException;
import com.forensics.platform.ufdr.UFDRParser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class EvidenceFileService {

    private static final String ENTITY = "EVIDENCE_FILE";

    private final EvidenceSourceFileRepository fileRepository;
    private final ProcessingJobRepository jobRepository;
    private final DeviceRepository deviceRepository;
    private final AuditLogRepository auditLogRepository;
    private final EvidenceStorageService storageService;
    private final ProcessingJobService jobService;
    private final CaseAccessService caseAccessService;
    private final AuditService auditService;
    private final ProcessingJobLogRepository jobLogRepository;

    public EvidenceFileService(EvidenceSourceFileRepository fileRepository, ProcessingJobRepository jobRepository,
                               DeviceRepository deviceRepository, AuditLogRepository auditLogRepository,
                               EvidenceStorageService storageService, ProcessingJobService jobService,
                               CaseAccessService caseAccessService, AuditService auditService,
                               ProcessingJobLogRepository jobLogRepository) {
        this.fileRepository = fileRepository;
        this.jobLogRepository = jobLogRepository;
        this.jobRepository = jobRepository;
        this.deviceRepository = deviceRepository;
        this.auditLogRepository = auditLogRepository;
        this.storageService = storageService;
        this.jobService = jobService;
        this.caseAccessService = caseAccessService;
        this.auditService = auditService;
    }

    /**
     * Validates and stores an uploaded original file, then creates a queued processing job.
     * The caller submits the job after this transaction commits.
     */
    @Transactional
    public UploadResponse receive(Long caseId, Long deviceId, MultipartFile upload, Authentication authentication) {
        InvestigationCase investigationCase = caseAccessService.requireEditable(caseId, authentication);
        if (upload == null || upload.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select a non-empty evidence file to upload");
        }
        if (upload.getSize() > storageService.maxBytes()) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "The uploaded file exceeds the maximum allowed size");
        }
        String originalName = sanitizeFileName(upload.getOriginalFilename());

        Device device = null;
        if (deviceId != null) {
            device = deviceRepository.findById(deviceId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Device not found"));
            if (device.getInvestigationCase() == null || !Objects.equals(device.getInvestigationCase().getId(), caseId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The selected device does not belong to this case");
            }
        }

        EvidenceSourceFile.SourceFormat format;
        EvidenceStorageService.StoredFile stored;
        try (InputStream raw = upload.getInputStream(); BufferedInputStream input = new BufferedInputStream(raw)) {
            input.mark(16);
            byte[] header = input.readNBytes(8);
            input.reset();
            format = UFDRParser.detectFormat(originalName, header);
            stored = storageService.store(caseId, input, format == EvidenceSourceFile.SourceFormat.XML ? "xml" : format.name().toLowerCase(Locale.ROOT));
        } catch (EvidenceFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "The upload could not be stored");
        }

        var duplicate = fileRepository.findByInvestigationCaseIdAndSha256(caseId, stored.sha256());
        if (duplicate.isPresent()) {
            EvidenceSourceFile existing = duplicate.get();
            auditService.record(new AuditService.Entry(null, authentication.getName(), "EVIDENCE_FILE_DUPLICATE_REJECTED",
                    "Rejected re-upload of '" + originalName + "' (SHA-256 " + stored.sha256() + ") matching file #" + existing.getId(),
                    false, caseId, ENTITY, String.valueOf(existing.getId()), null));
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This exact file (SHA-256 " + stored.sha256()
                    + ") was already imported into this case as file #" + existing.getId() + " on " + existing.getImportedAt() + ".");
        }

        EvidenceSourceFile file = new EvidenceSourceFile();
        file.setInvestigationCase(investigationCase);
        file.setDevice(device);
        file.setOriginalFileName(originalName);
        file.setStorageKey(stored.storageKey());
        file.setContentType(truncate(upload.getContentType(), 120));
        file.setSourceFormat(format);
        file.setSizeBytes(stored.sizeBytes());
        file.setSha256(stored.sha256());
        file.setImportedBy(authentication.getName());
        file.setImportedAt(Instant.now());
        file = fileRepository.save(file);

        ProcessingJob job = new ProcessingJob();
        job.setInvestigationCase(investigationCase);
        job.setDevice(device);
        job.setEvidenceFile(file);
        job.setCreatedBy(authentication.getName());
        job.setStage("Queued");
        job = jobRepository.save(job);
        // Same transaction as the job row (the job is not visible to other transactions yet).
        jobLogRepository.save(new ProcessingJobLog(job.getId(), "INFO",
                "Received '" + originalName + "' (" + stored.sizeBytes() + " bytes, SHA-256 " + stored.sha256() + ")."));

        auditService.record(new AuditService.Entry(null, authentication.getName(), "EVIDENCE_FILE_RECEIVED",
                "Received evidence file '" + originalName + "' (" + format + ", " + stored.sizeBytes() + " bytes, SHA-256 " + stored.sha256() + ")",
                true, caseId, ENTITY, String.valueOf(file.getId()), null));

        return new UploadResponse(toResponse(file), jobService.toResponse(job), "File stored and queued for processing.");
    }

    @Transactional(readOnly = true)
    public List<EvidenceFileResponse> listForCase(Long caseId, Authentication authentication) {
        caseAccessService.requireViewable(caseId, authentication);
        return fileRepository.findByInvestigationCaseIdOrderByImportedAtDesc(caseId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public EvidenceFileResponse get(Long fileId, Authentication authentication) {
        return toResponse(requireViewable(fileId, authentication));
    }

    /**
     * Recomputes the stored file's SHA-256 and compares it with the hash recorded at import.
     */
    @Transactional
    public VerificationResponse verify(Long fileId, Authentication authentication) {
        EvidenceSourceFile file = requireViewable(fileId, authentication);
        EvidenceSourceFile.IntegrityStatus status;
        String computed = null;
        String message;
        if (!storageService.exists(file.getStorageKey())) {
            status = EvidenceSourceFile.IntegrityStatus.MISSING;
            message = "The stored original file could not be found.";
        } else {
            try {
                computed = storageService.sha256(file.getStorageKey());
            } catch (IOException ex) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "The stored file could not be read for verification");
            }
            if (computed.equals(file.getSha256())) {
                status = EvidenceSourceFile.IntegrityStatus.VERIFIED;
                message = "SHA-256 matches the value recorded at import.";
            } else {
                status = EvidenceSourceFile.IntegrityStatus.MISMATCH;
                message = "SHA-256 does NOT match the value recorded at import.";
            }
        }
        Instant now = Instant.now();
        file.setIntegrityStatus(status);
        file.setLastVerifiedAt(now);
        file.setLastVerifiedBy(authentication.getName());
        auditService.record(new AuditService.Entry(null, authentication.getName(), "EVIDENCE_FILE_VERIFIED",
                "Hash verification of file #" + fileId + ": " + status + (computed == null ? "" : " (computed " + computed + ")"),
                status == EvidenceSourceFile.IntegrityStatus.VERIFIED, file.getInvestigationCase().getId(), ENTITY, String.valueOf(fileId), null));
        return new VerificationResponse(fileId, status, file.getSha256(), computed, now, authentication.getName(), message);
    }

    public record Download(Path path, String fileName, String contentType, long size) {}

    @Transactional(readOnly = true)
    public Download download(Long fileId, Authentication authentication) {
        EvidenceSourceFile file = requireViewable(fileId, authentication);
        if (!storageService.exists(file.getStorageKey())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "The stored original file could not be found");
        }
        auditService.record(new AuditService.Entry(null, authentication.getName(), "EVIDENCE_FILE_DOWNLOADED",
                "Downloaded original evidence file #" + fileId + " (SHA-256 " + file.getSha256() + ")",
                true, file.getInvestigationCase().getId(), ENTITY, String.valueOf(fileId), null));
        String contentType = file.getSourceFormat() == EvidenceSourceFile.SourceFormat.XML ? "application/xml" : "application/zip";
        return new Download(storageService.resolve(file.getStorageKey()), file.getOriginalFileName(), contentType, file.getSizeBytes());
    }

    @Transactional(readOnly = true)
    public List<CustodyEvent> custody(Long fileId, Authentication authentication) {
        requireViewable(fileId, authentication);
        return auditLogRepository.findByEntityTypeAndEntityIdOrderByIdAsc(ENTITY, String.valueOf(fileId)).stream()
                .map(entry -> new CustodyEvent(entry.getId(), entry.getTimestamp(), entry.getUsername(), entry.getAction(), entry.getDescription(), entry.isSuccess()))
                .toList();
    }

    public EvidenceFileResponse toResponse(EvidenceSourceFile file) {
        var investigationCase = file.getInvestigationCase();
        var device = file.getDevice();
        return new EvidenceFileResponse(
                file.getId(),
                investigationCase.getId(),
                investigationCase.getCaseNumber(),
                device == null ? null : device.getId(),
                device == null ? null : device.getDeviceName(),
                file.getOriginalFileName(),
                file.getContentType(),
                file.getSourceFormat(),
                file.getSizeBytes(),
                file.getSha256(),
                file.getImportedBy(),
                file.getImportedAt(),
                file.getProcessingStatus(),
                file.getIntegrityStatus(),
                file.getLastVerifiedAt(),
                file.getLastVerifiedBy(),
                file.getRecordsExtracted(),
                file.getDuplicatesSkipped(),
                file.getTimestampIssues(),
                file.getId() == null ? null : jobService.latestForFile(file.getId())
        );
    }

    private EvidenceSourceFile requireViewable(Long fileId, Authentication authentication) {
        EvidenceSourceFile file = fileRepository.findWithCaseById(fileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evidence file not found"));
        caseAccessService.assertCanView(file.getInvestigationCase(), authentication);
        return file;
    }

    /**
     * Display name only: strips directories and control characters. Never used in storage paths.
     */
    static String sanitizeFileName(String original) {
        if (original == null || original.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded file has no name");
        }
        String name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = name.replaceAll("[\\p{Cntrl}]", "").trim();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded file name is not valid");
        }
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
