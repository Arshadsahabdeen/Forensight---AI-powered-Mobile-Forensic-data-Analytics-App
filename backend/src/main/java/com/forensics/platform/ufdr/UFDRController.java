package com.forensics.platform.ufdr;

import com.forensics.platform.dto.IngestionDtos.UploadResponse;
import com.forensics.platform.service.EvidenceFileService;
import com.forensics.platform.service.ProcessingJobRunner;
import com.forensics.platform.service.ProcessingJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Backwards-compatible upload path. Requires authentication and a target case, and uses the same
 * validated, hashed, audited pipeline as {@code POST /api/v1/cases/{caseId}/evidence-files}.
 */
@RestController
@RequestMapping("/api/v1/ufdr")
@SecurityRequirement(name = "bearerAuth")
public class UFDRController {

    private final EvidenceFileService fileService;
    private final ProcessingJobRunner jobRunner;
    private final ProcessingJobService jobService;

    public UFDRController(EvidenceFileService fileService, ProcessingJobRunner jobRunner, ProcessingJobService jobService) {
        this.fileService = fileService;
        this.jobRunner = jobRunner;
        this.jobService = jobService;
    }

    @Operation(summary = "Upload an extraction file to a case (alias of the case evidence-file upload)")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','INVESTIGATOR')")
    public ResponseEntity<UploadResponse> upload(@RequestParam Long caseId,
                                                 @RequestParam(required = false) Long deviceId,
                                                 @RequestPart("file") MultipartFile file,
                                                 Authentication authentication) {
        UploadResponse received = fileService.receive(caseId, deviceId, file, authentication);
        jobRunner.submit(received.job().id());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new UploadResponse(
                fileService.get(received.file().id(), authentication),
                jobService.detail(received.job().id(), authentication).job(),
                received.message()));
    }
}
