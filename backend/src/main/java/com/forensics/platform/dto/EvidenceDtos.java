package com.forensics.platform.dto;

import java.time.Instant;
import java.util.List;

public final class EvidenceDtos {
    private EvidenceDtos() { }

    public record EvidenceResponse(Long id, String artifactType, String fileName, String hashSha256,
            String title, String content, String source, String target, String phoneNumber,
            String contactName, String location, Double latitude, Double longitude, Instant eventTime,
            Instant createdAt, Long deviceId, String deviceName, Long caseId, String caseNumber) { }
    public record EvidencePageResponse(List<EvidenceResponse> items, long totalElements, int totalPages,
            int page, int size, boolean first, boolean last) { }
    public record EvidenceCountResponse(long totalElements) { }
    public record RelatedEvidenceResponse(Long id, String relationshipType, String relationship,
                                          EvidenceResponse evidence) { }
}
