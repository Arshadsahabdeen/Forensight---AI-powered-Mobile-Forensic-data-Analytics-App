package com.forensics.platform.controller;

import com.forensics.platform.dto.EvidenceDtos.EvidenceCountResponse;
import com.forensics.platform.dto.EvidenceDtos.EvidenceDetailResponse;
import com.forensics.platform.dto.EvidenceDtos.EvidencePageResponse;
import com.forensics.platform.dto.EvidenceDtos.FilterOptions;
import com.forensics.platform.dto.EvidenceDtos.RelatedEvidenceResponse;
import com.forensics.platform.service.EvidenceService;
import com.forensics.platform.service.EvidenceService.EvidenceQuery;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/evidence")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN','INVESTIGATOR','REVIEWER')")
public class EvidenceController {
    private final EvidenceService service;

    public EvidenceController(EvidenceService service) {
        this.service = service;
    }

    @GetMapping
    public EvidencePageResponse list(@RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "50") int size,
                                     @RequestParam(defaultValue = "eventTime") String sort,
                                     @RequestParam(defaultValue = "desc") String direction,
                                     @RequestParam(required = false) String search,
                                     @RequestParam(required = false) String artifactType,
                                     @RequestParam(required = false) String category,
                                     @RequestParam(required = false) Long deviceId,
                                     @RequestParam(required = false) Long caseId,
                                     @RequestParam(required = false) Long fileId,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime,
                                     @RequestParam(required = false) String source,
                                     @RequestParam(required = false) String location,
                                     @RequestParam(required = false) String entity,
                                     @RequestParam(required = false) String timestampStatus,
                                     Authentication authentication) {
        return service.listEvidence(new EvidenceQuery(page, size, sort, direction, search, artifactType, category, deviceId, caseId,
                fileId, startTime, endTime, source, location, entity, timestampStatus), authentication);
    }

    @GetMapping("/count")
    public EvidenceCountResponse count(@RequestParam(required = false) String search,
                                       @RequestParam(required = false) String artifactType,
                                       @RequestParam(required = false) String category,
                                       @RequestParam(required = false) Long deviceId,
                                       @RequestParam(required = false) Long caseId,
                                       @RequestParam(required = false) Long fileId,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime,
                                       @RequestParam(required = false) String source,
                                       @RequestParam(required = false) String location,
                                       @RequestParam(required = false) String entity,
                                       @RequestParam(required = false) String timestampStatus,
                                       Authentication authentication) {
        return service.countEvidence(new EvidenceQuery(0, 1, "id", "asc", search, artifactType, category, deviceId, caseId,
                fileId, startTime, endTime, source, location, entity, timestampStatus), authentication);
    }

    @GetMapping("/filters")
    public FilterOptions filters() {
        return service.filterOptions();
    }

    @GetMapping("/device/{deviceId}")
    public EvidencePageResponse device(@PathVariable Long deviceId,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "50") int size,
                                       @RequestParam(defaultValue = "eventTime") String sort,
                                       @RequestParam(defaultValue = "desc") String direction,
                                       @RequestParam(required = false) String search,
                                       @RequestParam(required = false) String artifactType,
                                       Authentication authentication) {
        return service.listEvidence(new EvidenceQuery(page, size, sort, direction, search, artifactType, null, deviceId, null,
                null, null, null, null, null, null, null), authentication);
    }

    @GetMapping("/{id}")
    public EvidenceDetailResponse detail(@PathVariable Long id, Authentication authentication) {
        return service.getEvidence(id, authentication);
    }

    @GetMapping("/{id}/related")
    public List<RelatedEvidenceResponse> related(@PathVariable Long id, Authentication authentication) {
        return service.relatedEvidence(id, authentication);
    }
}
