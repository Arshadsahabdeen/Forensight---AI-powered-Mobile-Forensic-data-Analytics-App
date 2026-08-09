package com.forensics.platform.controller;

import com.forensics.platform.dto.TimelineEventDto;
import com.forensics.platform.service.TimelineService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    public List<TimelineEventDto> getTimeline() {
        return timelineService.getAllTimeline();
    }

    @GetMapping("/device/{deviceId}")
    @PreAuthorize("hasAnyRole('ADMIN','INVESTIGATOR','REVIEWER')")
    public List<TimelineEventDto> getDeviceTimeline(
            @PathVariable Long deviceId
    ) {
        return timelineService.getDeviceTimeline(deviceId);
    }
}