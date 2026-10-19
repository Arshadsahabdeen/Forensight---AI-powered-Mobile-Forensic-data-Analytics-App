package com.forensics.platform.controller;

import com.forensics.platform.dto.RelationshipGraphDto;
import com.forensics.platform.service.RelationshipGraphService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cases/{caseId}/correlations")
@SecurityRequirement(name = "bearerAuth")
public class RelationshipGraphController {

    private final RelationshipGraphService graphService;

    public RelationshipGraphController(RelationshipGraphService graphService) {
        this.graphService = graphService;
    }

    @Operation(summary = "Explainable relationships between identifiers, people, locations and devices in a case")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','INVESTIGATOR','REVIEWER')")
    public RelationshipGraphDto correlations(@PathVariable Long caseId,
                                             @RequestParam(required = false) Long deviceId,
                                             @RequestParam(defaultValue = "false") boolean includeEvents,
                                             @RequestParam(defaultValue = "15") int windowMinutes,
                                             @RequestParam(defaultValue = "400") int maxEntities,
                                             Authentication authentication) {
        return graphService.buildCaseGraph(caseId, deviceId, includeEvents, windowMinutes, maxEntities, authentication);
    }
}
