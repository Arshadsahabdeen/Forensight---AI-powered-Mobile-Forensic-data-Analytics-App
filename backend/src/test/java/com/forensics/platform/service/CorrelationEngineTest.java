package com.forensics.platform.service;

import com.forensics.platform.dto.RelationshipGraphDto;
import com.forensics.platform.entity.Device;
import com.forensics.platform.entity.Evidence;
import com.forensics.platform.entity.InvestigationCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationEngineTest {

    private Device phoneA;
    private Device phoneB;
    private List<Evidence> evidence;
    private Evidence smsOnA;
    private Evidence sameSmsOnB;

    @BeforeEach
    void setUp() {
        InvestigationCase c = TestEvidence.investigationCase(1);
        phoneA = TestEvidence.device(10, c, "Phone A");
        phoneB = TestEvidence.device(20, c, "Phone B");
        smsOnA = TestEvidence.sms(phoneA, "+919845011223", "+919900112233", "Pickup at 11", "2026-07-14T03:50:00Z");
        sameSmsOnB = TestEvidence.sms(phoneB, "+919845011223", "+919900112233", "Pickup at 11", "2026-07-14T03:51:00Z");
        evidence = List.of(
                smsOnA,
                TestEvidence.sms(phoneA, "+919900112233", "+919845011223", "Confirmed", "2026-07-14T03:55:00Z"),
                TestEvidence.contact(phoneA, "R. Mehta", "+91 98450 11223"),
                TestEvidence.location(phoneA, "Bengaluru Central", "2026-07-14T04:00:00Z"),
                sameSmsOnB,
                TestEvidence.contact(phoneB, "Mehta (work)", "9845011223")
        );
    }

    private RelationshipGraphDto build() {
        return CorrelationEngine.build(evidence, new CorrelationEngine.Options(1L, null, false, Duration.ofMinutes(15), 400));
    }

    @Test
    void phoneFormatsNormalizeToTheSameKey() {
        assertThat(CorrelationEngine.phoneKey("+91 98450 11223")).isEqualTo("9845011223");
        assertThat(CorrelationEngine.phoneKey("9845011223")).isEqualTo("9845011223");
        assertThat(CorrelationEngine.phoneKey("080-4455-6677")).isEqualTo(CorrelationEngine.phoneKey("08044556677"));
        assertThat(CorrelationEngine.phoneKey("john@example.com")).isNull();
        assertThat(CorrelationEngine.phoneKey("2026")).isNull();
    }

    @Test
    void communicationRelationshipIsVerifiedAndCitesItsRecords() {
        var graph = build();
        var communication = graph.relationships().stream().filter(r -> r.type().equals("COMMUNICATED_WITH")).toList();
        assertThat(communication).hasSize(1);
        var edge = communication.get(0);
        assertThat(edge.basis()).isEqualTo(CorrelationEngine.VERIFIED);
        assertThat(edge.rule()).isEqualTo("COMMUNICATION_RECORD");
        assertThat(edge.recordCount()).isEqualTo(3);
        assertThat(edge.explanation()).contains("sender and recipient");
    }

    @Test
    void crossDeviceLinksAreInferredNotVerified() {
        var graph = build();
        var shared = graph.relationships().stream().filter(r -> r.type().equals("SHARED_IDENTIFIER")).findFirst().orElseThrow();
        assertThat(shared.basis()).isEqualTo(CorrelationEngine.INFERRED);
        assertThat(Set.of(shared.source(), shared.target())).containsExactlyInAnyOrder("device:10", "device:20");

        var corroborated = graph.relationships().stream().filter(r -> r.type().equals("CORROBORATED_BY")).findFirst().orElseThrow();
        assertThat(corroborated.basis()).isEqualTo(CorrelationEngine.INFERRED);
        assertThat(corroborated.evidenceIds()).contains(smsOnA.getId(), sameSmsOnB.getId());
    }

    @Test
    void mergedPhoneFormatsAreDisclosedOnTheEntity() {
        var graph = build();
        var phone = graph.entities().stream().filter(e -> e.id().equals("phone:9845011223")).findFirst().orElseThrow();
        assertThat(phone.variants()).contains("+919845011223", "+91 98450 11223", "9845011223");
        assertThat(phone.note()).contains("inference");
        assertThat(phone.deviceIds()).containsExactlyInAnyOrder(10L, 20L);
    }

    @Test
    void temporalProximityIsInferredWithinTheWindowOnly() {
        var graph = build();
        var near = graph.relationships().stream().filter(r -> r.type().equals("NEAR_IN_TIME")).toList();
        assertThat(near).isNotEmpty().allSatisfy(r -> assertThat(r.basis()).isEqualTo(CorrelationEngine.INFERRED));

        var none = CorrelationEngine.build(evidence, new CorrelationEngine.Options(1L, null, false, Duration.ofMinutes(1), 400));
        assertThat(none.relationships().stream().filter(r -> r.type().equals("NEAR_IN_TIME"))).isEmpty();
    }

    @Test
    void everyRelationshipIsTraceableToSuppliedRecords() {
        Set<Long> supplied = evidence.stream().map(Evidence::getId).collect(Collectors.toSet());
        var graph = build();
        assertThat(graph.relationships()).isNotEmpty().allSatisfy(r -> {
            assertThat(r.evidenceIds()).isNotEmpty();
            assertThat(supplied).containsAll(r.evidenceIds());
            assertThat(r.explanation()).isNotBlank();
        });
        assertThat(graph.disclaimer()).contains("not evidence of intent or wrongdoing");
    }

    @Test
    void outputIsDeterministic() {
        var first = build();
        var second = build();
        assertThat(first.relationships()).usingRecursiveComparison().isEqualTo(second.relationships());
        assertThat(first.entities()).usingRecursiveComparison().isEqualTo(second.entities());
    }
}
