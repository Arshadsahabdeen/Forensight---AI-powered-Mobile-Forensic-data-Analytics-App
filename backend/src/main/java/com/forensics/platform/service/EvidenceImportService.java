package com.forensics.platform.service;

import com.forensics.platform.entity.Device;
import com.forensics.platform.entity.Evidence;
import com.forensics.platform.entity.EvidenceSourceFile;
import com.forensics.platform.entity.TimestampStatus;
import com.forensics.platform.repository.EvidenceRepository;
import com.forensics.platform.repository.EvidenceSourceFileRepository;
import com.forensics.platform.ufdr.EvidenceExtractor.ExtractedRecord;
import com.forensics.platform.ufdr.EvidenceExtractor.ExtractionResult;
import com.forensics.platform.ufdr.UFDRImportService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * Persists extracted records for one evidence file in a single transaction (all-or-nothing),
 * so a failed import never leaves partial evidence behind and can be retried safely.
 */
@Service
public class EvidenceImportService {

    private static final Set<TimestampStatus> TIMESTAMP_ISSUES =
            EnumSet.of(TimestampStatus.MISSING, TimestampStatus.UNPARSEABLE, TimestampStatus.ASSUMED_UTC, TimestampStatus.DATE_ONLY);

    private final EvidenceRepository evidenceRepository;
    private final EvidenceSourceFileRepository fileRepository;
    private final UFDRImportService deviceResolver;
    private final EvidenceNormalizationService normalizationService;

    public EvidenceImportService(EvidenceRepository evidenceRepository, EvidenceSourceFileRepository fileRepository,
                                 UFDRImportService deviceResolver, EvidenceNormalizationService normalizationService) {
        this.evidenceRepository = evidenceRepository;
        this.fileRepository = fileRepository;
        this.deviceResolver = deviceResolver;
        this.normalizationService = normalizationService;
    }

    public record ImportOutcome(Long deviceId, String deviceName, boolean deviceCreated, int recordsCreated,
                                int duplicatesSkipped, int timestampIssues) {}

    @Transactional
    public ImportOutcome persist(Long fileId, ExtractionResult extraction, String actor) {
        EvidenceSourceFile file = fileRepository.findWithCaseById(fileId)
                .orElseThrow(() -> new IllegalStateException("Evidence file " + fileId + " not found"));

        UFDRImportService.Resolution resolution = deviceResolver.resolve(file, extraction.device(), actor);
        Device device = resolution.device();
        file.setDevice(device);

        Set<String> existingHashes = new HashSet<>(evidenceRepository.findRecordHashesForDevice(device.getId()));
        List<Evidence> toSave = new ArrayList<>();
        int duplicates = 0;
        int timestampIssues = 0;

        for (ExtractedRecord record : extraction.records()) {
            String recordHash = recordHash(record);
            if (!existingHashes.add(recordHash)) {
                duplicates++;
                continue;
            }
            Evidence evidence = new Evidence();
            evidence.setDevice(device);
            evidence.setSourceFile(file);
            evidence.setFileName(file.getOriginalFileName());
            evidence.setHashSha256(file.getSha256());
            evidence.setSourceRecordRef(record.sourceRecordRef());
            evidence.setRecordHash(recordHash);
            evidence.setArtifactType(record.artifactType());
            evidence.setTitle(record.title());
            evidence.setContent(record.content());
            evidence.setSource(record.source());
            evidence.setTarget(record.target());
            evidence.setPhoneNumber(record.phoneNumber());
            evidence.setContactName(record.contactName());
            evidence.setLocation(record.location());
            evidence.setLatitude(record.latitude());
            evidence.setLongitude(record.longitude());
            evidence.setEventTime(record.timestamp().instant());
            evidence.setOriginalTimestamp(truncate(record.timestamp().originalText(), 120));
            evidence.setOriginalTimezone(truncate(record.timestamp().originalTimezone(), 40));
            evidence.setTimestampStatus(record.timestamp().status());
            if (TIMESTAMP_ISSUES.contains(record.timestamp().status())) {
                timestampIssues++;
            }
            toSave.add(evidence);
        }

        List<Evidence> saved = evidenceRepository.saveAll(toSave);
        normalizationService.normalizeEvidence(saved, file.getSourceFormat().name());

        file.setRecordsExtracted(saved.size());
        file.setDuplicatesSkipped(duplicates);
        file.setTimestampIssues(timestampIssues);
        file.setProcessingStatus(EvidenceSourceFile.ProcessingStatus.PROCESSED);

        return new ImportOutcome(device.getId(), device.getDeviceName(), resolution.created(), saved.size(), duplicates, timestampIssues);
    }

    /**
     * SHA-256 over the record's extracted fields and original timestamp text. The location inside the
     * source file is excluded so the same record arriving in a second extraction is recognised.
     */
    public static String recordHash(ExtractedRecord record) {
        StringBuilder canonical = new StringBuilder();
        for (Object value : new Object[]{
                record.artifactType(), record.title(), record.content(), record.source(), record.target(),
                record.phoneNumber(), record.contactName(), record.location(), record.latitude(), record.longitude(),
                record.timestamp().originalText(), record.timestamp().originalTimezone()
        }) {
            canonical.append(value == null ? "␀" : value.toString()).append('\u001f');
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
