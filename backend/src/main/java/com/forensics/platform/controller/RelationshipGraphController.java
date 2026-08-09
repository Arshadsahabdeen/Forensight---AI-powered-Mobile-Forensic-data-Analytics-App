package com.forensics.platform.controller;

import com.forensics.platform.dto.RelationshipGraphDto;
import com.forensics.platform.service.RelationshipGraphService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/relationship-graph")
@SecurityRequirement(name = "bearerAuth")
public class RelationshipGraphController {

    private final RelationshipGraphService graphService;

    public RelationshipGraphController(
            RelationshipGraphService graphService
    ) {
        this.graphService = graphService;
    }

    @GetMapping("/device/{deviceId}")
    @PreAuthorize("hasAnyRole('ADMIN','INVESTIGATOR','REVIEWER')")
    public RelationshipGraphDto getDeviceGraph(
            @PathVariable Long deviceId
    ) {
        return graphService.buildDeviceGraph(deviceId);
    }
}