package com.forensics.platform.mapper;

import com.forensics.platform.dto.EvidenceDtos.EvidenceResponse;
import com.forensics.platform.entity.Device;
import com.forensics.platform.entity.Evidence;
import com.forensics.platform.entity.InvestigationCase;
import com.forensics.platform.entity.TimestampStatus;
import com.forensics.platform.service.EvidenceCategories;

import java.util.regex.Pattern;

public final class EvidenceMapper {

    private static final Pattern SHA256_HEX = Pattern.compile("^[0-9a-fA-F]{64}$");

    private EvidenceMapper() {
    }

    public static EvidenceResponse toResponse(Evidence e) {
        Device d = e.getDevice();
        InvestigationCase c = d == null ? null : d.getInvestigationCase();
        String hash = e.getHashSha256();
        String hashNote = null;
        if (hash != null && !SHA256_HEX.matcher(hash).matches()) {
            hashNote = "Stored value is not a SHA-256 digest (legacy or demo record); no integrity hash is available.";
            hash = null;
        } else if (hash == null) {
            hashNote = e.getSourceFile() == null
                    ? "Record predates source-file tracking; no source-file hash is available."
                    : null;
        }
        TimestampStatus status = e.getTimestampStatus();
        if (status == null) {
            status = e.getEventTime() == null ? TimestampStatus.MISSING : TimestampStatus.LEGACY_IMPORT;
        }
        return new EvidenceResponse(
                e.getId(),
                e.getArtifactType(),
                EvidenceCategories.resolve(e.getArtifactType()),
                e.getFileName(),
                hash == null ? null : hash.toLowerCase(),
                hashNote,
                e.getTitle(),
                e.getContent(),
                e.getSource(),
                e.getTarget(),
                e.getPhoneNumber(),
                e.getContactName(),
                e.getLocation(),
                e.getLatitude(),
                e.getLongitude(),
                e.getEventTime(),
                e.getOriginalTimestamp(),
                e.getOriginalTimezone(),
                status,
                e.getCreatedAt(),
                d == null ? null : d.getId(),
                d == null ? null : d.getDeviceName(),
                c == null ? null : c.getId(),
                c == null ? null : c.getCaseNumber(),
                e.getSourceFile() == null ? null : e.getSourceFile().getId(),
                e.getSourceRecordRef(),
                e.getRecordHash()
        );
    }
}
