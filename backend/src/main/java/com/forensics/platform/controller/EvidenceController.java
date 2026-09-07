package com.forensics.platform.controller;

import com.forensics.platform.dto.EvidenceDtos.*;
import com.forensics.platform.service.EvidenceService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.List;

@RestController @RequestMapping("/api/v1/evidence") @SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN','INVESTIGATOR','REVIEWER')")
public class EvidenceController {
    private final EvidenceService service;
    public EvidenceController(EvidenceService service) { this.service = service; }
    @GetMapping public EvidencePageResponse list(@RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="50") int size, @RequestParam(defaultValue="eventTime") String sort, @RequestParam(defaultValue="desc") String direction, @RequestParam(required=false) String search, @RequestParam(required=false) String artifactType, @RequestParam(required=false) Long deviceId, @RequestParam(required=false) Long caseId, @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant startTime, @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant endTime, @RequestParam(required=false) String source, @RequestParam(required=false) String location) { return service.listEvidence(page,size,sort,direction,search,artifactType,deviceId,caseId,startTime,endTime,source,location); }
    @GetMapping("/count") public EvidenceCountResponse count(@RequestParam(required=false) String search, @RequestParam(required=false) String artifactType, @RequestParam(required=false) Long deviceId, @RequestParam(required=false) Long caseId, @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant startTime, @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant endTime, @RequestParam(required=false) String source, @RequestParam(required=false) String location) { return service.countEvidence(search,artifactType,deviceId,caseId,startTime,endTime,source,location); }
    @GetMapping("/device/{deviceId}") public EvidencePageResponse device(@PathVariable Long deviceId, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="50") int size, @RequestParam(defaultValue="eventTime") String sort, @RequestParam(defaultValue="desc") String direction, @RequestParam(required=false) String search, @RequestParam(required=false) String artifactType, @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant startTime, @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant endTime, @RequestParam(required=false) String source, @RequestParam(required=false) String location) { return service.getDeviceEvidence(deviceId,page,size,sort,direction,search,artifactType,startTime,endTime,source,location); }
    @GetMapping("/{id}") public EvidenceResponse detail(@PathVariable Long id) { return service.getEvidence(id); }
    @GetMapping("/{id}/related") public List<RelatedEvidenceResponse> related(@PathVariable Long id) { return service.relatedEvidence(id); }
}
