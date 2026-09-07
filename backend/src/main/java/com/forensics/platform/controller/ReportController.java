package com.forensics.platform.controller;

import com.forensics.platform.entity.Report;
import com.forensics.platform.dto.InvestigationReportDto;
import com.forensics.platform.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    /*
     * Generate a new report from a device.
     */

    @Operation(
            summary = "Generate investigation report"
    )
    @GetMapping("/device/{deviceId}")
    @PreAuthorize(
            "hasAnyRole('ADMIN','INVESTIGATOR','REVIEWER')"
    )
    public InvestigationReportDto generateReport(
            @PathVariable Long deviceId
    ) throws Exception {

        return reportService.generateReport(
                deviceId
        );
    }

    /*
     * Get all permanently stored reports.
     */

    @Operation(
            summary = "Get stored investigation reports"
    )
    @GetMapping
    @PreAuthorize(
            "hasAnyRole('ADMIN','INVESTIGATOR','REVIEWER')"
    )
    public List<Report> getAllReports() {

        return reportService.getAllReports();
    }

    /*
     * Get reports generated for a device.
     */

    @GetMapping("/device/{deviceId}/stored")
    @PreAuthorize(
            "hasAnyRole('ADMIN','INVESTIGATOR','REVIEWER')"
    )
    public List<Report> getDeviceReports(
            @PathVariable Long deviceId
    ) {

        return reportService.getReportsByDevice(
                deviceId
        );
    }

    /*
     * Get one stored report.
     */

    @GetMapping("/{reportId}")
    @PreAuthorize(
            "hasAnyRole('ADMIN','INVESTIGATOR','REVIEWER')"
    )
    public Report getReport(
            @PathVariable Long reportId
    ) {

        return reportService.getReport(
                reportId
        );
    }

    /*
     * Delete stored report.
     */

    @DeleteMapping("/{reportId}")
    @PreAuthorize(
            "hasAnyRole('ADMIN','INVESTIGATOR')"
    )
    public ResponseEntity<Void> deleteReport(
            @PathVariable Long reportId
    ) {

        reportService.deleteReport(
                reportId
        );

        return ResponseEntity.noContent()
                .build();
    }
}