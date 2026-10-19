package com.forensics.platform.controller;

import com.forensics.platform.dto.AIAnalysisResponse;
import com.forensics.platform.service.AIAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@SecurityRequirement(name = "bearerAuth")
public class AIAnalysisController {

    private final AIAnalysisService aiAnalysisService;

    public AIAnalysisController(AIAnalysisService aiAnalysisService) {
        this.aiAnalysisService = aiAnalysisService;
    }

    @Operation(summary = "Evidence-grounded search, summary, entity extraction or question answering for a case")
    @PostMapping("/cases/{caseId}/assistant")
    @PreAuthorize("hasAnyRole('ADMIN','INVESTIGATOR','REVIEWER')")
    public AIAnalysisResponse assist(@PathVariable Long caseId, @Valid @RequestBody AIAnalysisResponse.Request request,
                                     Authentication authentication) {
        return aiAnalysisService.assist(caseId, request, authentication);
    }

    @Operation(summary = "AI service availability and configuration (no secrets are returned)")
    @GetMapping("/assistant/status")
    @PreAuthorize("hasAnyRole('ADMIN','INVESTIGATOR','REVIEWER')")
    public AIAnalysisResponse.Status status() {
        return aiAnalysisService.status();
    }
}
