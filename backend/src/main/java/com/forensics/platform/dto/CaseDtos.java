        package com.forensics.platform.dto;

        import com.forensics.platform.entity.CasePriority;
        import com.forensics.platform.entity.CaseStatus;
        import jakarta.validation.constraints.NotBlank;
        import jakarta.validation.constraints.NotNull;
        import jakarta.validation.constraints.Size;

        import java.time.Instant;
        import java.util.List;

        public class CaseDtos {
        public record CaseRequest(
                @NotBlank @Size(max = 80) String caseNumber,
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

        public record InvestigatorAssignment(
                String name,
                String role,
                String assignmentType
        ) {}

        public record DeviceItem(
        Long id,
        String deviceName,
        String operatingSystem,
        com.forensics.platform.entity.DeviceStatus acquisitionStatus
        ) {}

        public record EvidenceItem(
                Long id,
                String artifactType,
                String fileName,
                String hashSha256,
                String deviceName,
                Instant createdAt
        ) {}

        public record ReportItem(
                Long id,
                String reportNumber,
                String status,
                Instant generatedAt
        ) {}

        public record ActivityItem(
                Long id,
                String actor,
                String action,
                String description,
                Instant timestamp
        ) {}

        public record CaseDetailResponse(
                CaseResponse overview,
                List<InvestigatorAssignment> investigators,
                List<DeviceItem> devices,
                List<EvidenceItem> evidence,
                List<ActivityItem> timeline,
                List<ReportItem> reports,
                String notes,
                List<ActivityItem> activity
        ) {}
        }
