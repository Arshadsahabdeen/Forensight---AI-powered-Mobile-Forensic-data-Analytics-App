package com.forensics.platform.service;

import com.forensics.platform.dto.EvidenceDtos.EvidenceResponse;
import com.forensics.platform.dto.WorkspaceDtos.*;
import com.forensics.platform.entity.Evidence;
import com.forensics.platform.entity.EvidenceBookmark;
import com.forensics.platform.entity.InvestigationFinding;
import com.forensics.platform.entity.InvestigationNote;
import com.forensics.platform.mapper.EvidenceMapper;
import com.forensics.platform.repository.EvidenceBookmarkRepository;
import com.forensics.platform.repository.EvidenceRepository;
import com.forensics.platform.repository.InvestigationFindingRepository;
import com.forensics.platform.repository.InvestigationNoteRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Investigation workspace: bookmarks, notes and findings for a case.
 * Editing requires edit access to the case. Findings are reviewed by a reviewer or administrator
 * who is not the finding's author; editing a finding clears any earlier review.
 */
@Service
public class WorkspaceService {

    private final EvidenceBookmarkRepository bookmarkRepository;
    private final InvestigationNoteRepository noteRepository;
    private final InvestigationFindingRepository findingRepository;
    private final EvidenceRepository evidenceRepository;
    private final CaseAccessService caseAccessService;
    private final AuditService auditService;

    public WorkspaceService(EvidenceBookmarkRepository bookmarkRepository, InvestigationNoteRepository noteRepository,
                            InvestigationFindingRepository findingRepository, EvidenceRepository evidenceRepository,
                            CaseAccessService caseAccessService, AuditService auditService) {
        this.bookmarkRepository = bookmarkRepository;
        this.noteRepository = noteRepository;
        this.findingRepository = findingRepository;
        this.evidenceRepository = evidenceRepository;
        this.caseAccessService = caseAccessService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public WorkspaceResponse workspace(Long caseId, Authentication authentication) {
        caseAccessService.requireViewable(caseId, authentication);
        List<BookmarkResponse> bookmarks = bookmarkRepository.findByCaseIdOrderByCreatedAtDesc(caseId).stream().map(this::toResponse).toList();
        List<NoteResponse> notes = noteRepository.findByCaseIdOrderByUpdatedAtDesc(caseId).stream().map(this::toResponse).toList();
        List<FindingResponse> findings = findingsFor(caseId);
        return new WorkspaceResponse(bookmarks, notes, findings, narrative(findings));
    }

    @Transactional(readOnly = true)
    public List<FindingResponse> findingsFor(Long caseId) {
        List<InvestigationFinding> findings = findingRepository.findByCaseIdOrderByCreatedAtAsc(caseId);
        Set<Long> ids = findings.stream().flatMap(f -> f.getEvidenceIds().stream()).collect(Collectors.toSet());
        Map<Long, Evidence> evidence = ids.isEmpty() ? Map.of()
                : evidenceRepository.findByIdIn(ids).stream().collect(Collectors.toMap(Evidence::getId, e -> e));
        return findings.stream().map(f -> toResponse(f, evidence)).toList();
    }

    /**
     * Chronological case narrative: findings marked for the report, ordered by explicit narrative
     * position when set, otherwise by the earliest time of their supporting evidence.
     */
    public static List<FindingResponse> narrative(List<FindingResponse> findings) {
        return findings.stream()
                .filter(FindingResponse::includeInReport)
                .filter(f -> f.reviewStatus() != InvestigationFinding.ReviewStatus.REJECTED)
                .sorted(Comparator
                        .comparing((FindingResponse f) -> f.narrativeOrder() == null ? Integer.MAX_VALUE : f.narrativeOrder())
                        .thenComparing(f -> f.earliestEventTime() == null ? Instant.MAX : f.earliestEventTime())
                        .thenComparing(FindingResponse::id))
                .toList();
    }

    // ---- bookmarks

    @Transactional
    public BookmarkResponse addBookmark(Long caseId, BookmarkRequest request, Authentication authentication) {
        caseAccessService.requireEditable(caseId, authentication);
        Evidence evidence = requireCaseEvidence(caseId, List.of(request.evidenceId())).get(0);
        if (bookmarkRepository.findByCaseIdAndEvidenceId(caseId, evidence.getId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This evidence record is already bookmarked");
        }
        EvidenceBookmark bookmark = new EvidenceBookmark();
        bookmark.setCaseId(caseId);
        bookmark.setEvidence(evidence);
        bookmark.setLabel(blankToNull(request.label()));
        bookmark.setNote(blankToNull(request.note()));
        bookmark.setCreatedBy(authentication.getName());
        bookmark = bookmarkRepository.save(bookmark);
        audit(authentication, "EVIDENCE_BOOKMARKED", "Bookmarked evidence #" + evidence.getId(), caseId, "BOOKMARK", bookmark.getId());
        return toResponse(bookmark);
    }

    @Transactional
    public void removeBookmark(Long caseId, Long bookmarkId, Authentication authentication) {
        caseAccessService.requireEditable(caseId, authentication);
        EvidenceBookmark bookmark = bookmarkRepository.findById(bookmarkId)
                .filter(b -> b.getCaseId().equals(caseId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bookmark not found"));
        bookmarkRepository.delete(bookmark);
        audit(authentication, "EVIDENCE_BOOKMARK_REMOVED", "Removed bookmark for evidence #" + bookmark.getEvidence().getId(), caseId, "BOOKMARK", bookmarkId);
    }

    // ---- notes

    @Transactional
    public NoteResponse createNote(Long caseId, NoteRequest request, Authentication authentication) {
        caseAccessService.requireEditable(caseId, authentication);
        InvestigationNote note = new InvestigationNote();
        note.setCaseId(caseId);
        note.setTitle(request.title().trim());
        note.setBody(request.body().trim());
        note.setCreatedBy(authentication.getName());
        note.setUpdatedBy(authentication.getName());
        note = noteRepository.save(note);
        audit(authentication, "NOTE_CREATED", "Created note #" + note.getId(), caseId, "NOTE", note.getId());
        return toResponse(note);
    }

    @Transactional
    public NoteResponse updateNote(Long caseId, Long noteId, NoteRequest request, Authentication authentication) {
        caseAccessService.requireEditable(caseId, authentication);
        InvestigationNote note = requireNote(caseId, noteId);
        note.setTitle(request.title().trim());
        note.setBody(request.body().trim());
        note.setUpdatedBy(authentication.getName());
        note.setUpdatedAt(Instant.now());
        audit(authentication, "NOTE_UPDATED", "Updated note #" + noteId, caseId, "NOTE", noteId);
        return toResponse(note);
    }

    @Transactional
    public void deleteNote(Long caseId, Long noteId, Authentication authentication) {
        caseAccessService.requireEditable(caseId, authentication);
        InvestigationNote note = requireNote(caseId, noteId);
        noteRepository.delete(note);
        audit(authentication, "NOTE_DELETED", "Deleted note #" + noteId + " ('" + note.getTitle() + "')", caseId, "NOTE", noteId);
    }

    // ---- findings

    @Transactional
    public FindingResponse createFinding(Long caseId, FindingRequest request, Authentication authentication) {
        caseAccessService.requireEditable(caseId, authentication);
        InvestigationFinding finding = new InvestigationFinding();
        finding.setCaseId(caseId);
        finding.setCreatedBy(authentication.getName());
        boolean ai = Boolean.TRUE.equals(request.aiGenerated());
        finding.setAiGenerated(ai);
        finding.setAiEngine(ai ? blankToNull(request.aiEngine()) : null);
        apply(finding, request, authentication);
        finding = findingRepository.save(finding);
        audit(authentication, "FINDING_CREATED", "Created finding #" + finding.getId() + (ai ? " (AI-generated, requires review)" : ""),
                caseId, "FINDING", finding.getId());
        return single(finding);
    }

    @Transactional
    public FindingResponse updateFinding(Long caseId, Long findingId, FindingRequest request, Authentication authentication) {
        caseAccessService.requireEditable(caseId, authentication);
        InvestigationFinding finding = requireFinding(caseId, findingId);
        boolean wasReviewed = finding.getReviewStatus() == InvestigationFinding.ReviewStatus.REVIEWED;
        apply(finding, request, authentication);
        audit(authentication, "FINDING_UPDATED", "Updated finding #" + findingId + (wasReviewed ? " (previous review cleared)" : ""),
                caseId, "FINDING", findingId);
        return single(finding);
    }

    @Transactional
    public FindingResponse submitForReview(Long caseId, Long findingId, Authentication authentication) {
        caseAccessService.requireEditable(caseId, authentication);
        InvestigationFinding finding = requireFinding(caseId, findingId);
        if (finding.getReviewStatus() == InvestigationFinding.ReviewStatus.REVIEWED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Finding is already reviewed");
        }
        finding.setReviewStatus(InvestigationFinding.ReviewStatus.NEEDS_REVIEW);
        audit(authentication, "FINDING_SUBMITTED_FOR_REVIEW", "Submitted finding #" + findingId + " for review", caseId, "FINDING", findingId);
        return single(finding);
    }

    @Transactional
    public FindingResponse review(Long caseId, Long findingId, ReviewRequest request, Authentication authentication) {
        caseAccessService.requireViewable(caseId, authentication);
        CaseAccessService.Actor actor = caseAccessService.actor(authentication);
        if (!actor.admin() && !actor.reviewer()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only reviewers or administrators can review findings");
        }
        if (request.decision() != InvestigationFinding.ReviewStatus.REVIEWED && request.decision() != InvestigationFinding.ReviewStatus.REJECTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "decision must be REVIEWED or REJECTED");
        }
        InvestigationFinding finding = requireFinding(caseId, findingId);
        if (finding.getCreatedBy().equalsIgnoreCase(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A finding cannot be reviewed by its author");
        }
        if (finding.getReviewStatus() != InvestigationFinding.ReviewStatus.NEEDS_REVIEW) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only findings awaiting review can be reviewed");
        }
        if (request.decision() == InvestigationFinding.ReviewStatus.REJECTED && (request.comment() == null || request.comment().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A comment is required when rejecting a finding");
        }
        finding.setReviewStatus(request.decision());
        finding.setReviewedBy(authentication.getName());
        finding.setReviewedAt(Instant.now());
        finding.setReviewComment(blankToNull(request.comment()));
        audit(authentication, "FINDING_" + request.decision().name(), "Finding #" + findingId + " marked " + request.decision(),
                caseId, "FINDING", findingId);
        return single(finding);
    }

    @Transactional
    public void deleteFinding(Long caseId, Long findingId, Authentication authentication) {
        caseAccessService.requireEditable(caseId, authentication);
        InvestigationFinding finding = requireFinding(caseId, findingId);
        findingRepository.delete(finding);
        audit(authentication, "FINDING_DELETED", "Deleted finding #" + findingId + " ('" + finding.getTitle() + "')", caseId, "FINDING", findingId);
    }

    private void apply(InvestigationFinding finding, FindingRequest request, Authentication authentication) {
        List<Long> evidenceIds = request.evidenceIds() == null ? List.of() : request.evidenceIds().stream().filter(Objects::nonNull).distinct().toList();
        if (!evidenceIds.isEmpty()) {
            requireCaseEvidence(finding.getCaseId(), evidenceIds);
        }
        finding.setTitle(request.title().trim());
        finding.setDescription(request.description().trim());
        // AI provenance cannot be removed by editing; AI findings always stay AI_SUGGESTED.
        finding.setBasis(finding.isAiGenerated() ? InvestigationFinding.Basis.AI_SUGGESTED : request.basis());
        finding.setSignificance(request.significance());
        finding.setEvidenceIds(new LinkedHashSet<>(evidenceIds));
        finding.setIncludeInReport(request.includeInReport() == null || request.includeInReport());
        finding.setNarrativeOrder(request.narrativeOrder());
        finding.setUpdatedBy(authentication.getName());
        finding.setUpdatedAt(Instant.now());
        finding.setReviewStatus(finding.isAiGenerated() ? InvestigationFinding.ReviewStatus.NEEDS_REVIEW : InvestigationFinding.ReviewStatus.DRAFT);
        finding.setReviewedBy(null);
        finding.setReviewedAt(null);
        finding.setReviewComment(null);
    }

    private List<Evidence> requireCaseEvidence(Long caseId, Collection<Long> ids) {
        List<Evidence> found = evidenceRepository.findByIdIn(ids);
        Set<Long> valid = found.stream()
                .filter(e -> e.getDevice() != null && e.getDevice().getInvestigationCase() != null
                        && caseId.equals(e.getDevice().getInvestigationCase().getId()))
                .map(Evidence::getId)
                .collect(Collectors.toSet());
        List<Long> invalid = ids.stream().filter(id -> !valid.contains(id)).toList();
        if (!invalid.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Evidence record(s) " + invalid + " do not exist in this case");
        }
        return found.stream().filter(e -> valid.contains(e.getId())).toList();
    }

    private FindingResponse single(InvestigationFinding finding) {
        Map<Long, Evidence> evidence = finding.getEvidenceIds().isEmpty() ? Map.of()
                : evidenceRepository.findByIdIn(finding.getEvidenceIds()).stream().collect(Collectors.toMap(Evidence::getId, e -> e));
        return toResponse(finding, evidence);
    }

    private FindingResponse toResponse(InvestigationFinding f, Map<Long, Evidence> evidenceById) {
        List<EvidenceResponse> evidence = new ArrayList<>();
        List<Long> missing = new ArrayList<>();
        for (Long id : f.getEvidenceIds()) {
            Evidence e = evidenceById.get(id);
            if (e == null) {
                missing.add(id);
            } else {
                evidence.add(EvidenceMapper.toResponse(e));
            }
        }
        evidence.sort(Comparator.comparing((EvidenceResponse e) -> e.eventTime() == null ? Instant.MAX : e.eventTime()).thenComparing(EvidenceResponse::id));
        Instant earliest = evidence.stream().map(EvidenceResponse::eventTime).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
        Instant latest = evidence.stream().map(EvidenceResponse::eventTime).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
        return new FindingResponse(f.getId(), f.getCaseId(), f.getTitle(), f.getDescription(), f.getBasis(), f.getSignificance(),
                f.getReviewStatus(), f.isAiGenerated(), f.getAiEngine(), f.isIncludeInReport(), f.getNarrativeOrder(), evidence, missing,
                earliest, latest, f.getCreatedBy(), f.getCreatedAt(), f.getUpdatedBy(), f.getUpdatedAt(), f.getReviewedBy(),
                f.getReviewedAt(), f.getReviewComment());
    }

    private BookmarkResponse toResponse(EvidenceBookmark b) {
        return new BookmarkResponse(b.getId(), b.getCaseId(), b.getEvidence().getId(), b.getLabel(), b.getNote(), b.getCreatedBy(),
                b.getCreatedAt(), EvidenceMapper.toResponse(b.getEvidence()));
    }

    private NoteResponse toResponse(InvestigationNote n) {
        return new NoteResponse(n.getId(), n.getCaseId(), n.getTitle(), n.getBody(), n.getCreatedBy(), n.getCreatedAt(), n.getUpdatedBy(), n.getUpdatedAt());
    }

    private InvestigationNote requireNote(Long caseId, Long noteId) {
        return noteRepository.findById(noteId).filter(n -> n.getCaseId().equals(caseId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Note not found"));
    }

    private InvestigationFinding requireFinding(Long caseId, Long findingId) {
        return findingRepository.findById(findingId).filter(f -> f.getCaseId().equals(caseId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Finding not found"));
    }

    private void audit(Authentication authentication, String action, String description, Long caseId, String entityType, Long entityId) {
        auditService.record(new AuditService.Entry(null, authentication.getName(), action, description, true, caseId, entityType,
                String.valueOf(entityId), null));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
