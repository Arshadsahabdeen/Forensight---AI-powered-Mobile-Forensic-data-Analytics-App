package com.forensics.platform.dto;

import com.forensics.platform.entity.EvidenceSourceFile;
import com.forensics.platform.entity.ProcessingJob;

import java.time.Instant;
import java.util.List;

public final class IngestionDtos {
    private IngestionDtos() {
    }

    public record EvidenceFileResponse(
            Long id,
            Long caseId,
            String caseNumber,
            Long deviceId,
            String deviceName,
            String originalFileName,
            String contentType,
            EvidenceSourceFile.SourceFormat sourceFormat,
            long sizeBytes,
            String sha256,
            String importedBy,
            Instant importedAt,
            EvidenceSourceFile.ProcessingStatus processingStatus,
            EvidenceSourceFile.IntegrityStatus integrityStatus,
            Instant lastVerifiedAt,
            String lastVerifiedBy,
            int recordsExtracted,
            int duplicatesSkipped,
            int timestampIssues,
            ProcessingJobResponse latestJob
    ) {}

    public record UploadResponse(EvidenceFileResponse file, ProcessingJobResponse job, String message) {}

    public record VerificationResponse(
            Long fileId,
            EvidenceSourceFile.IntegrityStatus status,
            String recordedSha256,
            String computedSha256,
            Instant verifiedAt,
            String verifiedBy,
            String message
    ) {}

    public record ProcessingJobResponse(
            Long id,
            ProcessingJob.JobType jobType,
            ProcessingJob.Status status,
            int progress,
            String stage,
            int attempts,
            String errorMessage,
            Long caseId,
            String caseNumber,
            Long deviceId,
            String deviceName,
            Long evidenceFileId,
            String fileName,
            String createdBy,
            Instant createdAt,
            Instant startedAt,
            Instant finishedAt,
            boolean canRetry
    ) {}

    public record JobLogEntry(Long id, Instant loggedAt, String level, String message) {}

    public record ProcessingJobDetail(ProcessingJobResponse job, List<JobLogEntry> logs) {}

    public record ProcessingJobPage(List<ProcessingJobResponse> items, long totalElements, int totalPages, int page, int size,
                                    long queued, long running, long failed) {}

    public record CustodyEvent(Long id, Instant timestamp, String actor, String action, String description, boolean success) {}
}
