package com.forensics.platform.dto;

import java.time.Instant;
import java.util.List;

/**
 * Result of the evidence correlation engine for one case.
 *
 * <p>{@code basis} is either {@code VERIFIED_RECORD} (the relationship is stated directly by the cited
 * records) or {@code INFERRED} (derived by a rule such as identifier normalization, shared identifiers
 * across devices, or proximity in time). Neither basis establishes identity, intent or wrongdoing.</p>
 */
public record RelationshipGraphDto(
        Long caseId,
        Long deviceId,
        Instant generatedAt,
        int evidenceConsidered,
        List<Entity> entities,
        List<Relationship> relationships,
        List<Rule> rules,
        String disclaimer,
        boolean truncated
) {

    public record Entity(
            String id,
            String type,
            String label,
            List<String> variants,
            int evidenceCount,
            List<Long> deviceIds,
            Instant firstSeen,
            Instant lastSeen,
            String note
    ) {}

    public record Relationship(
            String id,
            String source,
            String target,
            String type,
            String basis,
            String rule,
            String explanation,
            int recordCount,
            List<Long> evidenceIds,
            boolean evidenceTruncated,
            Instant firstSeen,
            Instant lastSeen
    ) {}

    public record Rule(String code, String basis, String description) {}
}
