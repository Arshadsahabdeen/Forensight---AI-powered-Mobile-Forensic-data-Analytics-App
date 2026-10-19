package com.forensics.platform.controller;

import com.forensics.platform.dto.IngestionDtos.CustodyEvent;
import com.forensics.platform.dto.IngestionDtos.EvidenceFileResponse;
import com.forensics.platform.dto.IngestionDtos.UploadResponse;
import com.forensics.platform.dto.IngestionDtos.VerificationResponse;
import com.forensics.platform.service.EvidenceFileService;
import com.forensics.platform.service.ProcessingJobRunner;
import com.forensics.platform.service.ProcessingJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@SecurityRequirement(name = "bearerAuth")
public class EvidenceFileController {

    private final EvidenceFileService fileService;
    private final ProcessingJobRunner jobRunner;
    private final ProcessingJobService jobService;

    public EvidenceFileController(EvidenceFileService fileService, ProcessingJobRunner jobRunner, ProcessingJobService jobService) {
        this.fileService = fileService;
        this.jobRunner = jobRunner;
        this.jobService = jobService;
    }

    @Operation(summary = "Upload an original evidence file (.xml, .ufdr, .zip) to a case and queue it for processing")
    @PostMapping(value = "/cases/{caseId}/evidence-files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','INVESTIGATOR')")
    public ResponseEntity<UploadResponse> upload(@PathVariable Long caseId,
                                                 @RequestParam(required = false) Long deviceId,
                                                 @RequestPart("file") MultipartFile file,
                                                 Authentication authentication) {
        return accepted(fileService.receive(caseId, deviceId, file, authentication), authentication);
    }

    /**
     * Submits the job only after {@link EvidenceFileService#receive} has committed, then returns
     * the current state (already final when processing runs synchronously).
     */
    ResponseEntity<UploadResponse> accepted(UploadResponse received, Authentication authentication) {
        jobRunner.submit(received.job().id());
        EvidenceFileResponse file = fileService.get(received.file().id(), authentication);
        var job = jobService.detail(received.job().id(), authentication).job();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new UploadResponse(file, job, received.message()));
    }

    @GetMapping("/cases/{caseId}/evidence-files")
    @PreAuthorize("hasAnyRole('ADMIN','INVESTIGATOR','REVIEWER')")
    public List<EvidenceFileResponse> list(@PathVariable Long caseId, Authentication authentication) {
        return fileService.listForCase(caseId, authentication);
    }

    @GetMapping("/evidence-files/{fileId}")
    @PreAuthorize("hasAnyRole('ADMIN','INVESTIGATOR','REVIEWER')")
    public EvidenceFileResponse get(@PathVariable Long fileId, Authentication authentication) {
        return fileService.get(fileId, authentication);
    }

    @Operation(summary = "Recompute the stored file's SHA-256 and compare it with the hash recorded at import")
    @PostMapping("/evidence-files/{fileId}/verify")
    @PreAuthorize("hasAnyRole('ADMIN','INVESTIGATOR','REVIEWER')")
    public VerificationResponse verify(@PathVariable Long fileId, Authentication authentication) {
        return fileService.verify(fileId, authentication);
    }

    @GetMapping("/evidence-files/{fileId}/custody")
    @PreAuthorize("hasAnyRole('ADMIN','INVESTIGATOR','REVIEWER')")
    public List<CustodyEvent> custody(@PathVariable Long fileId, Authentication authentication) {
        return fileService.custody(fileId, authentication);
    }

    @Operation(summary = "Download the preserved original evidence file (audited)")
    @GetMapping("/evidence-files/{fileId}/download")
    @PreAuthorize("hasAnyRole('ADMIN','INVESTIGATOR','REVIEWER')")
    public ResponseEntity<Resource> download(@PathVariable Long fileId, Authentication authentication) {
        EvidenceFileService.Download download = fileService.download(fileId, authentication);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.fileName(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.size())
                .body(new FileSystemResource(download.path()));
    }
}
