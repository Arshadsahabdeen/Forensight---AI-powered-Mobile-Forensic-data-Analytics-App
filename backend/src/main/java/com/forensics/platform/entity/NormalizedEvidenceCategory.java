package com.forensics.platform.entity;

public enum NormalizedEvidenceCategory {
    COMMUNICATION,
    LOCATION,
    CONTACT,
    MEDIA,
    BROWSER,
    ACCOUNT,
    /** Artifact types that do not match a known category (previously these were mislabelled as LOCATION). */
    OTHER
}
