package com.forensics.platform.controller;

import com.forensics.platform.dto.WorkspaceDtos.*;
import com.forensics.platform.service.WorkspaceService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cases/{caseId}/workspace")
@SecurityRequirement(name = "bearerAuth")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','INVESTIGATOR','REVIEWER')")
    public WorkspaceResponse workspace(@PathVariable Long caseId, Authentication authentication) {
        return workspaceService.workspace(caseId, authentication);
    }

    @PostMapping("/bookmarks")
    @PreAuthorize("hasAnyRole('ADMIN','INVESTIGATOR')")
    public BookmarkResponse addBookmark(@PathVariable Long caseId, @Valid @RequestBody BookmarkRequest request, Authentication authentication) {
        return workspaceService.addBookmark(caseId, request, authentication);
    }

    @DeleteMapping("/bookmarks/{bookmarkId}")
    @PreAuthorize("hasAnyRole('ADMIN','INVESTIGATOR')")
    public ResponseEntity<Void> removeBookmark(@PathVariable Long caseId, @PathVariable Long bookmarkId, Authentication authentication) {
        workspaceService.removeBookmark(caseId, bookmarkId, authentication);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/notes")
    @PreAuthorize("hasAnyRole('ADMIN','INVESTIGATOR')")
    public NoteResponse createNote(@PathVariable Long caseId, @Valid @RequestBody NoteRequest request, Authentication authentication) {
        return workspaceService.createNote(caseId, request, authentication);
    }

    @PutMapping("/notes/{noteId}")
    @PreAuthorize("hasAnyRole('ADMIN','INVESTIGATOR')")
    public NoteResponse updateNote(@PathVariable Long caseId, @PathVariable Long noteId, @Valid @RequestBody NoteRequest request,
                                   Authentication authentication) {
        return workspaceService.updateNote(caseId, noteId, request, authentication);
    }

    @DeleteMapping("/notes/{noteId}")
    @PreAuthorize("hasAnyRole('ADMIN','INVESTIGATOR')")
    public ResponseEntity<Void> deleteNote(@PathVariable Long caseId, @PathVariable Long noteId, Authentication authentication) {
        workspaceService.deleteNote(caseId, noteId, authentication);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/findings")
    @PreAuthorize("hasAnyRole('ADMIN','INVESTIGATOR')")
    public FindingResponse createFinding(@PathVariable Long caseId, @Valid @RequestBody FindingRequest request, Authentication authentication) {
        return workspaceService.createFinding(caseId, request, authentication);
    }

    @PutMapping("/findings/{findingId}")
    @PreAuthorize("hasAnyRole('ADMIN','INVESTIGATOR')")
    public FindingResponse updateFinding(@PathVariable Long caseId, @PathVariable Long findingId, @Valid @RequestBody FindingRequest request,
                                         Authentication authentication) {
        return workspaceService.updateFinding(caseId, findingId, request, authentication);
    }

    @PostMapping("/findings/{findingId}/submit")
    @PreAuthorize("hasAnyRole('ADMIN','INVESTIGATOR')")
    public FindingResponse submit(@PathVariable Long caseId, @PathVariable Long findingId, Authentication authentication) {
        return workspaceService.submitForReview(caseId, findingId, authentication);
    }

    @PostMapping("/findings/{findingId}/review")
    @PreAuthorize("hasAnyRole('ADMIN','REVIEWER')")
    public FindingResponse review(@PathVariable Long caseId, @PathVariable Long findingId, @Valid @RequestBody ReviewRequest request,
                                  Authentication authentication) {
        return workspaceService.review(caseId, findingId, request, authentication);
    }

    @DeleteMapping("/findings/{findingId}")
    @PreAuthorize("hasAnyRole('ADMIN','INVESTIGATOR')")
    public ResponseEntity<Void> deleteFinding(@PathVariable Long caseId, @PathVariable Long findingId, Authentication authentication) {
        workspaceService.deleteFinding(caseId, findingId, authentication);
        return ResponseEntity.noContent().build();
    }
}
