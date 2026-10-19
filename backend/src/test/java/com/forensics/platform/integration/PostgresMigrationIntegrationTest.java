package com.forensics.platform.integration;

import com.fasterxml.jackson.databind.JsonNode;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs the full Flyway migration chain (V1-V11) against a real PostgreSQL 16 server, followed by
 * Hibernate's schema update, then exercises PostgreSQL-specific behaviour (append-only audit trigger,
 * timestamp round trips) through the API.
 */
class PostgresMigrationIntegrationTest extends IntegrationTestSupport {

    private static final EmbeddedPostgres POSTGRES = start();

    private static EmbeddedPostgres start() {
        try {
            return EmbeddedPostgres.builder().start();
        } catch (IOException ex) {
            throw new IllegalStateException("Embedded PostgreSQL could not be started", ex);
        }
    }

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
    }

    @AfterAll
    static void stop() throws IOException {
        POSTGRES.close();
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void allMigrationsApplyCleanlyOnAFreshDatabase() {
        List<String> versions = jdbcTemplate.queryForList(
                "select version from flyway_schema_history where success = true and version is not null order by installed_rank", String.class);
        assertThat(versions).contains("1", "5", "9", "10", "11");
        Integer failed = jdbcTemplate.queryForObject("select count(*) from flyway_schema_history where success = false", Integer.class);
        assertThat(failed).isZero();
    }

    @Test
    void auditLogIsAppendOnlyInPostgres() throws Exception {
        String admin = admin();
        createCase(admin, "Aarav Sharma");
        Long latest = jdbcTemplate.queryForObject("select max(id) from audit_log", Long.class);

        assertThatThrownBy(() -> jdbcTemplate.update("update audit_log set description = 'edited' where id = ?", latest))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbcTemplate.update("delete from audit_log where id = ?", latest))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");

        mvc.perform(auth(post("/api/v1/audit/verify"), admin)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INTACT"));
    }

    @Test
    void importTimelineAndReportWorkOnPostgres() throws Exception {
        String admin = admin();
        long caseId = createCase(admin, "Aarav Sharma");
        JsonNode uploaded = body(upload(admin, caseId, "device-a-android.xml", sample("device-a-android.xml")));
        assertThat(uploaded.get("job").get("status").asText()).isEqualTo("SUCCEEDED");

        JsonNode timeline = body(mvc.perform(auth(get("/api/v1/timeline").param("caseId", String.valueOf(caseId)), admin)).andReturn());
        // 2026-07-14T09:12:00+05:30 must round-trip as exactly 03:42 UTC regardless of the server timezone.
        assertThat(timeline.get("events").findValuesAsText("eventTime")).contains("2026-07-14T03:42:00Z");

        JsonNode report = body(mvc.perform(auth(post("/api/v1/cases/" + caseId + "/reports"), admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"includeFullTimeline\":true}")).andExpect(status().isOk()).andReturn());
        byte[] pdf = mvc.perform(auth(get("/api/v1/reports/" + report.get("id").asLong() + "/pdf"), admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");

        mvc.perform(auth(get("/api/v1/dashboard"), admin)).andExpect(status().isOk()).andExpect(jsonPath("$.evidence.records").isNumber());
    }
}
