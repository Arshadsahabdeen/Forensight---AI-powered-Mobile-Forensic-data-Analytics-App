package com.forensics.platform.dto;

import com.forensics.platform.entity.NormalizedEvidenceCategory;
import com.forensics.platform.entity.TimestampStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class EvidenceDtos {
    private EvidenceDtos() { }

    /**
     * An original (as-extracted) evidence record with its provenance.
     * {@code hashSha256} is the SHA-256 of the source file the record was extracted from; it is null when
     * the stored value is not a valid SHA-256 (legacy/demo rows), in which case {@code hashNote} explains why.
     */
    public record EvidenceResponse(
            Long id,
            String artifactType,
            NormalizedEvidenceCategory category,
            String fileName,
            String hashSha256,
            String hashNote,
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
            String originalTimestamp,
            String originalTimezone,
            TimestampStatus timestampStatus,
            Instant createdAt,
            Long deviceId,
            String deviceName,
            Long caseId,
            String caseNumber,
            Long sourceFileId,
            String sourceRecordRef,
            String recordHash
    ) { }

    public record EvidencePageResponse(List<EvidenceResponse> items, long totalElements, int totalPages,
            int page, int size, boolean first, boolean last) { }

    public record EvidenceCountResponse(long totalElements) { }

    public record NormalizedView(Long id, NormalizedEvidenceCategory category, String sourceTool, String title,
                                 String content, Map<String, Object> metadata) { }

    public record EvidenceDetailResponse(EvidenceResponse evidence, NormalizedView normalized, Long bookmarkId,
                                         List<FindingRef> findings) { }

    public record FindingRef(Long id, String title, String basis, String reviewStatus) { }

    /**
     * A record related to another through an explainable rule.
     */
    public record RelatedEvidenceResponse(Long id, String relationshipType, String relationship, String basis,
                                          String reason, EvidenceResponse evidence) { }

    public record FilterOptions(List<String> artifactTypes, List<String> categories, List<String> timestampStatuses) { }
}
