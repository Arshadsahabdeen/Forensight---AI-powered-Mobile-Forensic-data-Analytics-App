package com.forensics.platform.integration;

import com.forensics.platform.repository.AuditLogRepository;
import com.forensics.platform.service.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class AuditChainIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private AuditService auditService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void tamperingWithAChainedRecordIsDetected() {
        auditService.record("tester", "TEST_EVENT", "first");
        auditService.record("tester", "TEST_EVENT", "second");
        auditService.record("tester", "TEST_EVENT", "third");
        assertThat(auditService.verifyChain().status()).isEqualTo("INTACT");

        Long target = auditLogRepository.findTopByEntryHashIsNotNullOrderByIdDesc().orElseThrow().getId() - 1;
        // H2 has no append-only trigger (PostgreSQL does, see migration V10); simulate a direct database edit.
        jdbcTemplate.update("update audit_log set description = 'edited' where id = ?", target);

        AuditService.ChainVerification result = auditService.verifyChain();
        assertThat(result.status()).isEqualTo("BROKEN");
        assertThat(result.firstBrokenRecordId()).isEqualTo(target);

        // Restore so other tests sharing the in-memory database see an intact chain.
        jdbcTemplate.update("update audit_log set description = 'second' where id = ?", target);
        assertThat(auditService.verifyChain().status()).isEqualTo("INTACT");
    }
}
