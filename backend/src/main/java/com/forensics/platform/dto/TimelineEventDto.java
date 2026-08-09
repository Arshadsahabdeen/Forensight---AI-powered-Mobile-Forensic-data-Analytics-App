package com.forensics.platform.dto;

import java.time.Instant;

public record TimelineEventDto(
        Long id,
        String artifactType,
        String title,
        String content,
        String source,
        String target,
        String phoneNumber,
        String contactName,
        String location,
        Double latitude,
        Double longitude,
        Instant eventTime,
        Long deviceId
) {
}