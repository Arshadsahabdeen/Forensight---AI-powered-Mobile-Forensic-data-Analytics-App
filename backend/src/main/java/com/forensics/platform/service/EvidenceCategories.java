package com.forensics.platform.service;

import com.forensics.platform.entity.NormalizedEvidenceCategory;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Maps raw artifact types to normalized categories. Shared by normalization, search, timeline
 * and correlation so every view categorizes records identically.
 */
public final class EvidenceCategories {

    private EvidenceCategories() {
    }

    public static String normalizeType(String artifactType) {
        if (artifactType == null || artifactType.isBlank()) {
            return "UNKNOWN";
        }
        return artifactType.trim().replace(' ', '_').replace('-', '_').toUpperCase(Locale.ROOT);
    }

    public static NormalizedEvidenceCategory resolve(String artifactType) {
        String type = normalizeType(artifactType);
        return switch (type) {
            case "SMS", "CALL", "MESSAGE", "MESSAGES", "CALLS", "EMAIL", "CHAT", "MESSAGING_APP" -> NormalizedEvidenceCategory.COMMUNICATION;
            case "LOCATION", "GPS", "CELL_TOWER", "WIFI_LOCATION" -> NormalizedEvidenceCategory.LOCATION;
            case "CONTACT", "CONTACTS" -> NormalizedEvidenceCategory.CONTACT;
            case "PHOTO", "IMAGE", "IMAGES", "VIDEO", "DOCUMENT", "DOCUMENTS", "MEDIA" -> NormalizedEvidenceCategory.MEDIA;
            case "BROWSER", "BROWSER_HISTORY", "URL", "SEARCH", "DOWNLOAD" -> NormalizedEvidenceCategory.BROWSER;
            case "ACCOUNT", "PROFILE" -> NormalizedEvidenceCategory.ACCOUNT;
            default -> {
                if (type.contains("CALL") || type.contains("SMS") || type.contains("MESSAGE") || type.contains("CHAT")) {
                    yield NormalizedEvidenceCategory.COMMUNICATION;
                }
                if (type.contains("CONTACT")) {
                    yield NormalizedEvidenceCategory.CONTACT;
                }
                if (type.contains("PHOTO") || type.contains("VIDEO") || type.contains("MEDIA") || type.contains("IMAGE")) {
                    yield NormalizedEvidenceCategory.MEDIA;
                }
                if (type.contains("BROWSER") || type.contains("SEARCH") || type.contains("URL")) {
                    yield NormalizedEvidenceCategory.BROWSER;
                }
                if (type.contains("ACCOUNT") || type.contains("PROFILE")) {
                    yield NormalizedEvidenceCategory.ACCOUNT;
                }
                if (type.contains("LOCATION") || type.contains("GPS")) {
                    yield NormalizedEvidenceCategory.LOCATION;
                }
                yield NormalizedEvidenceCategory.OTHER;
            }
        };
    }

    /**
     * Artifact types (as stored) that belong to a category; used to filter by category in queries.
     */
    public static List<String> typesFor(NormalizedEvidenceCategory category, List<String> knownTypes) {
        return knownTypes.stream().filter(type -> resolve(type) == category).toList();
    }

    public static NormalizedEvidenceCategory parse(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        String upper = category.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(NormalizedEvidenceCategory.values()).filter(value -> value.name().equals(upper)).findFirst().orElse(null);
    }
}
