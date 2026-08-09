package com.forensics.platform.controller;

import com.forensics.platform.dto.InvestigationReportDto;
import com.forensics.platform.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reports")
@SecurityRequirement(name = "bearerAuth")
public class ReportController {

    private final ReportService reportService;

    public ReportController(
            ReportService reportService
    ) {
        this.reportService = reportService;
    }

    @Operation(
            summary = "Generate investigation report",
            description =
                    "Combines device information, evidence, timeline, relationship graph and AI analysis."
    )
    @GetMapping("/device/{deviceId}")
    @PreAuthorize("hasAnyRole('ADMIN','INVESTIGATOR','REVIEWER')")
    public InvestigationReportDto generateReport(
            @PathVariable Long deviceId
    ) throws Exception {

        return reportService.generateReport(deviceId);
    }
}