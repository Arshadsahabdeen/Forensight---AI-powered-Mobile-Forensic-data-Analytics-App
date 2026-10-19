package com.forensics.platform.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class IntegrationTestSupport {

    private static final AtomicInteger CASE_COUNTER = new AtomicInteger();

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    protected String login(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "password", password))))
                .andReturn();
        if (result.getResponse().getStatus() != 200) {
            throw new IllegalStateException("Login failed for " + username + ": " + result.getResponse().getContentAsString());
        }
        return json.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    protected String admin() throws Exception {
        return login("admin", "Admin@123");
    }

    /** Seeded investigator "Aarav Sharma". */
    protected String investigator() throws Exception {
        return login("investigator", "Investigator@123");
    }

    protected String otherInvestigator() throws Exception {
        return login("inv01", "Investigator@123");
    }

    protected String reviewer() throws Exception {
        return login("reviewer", "Reviewer@123");
    }

    protected MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder builder, String token) {
        return builder.header("Authorization", "Bearer " + token);
    }

    protected JsonNode body(MvcResult result) throws Exception {
        String content = result.getResponse().getContentAsString();
        return content.isEmpty() ? json.nullNode() : json.readTree(content);
    }

    /** Creates a case as admin with the given lead investigator; returns its id. */
    protected long createCase(String adminToken, String lead) throws Exception {
        Map<String, Object> request = Map.of(
                "caseNumber", "IT-" + System.nanoTime() + "-" + CASE_COUNTER.incrementAndGet(),
                "title", "Integration test case",
                "description", "Created by an automated test.",
                "status", "ACTIVE",
                "priority", "MEDIUM",
                "leadInvestigator", lead,
                "category", "Test",
                "caseType", "Mobile Forensics");
        MvcResult result = mvc.perform(auth(post("/api/v1/cases"), adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andReturn();
        if (result.getResponse().getStatus() != 200) {
            throw new IllegalStateException("Case creation failed: " + result.getResponse().getContentAsString());
        }
        return body(result).get("id").asLong();
    }

    protected static byte[] sample(String name) throws Exception {
        return Files.readAllBytes(Path.of("..", "datasets", "samples", name));
    }

    protected MvcResult upload(String token, long caseId, String fileName, byte[] content) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", fileName, "application/octet-stream", content);
        return mvc.perform(auth(multipart("/api/v1/cases/" + caseId + "/evidence-files").file(file), token)).andReturn();
    }
}
