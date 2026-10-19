package com.forensics.platform.service;

import com.forensics.platform.dto.EvidenceDtos.EvidenceCountResponse;
import com.forensics.platform.dto.EvidenceDtos.EvidenceDetailResponse;
import com.forensics.platform.dto.EvidenceDtos.EvidencePageResponse;
import com.forensics.platform.dto.EvidenceDtos.EvidenceResponse;
import com.forensics.platform.dto.EvidenceDtos.FilterOptions;
import com.forensics.platform.dto.EvidenceDtos.FindingRef;
import com.forensics.platform.dto.EvidenceDtos.NormalizedView;
import com.forensics.platform.dto.EvidenceDtos.RelatedEvidenceResponse;
import com.forensics.platform.entity.Evidence;
import com.forensics.platform.entity.NormalizedEvidenceCategory;
import com.forensics.platform.entity.TimestampStatus;
import com.forensics.platform.mapper.EvidenceMapper;
import com.forensics.platform.repository.EvidenceBookmarkRepository;
import com.forensics.platform.repository.EvidenceRepository;
import com.forensics.platform.repository.InvestigationFindingRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class EvidenceService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> SORT_FIELDS = Set.of("eventTime", "createdAt", "id", "artifactType");

    private final EvidenceRepository evidenceRepository;
    private final CaseAccessService caseAccessService;
    private final AuditService auditService;
    private final EvidenceNormalizationService normalizationService;
    private final EvidenceBookmarkRepository bookmarkRepository;
    private final InvestigationFindingRepository findingRepository;
    private final RelationshipGraphService correlationService;

    public EvidenceService(EvidenceRepository evidenceRepository, CaseAccessService caseAccessService, AuditService auditService,
                           EvidenceNormalizationService normalizationService, EvidenceBookmarkRepository bookmarkRepository,
                           InvestigationFindingRepository findingRepository, RelationshipGraphService correlationService) {
        this.evidenceRepository = evidenceRepository;
        this.caseAccessService = caseAccessService;
        this.auditService = auditService;
        this.normalizationService = normalizationService;
        this.bookmarkRepository = bookmarkRepository;
        this.findingRepository = findingRepository;
        this.correlationService = correlationService;
    }

    /**
     * Search filters. All values are optional except paging and sorting.
     *
     * @param entity identifier search across sender, recipient, phone number and contact name;
     *               phone-like input also matches when only digits are compared
     */
    public record EvidenceQuery(int page, int size, String sort, String direction, String search, String artifactType,
                                String category, Long deviceId, Long caseId, Long fileId, Instant startTime, Instant endTime,
                                String source, String location, String entity, String timestampStatus) {
        public static EvidenceQuery of(int page, int size, String sort, String direction) {
            return new EvidenceQuery(page, size, sort, direction, null, null, null, null, null, null, null, null, null, null, null, null);
        }
    }

    @Transactional(readOnly = true)
    public EvidencePageResponse listEvidence(EvidenceQuery query, Authentication authentication) {
        validate(query.page(), query.size(), query.sort(), query.direction(), query.deviceId(), query.caseId(), query.startTime(), query.endTime());
        if (query.caseId() != null) {
            caseAccessService.requireViewable(query.caseId(), authentication);
        }
        Sort sort = Sort.by(Sort.Direction.fromString(query.direction()), query.sort()).and(Sort.by(Sort.Direction.ASC, "id"));
        Page<Evidence> results = evidenceRepository.findAll(specification(query, authentication), PageRequest.of(query.page(), query.size(), sort));
        return new EvidencePageResponse(results.getContent().stream().map(EvidenceMapper::toResponse).toList(), results.getTotalElements(),
                results.getTotalPages(), results.getNumber(), results.getSize(), results.isFirst(), results.isLast());
    }

    @Transactional(readOnly = true)
    public EvidenceCountResponse countEvidence(EvidenceQuery query, Authentication authentication) {
        validate(0, 1, "id", "asc", query.deviceId(), query.caseId(), query.startTime(), query.endTime());
        return new EvidenceCountResponse(evidenceRepository.count(specification(query, authentication)));
    }

    @Transactional(readOnly = true)
    public EvidenceDetailResponse getEvidence(Long id, Authentication authentication) {
        Evidence evidence = findViewable(id, authentication);
        Long caseId = evidence.getDevice().getInvestigationCase().getId();
        auditService.record(new AuditService.Entry(null, authentication.getName(), "EVIDENCE_VIEWED",
                "Viewed evidence record #" + id, true, caseId, "EVIDENCE", String.valueOf(id), null));

        NormalizedView normalized = normalizationService.getBySourceEvidenceId(id)
                .map(item -> new NormalizedView(item.id(), item.category(), item.sourceTool(), item.title(), item.content(), item.metadata()))
                .orElse(null);
        Long bookmarkId = bookmarkRepository.findByCaseIdAndEvidenceId(caseId, id).map(bookmark -> bookmark.getId()).orElse(null);
        List<FindingRef> findings = findingRepository.findByCaseIdOrderByCreatedAtAsc(caseId).stream()
                .filter(finding -> finding.getEvidenceIds().contains(id))
                .map(finding -> new FindingRef(finding.getId(), finding.getTitle(), finding.getBasis().name(), finding.getReviewStatus().name()))
                .toList();
        return new EvidenceDetailResponse(EvidenceMapper.toResponse(evidence), normalized, bookmarkId, findings);
    }

    @Transactional(readOnly = true)
    public List<RelatedEvidenceResponse> relatedEvidence(Long id, Authentication authentication) {
        Evidence evidence = findViewable(id, authentication);
        return correlationService.relatedRecords(evidence);
    }

    @Transactional(readOnly = true)
    public FilterOptions filterOptions() {
        return new FilterOptions(
                evidenceRepository.findDistinctArtifactTypes(),
                Arrays.stream(NormalizedEvidenceCategory.values()).map(Enum::name).toList(),
                Arrays.stream(TimestampStatus.values()).map(Enum::name).toList()
        );
    }

    public Evidence findViewable(Long id, Authentication authentication) {
        if (id == null || id < 1) {
            throw bad("Evidence ID must be positive");
        }
        Evidence evidence = evidenceRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evidence not found"));
        if (evidence.getDevice() == null || evidence.getDevice().getInvestigationCase() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Evidence is not linked to an accessible case");
        }
        caseAccessService.assertCanView(evidence.getDevice().getInvestigationCase(), authentication);
        return evidence;
    }

    private void validate(int page, int size, String sort, String direction, Long deviceId, Long caseId, Instant startTime, Instant endTime) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) throw bad("page must be zero or greater and size must be between 1 and " + MAX_PAGE_SIZE);
        if ((deviceId != null && deviceId < 1) || (caseId != null && caseId < 1)) throw bad("IDs must be positive");
        if (startTime != null && endTime != null && startTime.isAfter(endTime)) throw bad("startTime must be before or equal to endTime");
        if (!SORT_FIELDS.contains(sort)) throw bad("Unsupported sort field");
        try { Sort.Direction.fromString(direction); } catch (IllegalArgumentException ex) { throw bad("direction must be asc or desc"); }
    }

    private Specification<Evidence> specification(EvidenceQuery q, Authentication authentication) {
        NormalizedEvidenceCategory category = q.category() == null || q.category().isBlank() ? null : EvidenceCategories.parse(q.category());
        if (q.category() != null && !q.category().isBlank() && category == null) {
            throw bad("Unknown category filter");
        }
        TimestampStatus timestampStatus = parseTimestampStatus(q.timestampStatus());
        List<String> categoryTypes = category == null ? null : EvidenceCategories.typesFor(category, evidenceRepository.findDistinctArtifactTypes());

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            jakarta.persistence.criteria.Join<Evidence, com.forensics.platform.entity.Device> device = root.join("device");
            jakarta.persistence.criteria.Join<com.forensics.platform.entity.Device, com.forensics.platform.entity.InvestigationCase> investigationCase =
                    device.join("investigationCase");
            predicates.add(caseAccessService.restrictToViewableCases(investigationCase, cb, authentication));

            if (text(q.search())) {
                String term = like(q.search());
                String[] fields = {"title", "content", "artifactType", "source", "target", "phoneNumber", "contactName", "location", "fileName", "originalTimestamp"};
                predicates.add(cb.or(Arrays.stream(fields).map(field -> cb.like(cb.lower(root.get(field)), term)).toArray(Predicate[]::new)));
            }
            if (text(q.entity())) {
                String term = like(q.entity());
                List<Predicate> matches = new ArrayList<>();
                for (String field : List.of("source", "target", "phoneNumber", "contactName")) {
                    matches.add(cb.like(cb.lower(root.get(field)), term));
                }
                String digits = q.entity().replaceAll("\\D", "");
                if (digits.length() >= 7) {
                    String suffix = "%" + digits.substring(Math.max(0, digits.length() - 10)) + "%";
                    for (String field : List.of("source", "target", "phoneNumber")) {
                        var stripped = cb.function("replace", String.class,
                                cb.function("replace", String.class,
                                        cb.function("replace", String.class, root.get(field), cb.literal(" "), cb.literal("")),
                                        cb.literal("-"), cb.literal("")),
                                cb.literal("+"), cb.literal(""));
                        matches.add(cb.like(stripped, suffix));
                    }
                }
                predicates.add(cb.or(matches.toArray(Predicate[]::new)));
            }
            if (text(q.artifactType())) predicates.add(cb.equal(cb.lower(root.get("artifactType")), q.artifactType().trim().toLowerCase(Locale.ROOT)));
            if (categoryTypes != null) {
                predicates.add(categoryTypes.isEmpty() ? cb.disjunction() : root.get("artifactType").in(categoryTypes));
            }
            if (q.deviceId() != null) predicates.add(cb.equal(device.get("id"), q.deviceId()));
            if (q.caseId() != null) predicates.add(cb.equal(investigationCase.get("id"), q.caseId()));
            if (q.fileId() != null) predicates.add(cb.equal(root.get("sourceFile").get("id"), q.fileId()));
            if (q.startTime() != null) predicates.add(cb.greaterThanOrEqualTo(root.get("eventTime"), q.startTime()));
            if (q.endTime() != null) predicates.add(cb.lessThanOrEqualTo(root.get("eventTime"), q.endTime()));
            if (text(q.source())) predicates.add(cb.equal(cb.lower(root.get("source")), q.source().trim().toLowerCase(Locale.ROOT)));
            if (text(q.location())) predicates.add(cb.like(cb.lower(root.get("location")), like(q.location())));
            if (timestampStatus != null) {
                if (timestampStatus == TimestampStatus.MISSING) {
                    predicates.add(cb.or(cb.equal(root.get("timestampStatus"), timestampStatus),
                            cb.and(cb.isNull(root.get("timestampStatus")), cb.isNull(root.get("eventTime")))));
                } else {
                    predicates.add(cb.equal(root.get("timestampStatus"), timestampStatus));
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private TimestampStatus parseTimestampStatus(String value) {
        if (!text(value)) {
            return null;
        }
        try {
            return TimestampStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw bad("Unknown timestampStatus filter");
        }
    }

    private static String like(String value) {
        String escaped = value.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private boolean text(String value) { return value != null && !value.isBlank(); }
    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
