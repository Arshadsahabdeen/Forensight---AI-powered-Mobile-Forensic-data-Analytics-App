package com.forensics.platform.controller;

import com.forensics.platform.dto.ReportDtos.ReportDetail;
import com.forensics.platform.dto.ReportDtos.ReportRequest;
import com.forensics.platform.dto.ReportDtos.ReportSummary;
import com.forensics.platform.dto.ReportDtos.ReviewRequest;
import com.forensics.platform.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@SecurityRequirement(name = "bearerAuth")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @Operation(summary = "Generate a case report from current stored data (snapshot is stored with its SHA-256)")
    @PostMapping("/cases/{caseId}/reports")
    @PreAuthorize("hasAnyRole('ADMIN','INVESTIGATOR')")
    public ReportSummary generate(@PathVariable Long caseId, @Valid @RequestBody(required = false) ReportRequest request,
                                  Authentication authentication) {
        return reportService.generate(caseId, request, authentication);
    }

    @GetMapping("/reports")
    @PreAuthorize("hasAnyRole('ADMIN','INVESTIGATOR','REVIEWER')")
    public List<ReportSummary> list(@RequestParam(required = false) Long caseId, Authentication authentication) {
        return reportService.list(caseId, authentication);
    }

    @GetMapping("/reports/{reportId}")
    @PreAuthorize("hasAnyRole('ADMIN','INVESTIGATOR','REVIEWER')")
    public ReportDetail detail(@PathVariable Long reportId, Authentication authentication) {
        return reportService.detail(reportId, authentication);
    }

    @Operation(summary = "Export a report as PDF, rendered from its stored snapshot")
    @GetMapping("/reports/{reportId}/pdf")
    @PreAuthorize("hasAnyRole('ADMIN','INVESTIGATOR','REVIEWER')")
    public ResponseEntity<byte[]> pdf(@PathVariable Long reportId, Authentication authentication) {
        ReportDetail detail = reportService.detail(reportId, authentication);
        byte[] pdf = reportService.pdf(reportId, authentication);
        String name = (detail.summary().reportNumber() == null ? "report-" + reportId : detail.summary().reportNumber())
                .replaceAll("[^A-Za-z0-9._-]", "_") + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @PostMapping("/reports/{reportId}/review")
    @PreAuthorize("hasAnyRole('ADMIN','REVIEWER')")
    public ReportSummary review(@PathVariable Long reportId, @Valid @RequestBody(required = false) ReviewRequest request,
                                Authentication authentication) {
        return reportService.review(reportId, request, authentication);
    }

    @DeleteMapping("/reports/{reportId}")
    @PreAuthorize("hasAnyRole('ADMIN','INVESTIGATOR')")
    public ResponseEntity<Void> delete(@PathVariable Long reportId, Authentication authentication) {
        reportService.delete(reportId, authentication);
        return ResponseEntity.noContent().build();
    }
}
