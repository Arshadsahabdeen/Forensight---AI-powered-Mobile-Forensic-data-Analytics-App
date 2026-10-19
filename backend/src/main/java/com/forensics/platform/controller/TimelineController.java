package com.forensics.platform.controller;

import com.forensics.platform.dto.TimelineEventDto.TimelineResponse;
import com.forensics.platform.service.TimelineService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/timeline")
@SecurityRequirement(name = "bearerAuth")
public class TimelineController {

    private final TimelineService timelineService;

    public TimelineController(TimelineService timelineService) {
        this.timelineService = timelineService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','INVESTIGATOR','REVIEWER')")
    public TimelineResponse timeline(@RequestParam Long caseId,
                                     @RequestParam(required = false) Long deviceId,
                                     @RequestParam(required = false) String entity,
                                     @RequestParam(required = false) String types,
                                     @RequestParam(required = false) String category,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                     @RequestParam(defaultValue = "false") boolean flaggedOnly,
                                     @RequestParam(defaultValue = "asc") String order,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "200") int size,
                                     Authentication authentication) {
        Set<String> typeSet = types == null || types.isBlank() ? Set.of()
                : Arrays.stream(types.split(",")).map(String::trim).filter(value -> !value.isEmpty()).collect(Collectors.toSet());
        return timelineService.caseTimeline(new TimelineService.TimelineQuery(caseId, deviceId, entity, typeSet, category, from, to,
                flaggedOnly, order, page, size), authentication);
    }
}
