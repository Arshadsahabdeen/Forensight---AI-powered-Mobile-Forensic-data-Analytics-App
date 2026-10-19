package com.forensics.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forensics.platform.dto.AIAnalysisResponse;
import com.forensics.platform.entity.Device;
import com.forensics.platform.entity.Evidence;
import com.forensics.platform.entity.InvestigationCase;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AIAnalysisGroundingTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void ungroundedStatementsCitationsAndEntitiesAreRemoved() throws Exception {
        InvestigationCase c = TestEvidence.investigationCase(1);
        Device d = TestEvidence.device(1, c, "Phone");
        Evidence sms = TestEvidence.sms(d, "+919845011223", "+919900112233", "Shipment moved to the north gate", "2026-07-14T03:42:00Z");
        long id = sms.getId();
        long invented = id + 999;

        String aiJson = """
                {"engine":"llm","model":"test-model",
                 "answer":"The shipment moved [%d]. A meeting was planned [%d].",
                 "statements":[
                   {"text":"Shipment moved to the north gate.","kind":"RECORD_FACT","evidenceIds":[%d]},
                   {"text":"The suspects met at midnight.","kind":"INFERENCE","evidenceIds":[%d]},
                   {"text":"No citation at all.","kind":"INFERENCE","evidenceIds":[]}],
                 "citations":[{"evidenceId":%d,"reason":"mentions shipment"},{"evidenceId":%d,"reason":"made up"}],
                 "entities":[
                   {"type":"PHONE","value":"9845011223","evidenceIds":[%d]},
                   {"type":"PERSON","value":"Invented Person","evidenceIds":[%d]}],
                 "limitations":[]}
                """.formatted(id, invented, id, invented, id, invented, id, id);

        var result = AIAnalysisService.ground(AIAnalysisResponse.Task.ASK, mapper.readTree(aiJson), List.of(sms), 1);

        assertThat(result.statements()).extracting(AIAnalysisResponse.Statement::text).containsExactly("Shipment moved to the north gate.");
        assertThat(result.citations()).extracting(AIAnalysisResponse.Citation::evidenceId).containsExactly(id);
        // The snippet comes from the stored record, not from the AI response.
        assertThat(result.citations().get(0).snippet()).isEqualTo("Shipment moved to the north gate");
        // Phone matched after normalization; the invented person does not appear in the cited record.
        assertThat(result.entities()).extracting(AIAnalysisResponse.EntityMention::value).containsExactly("9845011223");
        assertThat(result.droppedUngroundedItems()).isGreaterThanOrEqualTo(5);
        assertThat(result.limitations()).anyMatch(text -> text.contains("not supplied"));
        assertThat(result.answerIsAiGenerated()).isTrue();
    }

    @Test
    void fallbackIsLabelledAndOnlyCitesSelectedRecords() {
        InvestigationCase c = TestEvidence.investigationCase(1);
        Device d = TestEvidence.device(1, c, "Phone");
        Evidence one = TestEvidence.sms(d, "1111111111", "2222222222", "invoice copies", "2026-07-14T03:42:00Z");
        Evidence two = TestEvidence.sms(d, "1111111111", "2222222222", "unrelated", "2026-07-14T03:43:00Z");

        var selected = AIAnalysisService.select(List.of(one, two), "invoice", AIAnalysisResponse.Task.SEARCH, 50);
        var result = AIAnalysisService.fallback(AIAnalysisResponse.Task.SEARCH, "invoice", selected, 2, "AI service unavailable.");

        assertThat(selected).containsExactly(one);
        assertThat(result.status()).isEqualTo("FALLBACK");
        assertThat(result.engine()).isEqualTo("backend-keyword-fallback");
        assertThat(result.answerIsAiGenerated()).isFalse();
        assertThat(result.citations()).extracting(AIAnalysisResponse.Citation::evidenceId).containsExactly(one.getId());
        assertThat(result.limitations().get(0)).contains("no AI interpretation was performed");
    }

    @Test
    void searchWithoutMatchingTermsSelectsNothing() {
        InvestigationCase c = TestEvidence.investigationCase(1);
        Device d = TestEvidence.device(1, c, "Phone");
        Evidence one = TestEvidence.sms(d, "1111111111", "2222222222", "hello", "2026-07-14T03:42:00Z");
        assertThat(AIAnalysisService.select(List.of(one), "weapons", AIAnalysisResponse.Task.ASK, 50)).isEmpty();
        assertThat(AIAnalysisService.select(List.of(one), "", AIAnalysisResponse.Task.SUMMARIZE, 50)).containsExactly(one);
    }
}
