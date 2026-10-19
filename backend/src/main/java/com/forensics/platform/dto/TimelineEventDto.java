package com.forensics.platform.dto;

import com.forensics.platform.entity.NormalizedEvidenceCategory;
import com.forensics.platform.entity.TimestampStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * One timeline entry backed by exactly one evidence record ({@code evidenceId}).
 * {@code eventTime} is normalized to UTC; {@code originalTimestamp}/{@code originalTimezone} are as
 * they appeared in the source, and {@code timestampStatus} says how the UTC value was derived.
 */
public record TimelineEventDto(
        Long evidenceId,
        String artifactType,
        NormalizedEvidenceCategory category,
        String title,
        String summary,
        String source,
        String target,
        String phoneNumber,
        String contactName,
        String location,
        Double latitude,
        Double longitude,
        Instant eventTime,
        String originalTimestamp,
        String originalTimezone,
        TimestampStatus timestampStatus,
        Long deviceId,
        String deviceName,
        Long caseId,
        Long sourceFileId,
        String sourceRecordRef,
        List<Flag> flags
) {

    /**
     * An explicit data-quality or consistency observation about this event's timestamp.
     */
    public record Flag(String code, String severity, String message, List<Long> relatedEvidenceIds) {}

    public record TimelineResponse(
            Long caseId,
            Long deviceId,
            int page,
            int size,
            long totalDated,
            int totalPages,
            long totalUndated,
            Instant firstEventTime,
            Instant lastEventTime,
            Map<TimestampStatus, Long> timestampStatusCounts,
            long flaggedEvents,
            List<TimelineEventDto> events,
            List<TimelineEventDto> undated,
            String timezoneNote
    ) {}
}
