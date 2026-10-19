package com.forensics.platform.service;

import com.forensics.platform.dto.TimelineEventDto;
import com.forensics.platform.entity.Device;
import com.forensics.platform.entity.Evidence;
import com.forensics.platform.entity.InvestigationCase;
import com.forensics.platform.entity.TimestampStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TimelineServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-20T00:00:00Z");

    private static TimelineService.TimelineQuery query(Long caseId) {
        return new TimelineService.TimelineQuery(caseId, null, null, Set.of(), null, null, null, false, "asc", 0, 100);
    }

    @Test
    void eventsAreChronologicalAndUndatedRecordsAreKeptSeparately() {
        InvestigationCase c = TestEvidence.investigationCase(1);
        Device d = TestEvidence.device(1, c, "Phone");
        Evidence late = TestEvidence.sms(d, "1111111111", "2222222222", "late", "2026-07-14T12:00:00Z");
        Evidence early = TestEvidence.sms(d, "1111111111", "2222222222", "early", "2026-07-14T08:00:00Z");
        Evidence undated = TestEvidence.contact(d, "Someone", "3333333333");

        var result = TimelineService.build(List.of(late, early, undated), query(1L), null, NOW);

        assertThat(result.events()).extracting(TimelineEventDto::evidenceId).containsExactly(early.getId(), late.getId());
        assertThat(result.undated()).extracting(TimelineEventDto::evidenceId).containsExactly(undated.getId());
        assertThat(result.totalUndated()).isEqualTo(1);
        assertThat(result.firstEventTime()).isEqualTo(early.getEventTime());
        assertThat(result.timestampStatusCounts()).containsEntry(TimestampStatus.MISSING, 1L);
    }

    @Test
    void assumedUtcAndUnparseableTimestampsAreFlagged() {
        InvestigationCase c = TestEvidence.investigationCase(1);
        Device d = TestEvidence.device(1, c, "Phone");
        Evidence assumed = TestEvidence.sms(d, "1111111111", "2222222222", "x", "2026-07-14T18:45:00Z");
        assumed.setTimestampStatus(TimestampStatus.ASSUMED_UTC);
        Evidence garbage = TestEvidence.base(d, "CALL", null);
        garbage.setTimestampStatus(TimestampStatus.UNPARSEABLE);
        garbage.setOriginalTimestamp("not-recorded");

        var result = TimelineService.build(List.of(assumed, garbage), query(1L), null, NOW);

        assertThat(result.events().get(0).flags()).extracting(TimelineEventDto.Flag::code).contains("ASSUMED_UTC");
        assertThat(result.undated().get(0).flags()).extracting(TimelineEventDto.Flag::code).contains("UNPARSEABLE_TIMESTAMP");
        assertThat(result.undated().get(0).originalTimestamp()).isEqualTo("not-recorded");
    }

    @Test
    void sameCommunicationOnTwoDevicesIsCorroboratedOrConflicting() {
        InvestigationCase c = TestEvidence.investigationCase(1);
        Device a = TestEvidence.device(1, c, "A");
        Device b = TestEvidence.device(2, c, "B");
        Device skewed = TestEvidence.device(3, c, "C");
        Evidence onA = TestEvidence.sms(a, "1111111111", "2222222222", "meet at gate", "2026-07-14T10:00:00Z");
        Evidence onB = TestEvidence.sms(b, "1111111111", "2222222222", "meet at gate", "2026-07-14T10:01:00Z");
        Evidence onC = TestEvidence.sms(skewed, "1111111111", "2222222222", "meet at gate", "2026-07-14T15:30:00Z");

        var flags = TimelineService.computeFlags(List.of(onA, onB, onC), NOW);

        assertThat(flags.get(onA.getId())).extracting(TimelineEventDto.Flag::code).contains("CORROBORATED", "CONFLICTING_TIMESTAMP");
        var conflict = flags.get(onC.getId()).stream().filter(f -> f.code().equals("CONFLICTING_TIMESTAMP")).findFirst().orElseThrow();
        assertThat(conflict.relatedEvidenceIds()).contains(onA.getId(), onB.getId());
    }

    @Test
    void futureAndAfterSeizureTimestampsAreFlagged() {
        InvestigationCase c = TestEvidence.investigationCase(1);
        Device d = TestEvidence.device(1, c, "Phone");
        d.setSeizedDate(LocalDate.parse("2026-07-01"));
        Evidence afterSeizure = TestEvidence.sms(d, "1111111111", "2222222222", "x", "2026-07-14T10:00:00Z");
        Evidence future = TestEvidence.sms(d, "1111111111", "2222222222", "y", "2027-01-01T10:00:00Z");

        var flags = TimelineService.computeFlags(List.of(afterSeizure, future), NOW);

        assertThat(flags.get(afterSeizure.getId())).extracting(TimelineEventDto.Flag::code).contains("AFTER_SEIZURE");
        assertThat(flags.get(future.getId())).extracting(TimelineEventDto.Flag::code).contains("FUTURE_TIMESTAMP");
    }

    @Test
    void entityFilterMatchesNormalizedPhoneNumbers() {
        InvestigationCase c = TestEvidence.investigationCase(1);
        Device d = TestEvidence.device(1, c, "Phone");
        Evidence match = TestEvidence.sms(d, "+919845011223", "2222222222", "x", "2026-07-14T10:00:00Z");
        Evidence other = TestEvidence.sms(d, "3333333333", "2222222222", "y", "2026-07-14T11:00:00Z");
        var q = new TimelineService.TimelineQuery(1L, null, "98450 11223", Set.of(), null, null, null, false, "asc", 0, 100);

        var result = TimelineService.build(List.of(match, other), q, null, NOW);

        assertThat(result.events()).extracting(TimelineEventDto::evidenceId).containsExactly(match.getId());
    }
}
