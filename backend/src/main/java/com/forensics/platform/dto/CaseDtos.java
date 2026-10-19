package com.forensics.platform.dto;

import com.forensics.platform.entity.CasePriority;
import com.forensics.platform.entity.CaseStatus;
import com.forensics.platform.entity.DeviceStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class CaseDtos {
    public record CaseRequest(
            @NotBlank @Size(max = 80)
            @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._/-]*$", message = "Use letters, numbers, '.', '_', '-' or '/' only")
            String caseNumber,
            @NotBlank @Size(max = 180) String title,
            @NotBlank @Size(max = 2000) String description,
            @NotNull CaseStatus status,
            @NotNull CasePriority priority,
            @NotBlank @Size(max = 120) String leadInvestigator,
            @NotBlank @Size(max = 120) String category,
            @NotBlank @Size(max = 120) String caseType,
            @Size(max = 255) String location,
            @Size(max = 4000) String notes
    ) {}

    public record CaseResponse(
            Long id,
            String caseNumber,
            String title,
            String description,
            CaseStatus status,
            CasePriority priority,
            String leadInvestigator,
            String category,
            String caseType,
            String location,
            String notes,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record CaseListItem(
            Long id,
            String caseNumber,
            String title,
            CaseStatus status,
            CasePriority priority,
            String leadInvestigator,
            String category,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record CasePageResponse(
            List<CaseListItem> items,
            long totalElements,
            int totalPages,
            int page,
            int size,
            boolean first,
            boolean last
    ) {}

    public record InvestigatorAssignment(String name, String role, String assignmentType) {}

    public record DeviceItem(Long id, String deviceName, String operatingSystem, DeviceStatus acquisitionStatus,
                             String imei, long evidenceCount) {}

    public record ReportItem(Long id, String reportNumber, String reportTitle, String status, Instant generatedAt,
                             boolean hasSnapshot) {}

    public record ActivityItem(Long id, String actor, String action, String description, Instant timestamp, boolean success) {}

    public record EvidenceSummary(long totalRecords, long evidenceFiles, Map<String, Long> recordsByType,
                                  long filesVerified, long filesWithIntegrityIssues, long filesFailedProcessing) {}

    public record FindingSummary(long total, long needsReview, long reviewed, long aiGenerated) {}

    public record CaseDetailResponse(
            CaseResponse overview,
            List<InvestigatorAssignment> investigators,
            List<DeviceItem> devices,
            EvidenceSummary evidenceSummary,
            FindingSummary findingSummary,
            List<ReportItem> reports,
            List<ActivityItem> activity,
            boolean canEdit,
            boolean canDelete
    ) {}
}
