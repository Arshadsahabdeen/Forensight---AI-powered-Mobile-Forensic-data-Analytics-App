package com.forensics.platform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forensics.platform.dto.NormalizedEvidenceDtos.NormalizedEvidenceResponse;
import com.forensics.platform.entity.Device;
import com.forensics.platform.entity.Evidence;
import com.forensics.platform.entity.InvestigationCase;
import com.forensics.platform.entity.NormalizedEvidence;
import com.forensics.platform.entity.NormalizedEvidenceCategory;
import com.forensics.platform.repository.NormalizedEvidenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class EvidenceNormalizationService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final NormalizedEvidenceRepository normalizedEvidenceRepository;
    private final ObjectMapper objectMapper;

    public List<NormalizedEvidence> normalizeEvidence(
            List<Evidence> evidenceItems,
            String sourceTool
    ) {
        return evidenceItems.stream()
                .map(evidence -> normalizeEvidence(evidence, sourceTool))
                .toList();
    }

    public NormalizedEvidence normalizeEvidence(
            Evidence evidence,
            String sourceTool
    ) {
        if (evidence == null) {
            throw new IllegalArgumentException("Evidence is required for normalization.");
        }

        Device device = evidence.getDevice();
        InvestigationCase investigationCase = device == null ? null : device.getInvestigationCase();

        if (device == null || investigationCase == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Evidence must be linked to a device and case before normalization."
            );
        }

        NormalizedEvidence normalizedEvidence = normalizedEvidenceRepository
                .findBySourceEvidenceId(evidence.getId())
                .orElseGet(NormalizedEvidence::new);

        String artifactType = normalizeArtifactType(evidence.getArtifactType());
        NormalizedEvidenceCategory category = resolveCategory(artifactType);

        normalizedEvidence.setArtifactType(artifactType);
        normalizedEvidence.setCategory(category);
        normalizedEvidence.setSourceTool(clean(sourceTool, "UFDR"));
        normalizedEvidence.setDevice(device);
        normalizedEvidence.setInvestigationCase(investigationCase);
        normalizedEvidence.setSourceEvidence(evidence);
        normalizedEvidence.setEventTime(evidence.getEventTime());
        normalizedEvidence.setCreatedAt(evidence.getCreatedAt() == null ? Instant.now() : evidence.getCreatedAt());

        switch (category) {
            case COMMUNICATION -> normalizeCommunication(normalizedEvidence, evidence);
            case LOCATION -> normalizeLocation(normalizedEvidence, evidence);
            case CONTACT, ACCOUNT -> normalizeContact(normalizedEvidence, evidence);
            case MEDIA -> normalizeMedia(normalizedEvidence, evidence);
            case BROWSER -> normalizeBrowser(normalizedEvidence, evidence);
        }

        return normalizedEvidenceRepository.save(normalizedEvidence);
    }

    @Transactional(readOnly = true)
    public List<NormalizedEvidenceResponse> getByDeviceId(Long deviceId) {
        return normalizedEvidenceRepository.findByDeviceIdOrderByEventTimeDescCreatedAtDesc(deviceId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NormalizedEvidenceResponse> getByArtifactType(String artifactType) {
        return normalizedEvidenceRepository.findByArtifactTypeIgnoreCaseOrderByEventTimeDescCreatedAtDesc(artifactType)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NormalizedEvidenceResponse> getByCategory(NormalizedEvidenceCategory category) {
        return normalizedEvidenceRepository.findByCategoryOrderByEventTimeDescCreatedAtDesc(category)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NormalizedEvidenceResponse> getByDeviceIdAndCategory(
            Long deviceId,
            NormalizedEvidenceCategory category
    ) {
        return normalizedEvidenceRepository.findByDeviceIdAndCategoryOrderByEventTimeDescCreatedAtDesc(deviceId, category)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public NormalizedEvidenceResponse getById(Long id) {
        return normalizedEvidenceRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Normalized evidence not found."));
    }

    @Transactional(readOnly = true)
    public List<NormalizedEvidenceResponse> searchByKeyword(String keyword) {
        String cleanedKeyword = clean(keyword);
        if (cleanedKeyword == null) {
            return List.of();
        }

        return normalizedEvidenceRepository.searchByKeyword(cleanedKeyword)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public void normalizeCommunication(
            NormalizedEvidence normalizedEvidence,
            Evidence evidence
    ) {
        String content = clean(evidence.getContent());
        String title = clean(
                evidence.getTitle(),
                content != null ? abbreviate(content, 120) : clean(evidence.getArtifactType(), "Communication")
        );

        normalizedEvidence.setTitle(title);
        normalizedEvidence.setContent(content);
        normalizedEvidence.setMetadataJson(writeMetadata(metadataEntries(
                entry("fileName", clean(evidence.getFileName())),
                entry("source", clean(evidence.getSource())),
                entry("target", clean(evidence.getTarget())),
                entry("phoneNumber", clean(evidence.getPhoneNumber())),
                entry("contactName", clean(evidence.getContactName())),
                entry("location", clean(evidence.getLocation())),
                entry("latitude", evidence.getLatitude()),
                entry("longitude", evidence.getLongitude()),
                entry("sourceEvidenceId", evidence.getId()),
                entry("aiReadyText", buildAiReadyText(evidence)),
                entry("communicationParticipants", buildParticipants(evidence))
        )));
    }

    public void normalizeLocation(
            NormalizedEvidence normalizedEvidence,
            Evidence evidence
    ) {
        String resolvedLocation = clean(
                evidence.getLocation(),
                coordinateLabel(evidence.getLatitude(), evidence.getLongitude())
        );

        normalizedEvidence.setTitle(clean(evidence.getTitle(), resolvedLocation, "Location Artifact"));
        normalizedEvidence.setContent(clean(evidence.getContent(), resolvedLocation));
        normalizedEvidence.setMetadataJson(writeMetadata(metadataEntries(
                entry("fileName", clean(evidence.getFileName())),
                entry("location", resolvedLocation),
                entry("latitude", evidence.getLatitude()),
                entry("longitude", evidence.getLongitude()),
                entry("sourceEvidenceId", evidence.getId()),
                entry("aiReadyText", buildAiReadyText(evidence))
        )));
    }

    public void normalizeContact(
            NormalizedEvidence normalizedEvidence,
            Evidence evidence
    ) {
        String displayName = clean(evidence.getContactName(), evidence.getTitle(), evidence.getSource(), "Contact Artifact");
        String detail = clean(evidence.getContent(), evidence.getPhoneNumber(), evidence.getTarget(), evidence.getLocation());

        normalizedEvidence.setTitle(displayName);
        normalizedEvidence.setContent(detail);
        normalizedEvidence.setMetadataJson(writeMetadata(metadataEntries(
                entry("fileName", clean(evidence.getFileName())),
                entry("contactName", clean(evidence.getContactName())),
                entry("phoneNumber", clean(evidence.getPhoneNumber())),
                entry("source", clean(evidence.getSource())),
                entry("target", clean(evidence.getTarget())),
                entry("location", clean(evidence.getLocation())),
                entry("sourceEvidenceId", evidence.getId()),
                entry("aiReadyText", buildAiReadyText(evidence))
        )));
    }

    public void normalizeMedia(
            NormalizedEvidence normalizedEvidence,
            Evidence evidence
    ) {
        String fileName = clean(evidence.getFileName());
        String title = clean(evidence.getTitle(), fileName, clean(evidence.getArtifactType(), "Media Artifact"));
        String content = clean(evidence.getContent());

        normalizedEvidence.setTitle(title);
        normalizedEvidence.setContent(content);
        normalizedEvidence.setMetadataJson(writeMetadata(metadataEntries(
                entry("fileName", fileName),
                entry("hashSha256", clean(evidence.getHashSha256())),
                entry("location", clean(evidence.getLocation())),
                entry("latitude", evidence.getLatitude()),
                entry("longitude", evidence.getLongitude()),
                entry("sourceEvidenceId", evidence.getId()),
                entry("aiReadyText", buildAiReadyText(evidence))
        )));
    }

    public void normalizeBrowser(
            NormalizedEvidence normalizedEvidence,
            Evidence evidence
    ) {
        String title = clean(evidence.getTitle(), evidence.getContent(), "Browser Artifact");

        normalizedEvidence.setTitle(title);
        normalizedEvidence.setContent(clean(evidence.getContent()));
        normalizedEvidence.setMetadataJson(writeMetadata(metadataEntries(
                entry("fileName", clean(evidence.getFileName())),
                entry("source", clean(evidence.getSource())),
                entry("target", clean(evidence.getTarget())),
                entry("sourceEvidenceId", evidence.getId()),
                entry("aiReadyText", buildAiReadyText(evidence))
        )));
    }

    private String buildAiReadyText(Evidence evidence) {
        return clean(String.join(" | ", List.of(
                clean(evidence.getArtifactType()),
                clean(evidence.getTitle()),
                clean(evidence.getContent()),
                clean(evidence.getSource()),
                clean(evidence.getTarget()),
                clean(evidence.getPhoneNumber()),
                clean(evidence.getContactName()),
                clean(evidence.getLocation())
        ).stream().filter(value -> value != null && !value.isBlank()).toList()));
    }

    private List<String> buildParticipants(Evidence evidence) {
        List<String> participants = new ArrayList<>();
        addIfPresent(participants, clean(evidence.getSource()));
        addIfPresent(participants, clean(evidence.getTarget()));
        addIfPresent(participants, clean(evidence.getPhoneNumber()));
        addIfPresent(participants, clean(evidence.getContactName()));
        return participants.stream().distinct().toList();
    }

    private String writeMetadata(Map<String, Object> rawMetadata) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        rawMetadata.forEach((key, value) -> {
            if (value instanceof String stringValue) {
                String cleaned = clean(stringValue);
                if (cleaned != null) {
                    metadata.put(key, cleaned);
                }
                return;
            }

            if (value != null) {
                metadata.put(key, value);
            }
        });

        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize normalized evidence metadata.", ex);
        }
    }

    @SafeVarargs
    private Map<String, Object> metadataEntries(Map.Entry<String, Object>... entries) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : entries) {
            metadata.put(entry.getKey(), entry.getValue());
        }
        return metadata;
    }

    private Map.Entry<String, Object> entry(String key, Object value) {
        return new java.util.AbstractMap.SimpleEntry<>(key, value);
    }

    private NormalizedEvidenceResponse toResponse(NormalizedEvidence normalizedEvidence) {
        return new NormalizedEvidenceResponse(
                normalizedEvidence.getId(),
                normalizedEvidence.getArtifactType(),
                normalizedEvidence.getCategory(),
                normalizedEvidence.getSourceTool(),
                normalizedEvidence.getDevice().getId(),
                normalizedEvidence.getInvestigationCase().getId(),
                normalizedEvidence.getTitle(),
                normalizedEvidence.getContent(),
                parseMetadata(normalizedEvidence.getMetadataJson()),
                normalizedEvidence.getEventTime(),
                normalizedEvidence.getCreatedAt()
        );
    }

    private Map<String, Object> parseMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return Map.of();
        }

        try {
            return objectMapper.readValue(metadataJson, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            return Map.of("raw", metadataJson);
        }
    }

    private NormalizedEvidenceCategory resolveCategory(String artifactType) {
        return switch (artifactType) {
            case "SMS", "CALL", "MESSAGE", "EMAIL", "CHAT", "MESSAGING_APP" -> NormalizedEvidenceCategory.COMMUNICATION;
            case "LOCATION", "GPS", "CELL_TOWER", "WIFI_LOCATION" -> NormalizedEvidenceCategory.LOCATION;
            case "CONTACT" -> NormalizedEvidenceCategory.CONTACT;
            case "PHOTO", "IMAGE", "VIDEO", "DOCUMENT", "MEDIA" -> NormalizedEvidenceCategory.MEDIA;
            case "BROWSER", "BROWSER_HISTORY", "URL", "SEARCH", "DOWNLOAD" -> NormalizedEvidenceCategory.BROWSER;
            case "ACCOUNT", "PROFILE" -> NormalizedEvidenceCategory.ACCOUNT;
            default -> {
                if (artifactType.contains("CALL") || artifactType.contains("SMS") || artifactType.contains("MESSAGE")) {
                    yield NormalizedEvidenceCategory.COMMUNICATION;
                }
                if (artifactType.contains("CONTACT")) {
                    yield NormalizedEvidenceCategory.CONTACT;
                }
                if (artifactType.contains("PHOTO") || artifactType.contains("VIDEO") || artifactType.contains("MEDIA")) {
                    yield NormalizedEvidenceCategory.MEDIA;
                }
                if (artifactType.contains("BROWSER") || artifactType.contains("SEARCH") || artifactType.contains("URL")) {
                    yield NormalizedEvidenceCategory.BROWSER;
                }
                if (artifactType.contains("ACCOUNT") || artifactType.contains("PROFILE")) {
                    yield NormalizedEvidenceCategory.ACCOUNT;
                }
                yield NormalizedEvidenceCategory.LOCATION;
            }
        };
    }

    private String normalizeArtifactType(String value) {
        String cleaned = clean(value, "UNKNOWN");
        return cleaned.replace(' ', '_').replace('-', '_').toUpperCase(Locale.ROOT);
    }

    private String coordinateLabel(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            return null;
        }

        return latitude + ", " + longitude;
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 3) + "...";
    }

    private String clean(String value, String fallback) {
        String cleaned = clean(value);
        return cleaned != null ? cleaned : clean(fallback);
    }

    private String clean(String... values) {
        for (String value : values) {
            String cleaned = clean(value);
            if (cleaned != null) {
                return cleaned;
            }
        }
        return null;
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }

        String cleaned = value.replace('\u0000', ' ').trim();
        cleaned = cleaned.replaceAll("\\s+", " ");
        return cleaned.isBlank() ? null : cleaned;
    }

    private void addIfPresent(List<String> values, String value) {
        if (value != null && !value.isBlank()) {
            values.add(value);
        }
    }
}
