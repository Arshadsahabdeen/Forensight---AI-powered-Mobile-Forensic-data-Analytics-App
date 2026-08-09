package com.forensics.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forensics.platform.dto.AIAnalysisResponse;
import com.forensics.platform.entity.Evidence;
import com.forensics.platform.repository.EvidenceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

@Service
public class AIAnalysisService {

    private final EvidenceRepository evidenceRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.ai-service-url:http://ai-service:8000}")
    private String aiServiceUrl;

    public AIAnalysisService(
            EvidenceRepository evidenceRepository,
            ObjectMapper objectMapper
    ) {
        this.evidenceRepository = evidenceRepository;
        this.objectMapper = objectMapper;
    }

    public AIAnalysisResponse analyzeDevice(Long deviceId) throws Exception {

        List<Evidence> evidence =
                evidenceRepository.findByDeviceId(deviceId);

        List<Map<String, Object>> items = new ArrayList<>();

        for (Evidence e : evidence) {

            Map<String, Object> item = new LinkedHashMap<>();

            item.put("id", e.getId());
            item.put("artifactType", safe(e.getArtifactType()));
            item.put("title", safe(e.getTitle()));
            item.put("content", safe(e.getContent()));
            item.put("source", safe(e.getSource()));
            item.put("target", safe(e.getTarget()));
            item.put("phoneNumber", safe(e.getPhoneNumber()));
            item.put("contactName", safe(e.getContactName()));
            item.put("location", safe(e.getLocation()));
            item.put(
                    "eventTime",
                    e.getEventTime() != null
                            ? e.getEventTime().toString()
                            : ""
            );

            items.add(item);
        }

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("evidence", items);

        String json = objectMapper.writeValueAsString(requestBody);

        System.out.println("===== AI REQUEST =====");
        System.out.println(json);
        System.out.println("=====================");

        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(aiServiceUrl + "/analyze"))
                .version(HttpClient.Version.HTTP_1_1)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response =
                client.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

        System.out.println("AI STATUS: " + response.statusCode());
        System.out.println("AI RESPONSE: " + response.body());

        if (response.statusCode() < 200 ||
                response.statusCode() >= 300) {

            throw new RuntimeException(
                    "AI service returned HTTP "
                            + response.statusCode()
                            + ": "
                            + response.body()
            );
        }

        return objectMapper.readValue(
                response.body(),
                AIAnalysisResponse.class
        );
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}