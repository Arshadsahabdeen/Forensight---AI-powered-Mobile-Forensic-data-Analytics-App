package com.forensics.platform.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * Evidence-grounded assistant output. Every statement, citation and entity references evidence IDs
 * that were supplied to the assistant; anything citing other IDs is removed before it is returned.
 *
 * <ul>
 *   <li>{@code engine}: {@code llm} (generative model), {@code extractive} (rule-based retrieval in the AI service)
 *       or {@code backend-keyword-fallback} (AI service unavailable).</li>
 *   <li>{@code status}: COMPLETED, FALLBACK, NO_EVIDENCE.</li>
 *   <li>Statement {@code kind}: RECORD_FACT (restates a record), AGGREGATE (counts/ranges over records) or
 *       INFERENCE (an interpretation that must be verified).</li>
 * </ul>
 */
public record AIAnalysisResponse(
        String task,
        String engine,
        String model,
        String status,
        String answer,
        boolean answerIsAiGenerated,
        List<Statement> statements,
        List<Citation> citations,
        List<EntityMention> entities,
        List<String> limitations,
        String disclaimer,
        int evidenceConsidered,
        int evidenceAvailable,
        int droppedUngroundedItems,
        Instant generatedAt
) {

    public enum Task { SEARCH, SUMMARIZE, ENTITIES, ASK }

    public record Request(
            @NotNull Task task,
            @Size(max = 1000) String question,
            Long deviceId,
            @Min(5) @Max(300) Integer maxRecords
    ) {}

    public record Statement(String text, String kind, List<Long> evidenceIds) {}

    public record Citation(Long evidenceId, String reason, String snippet, String artifactType, Instant eventTime, String deviceName) {}

    public record EntityMention(String type, String value, List<Long> evidenceIds) {}

    public record Status(boolean reachable, String engine, String provider, String model, boolean llmConfigured,
                         boolean externalProcessingAllowed, String message, String serviceUrl) {}
}
