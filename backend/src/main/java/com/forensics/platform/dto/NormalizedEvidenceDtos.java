package com.forensics.platform.dto;

import com.forensics.platform.entity.NormalizedEvidenceCategory;

import java.time.Instant;
import java.util.Map;

public class NormalizedEvidenceDtos {

    public record NormalizedEvidenceResponse(

            Long id,

            String artifactType,

            NormalizedEvidenceCategory category,

            String sourceTool,

            Long deviceId,

            Long caseId,

            String title,

            String content,

            Map<String, Object> metadata,

            Instant eventTime,

            Instant createdAt

    ) {}
}
