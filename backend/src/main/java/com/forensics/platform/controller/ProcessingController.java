package com.forensics.platform.controller;

import com.forensics.platform.dto.IngestionDtos.ProcessingJobDetail;
import com.forensics.platform.dto.IngestionDtos.ProcessingJobPage;
import com.forensics.platform.dto.IngestionDtos.ProcessingJobResponse;
import com.forensics.platform.service.ProcessingJobRunner;
import com.forensics.platform.service.ProcessingJobService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/processing/jobs")
@SecurityRequirement(name = "bearerAuth")
public class ProcessingController {

    private final ProcessingJobService jobService;
    private final ProcessingJobRunner jobRunner;

    public ProcessingController(ProcessingJobService jobService, ProcessingJobRunner jobRunner) {
        this.jobService = jobService;
        this.jobRunner = jobRunner;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','INVESTIGATOR','REVIEWER')")
    public ProcessingJobPage list(@RequestParam(required = false) Long caseId,
                                  @RequestParam(required = false) String status,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size,
                                  Authentication authentication) {
        return jobService.list(caseId, status, page, size, authentication);
    }

    @GetMapping("/{jobId}")
    @PreAuthorize("hasAnyRole('ADMIN','INVESTIGATOR','REVIEWER')")
    public ProcessingJobDetail detail(@PathVariable Long jobId, Authentication authentication) {
        return jobService.detail(jobId, authentication);
    }

    @PostMapping("/{jobId}/retry")
    @PreAuthorize("hasAnyRole('ADMIN','INVESTIGATOR')")
    public ProcessingJobResponse retry(@PathVariable Long jobId, Authentication authentication) {
        jobService.retry(jobId, authentication);
        jobRunner.submit(jobId);
        return jobService.detail(jobId, authentication).job();
    }
}
