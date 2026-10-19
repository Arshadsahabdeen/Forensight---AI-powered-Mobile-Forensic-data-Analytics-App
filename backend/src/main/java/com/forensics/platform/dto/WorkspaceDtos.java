package com.forensics.platform.dto;

import com.forensics.platform.dto.EvidenceDtos.EvidenceResponse;
import com.forensics.platform.entity.InvestigationFinding;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class WorkspaceDtos {
    private WorkspaceDtos() {
    }

    public record BookmarkRequest(@NotNull Long evidenceId, @Size(max = 120) String label, @Size(max = 1000) String note) {}

    public record BookmarkResponse(Long id, Long caseId, Long evidenceId, String label, String note, String createdBy,
                                   Instant createdAt, EvidenceResponse evidence) {}

    public record NoteRequest(@NotBlank @Size(max = 200) String title, @NotBlank @Size(max = 20000) String body) {}

    public record NoteResponse(Long id, Long caseId, String title, String body, String createdBy, Instant createdAt,
                               String updatedBy, Instant updatedAt) {}

    public record FindingRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 20000) String description,
            @NotNull InvestigationFinding.Basis basis,
            @NotNull InvestigationFinding.Significance significance,
            @Size(max = 200) List<Long> evidenceIds,
            Boolean includeInReport,
            Integer narrativeOrder,
            Boolean aiGenerated,
            @Size(max = 120) String aiEngine
    ) {}

    public record ReviewRequest(@NotNull InvestigationFinding.ReviewStatus decision, @Size(max = 1000) String comment) {}

    public record FindingResponse(
            Long id,
            Long caseId,
            String title,
            String description,
            InvestigationFinding.Basis basis,
            InvestigationFinding.Significance significance,
            InvestigationFinding.ReviewStatus reviewStatus,
            boolean aiGenerated,
            String aiEngine,
            boolean includeInReport,
            Integer narrativeOrder,
            List<EvidenceResponse> evidence,
            List<Long> missingEvidenceIds,
            Instant earliestEventTime,
            Instant latestEventTime,
            String createdBy,
            Instant createdAt,
            String updatedBy,
            Instant updatedAt,
            String reviewedBy,
            Instant reviewedAt,
            String reviewComment
    ) {}

    public record WorkspaceResponse(List<BookmarkResponse> bookmarks, List<NoteResponse> notes, List<FindingResponse> findings,
                                    List<FindingResponse> narrative) {}
}
