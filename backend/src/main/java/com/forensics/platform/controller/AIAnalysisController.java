package com.forensics.platform.controller;

import com.forensics.platform.dto.AIAnalysisResponse;
import com.forensics.platform.service.AIAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai")
@SecurityRequirement(name = "bearerAuth")
public class AIAnalysisController {

    private final AIAnalysisService aiAnalysisService;

    public AIAnalysisController(
            AIAnalysisService aiAnalysisService
    ) {
        this.aiAnalysisService = aiAnalysisService;
    }

    @Operation(
            summary = "Analyze device forensic evidence",
            description = "Runs AI/NLP analysis on evidence stored for the specified device."
    )
    @GetMapping("/device/{deviceId}/analyze")
    @PreAuthorize("hasAnyRole('ADMIN','INVESTIGATOR')")
    public AIAnalysisResponse analyzeDevice(
            @PathVariable Long deviceId
    ) throws Exception {

        return aiAnalysisService.analyzeDevice(deviceId);
    }
}