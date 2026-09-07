package com.forensics.platform.service;

import com.forensics.platform.dto.EvidenceDtos.*;
import com.forensics.platform.entity.Device;
import com.forensics.platform.entity.Evidence;
import com.forensics.platform.entity.InvestigationCase;
import com.forensics.platform.repository.EvidenceRepository;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class EvidenceService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> SORT_FIELDS = Set.of("eventTime", "createdAt", "id", "artifactType");
    private final EvidenceRepository evidenceRepository;
    public EvidenceService(EvidenceRepository evidenceRepository) { this.evidenceRepository = evidenceRepository; }

    public EvidencePageResponse listEvidence(int page, int size, String sort, String direction, String search, String artifactType, Long deviceId, Long caseId, Instant startTime, Instant endTime, String source, String location) {
        validate(page, size, sort, direction, deviceId, caseId, startTime, endTime);
        Page<Evidence> results = evidenceRepository.findAll(specification(search, artifactType, deviceId, caseId, startTime, endTime, source, location), PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(direction), sort)));
        return page(results);
    }
    public EvidenceCountResponse countEvidence(String search, String artifactType, Long deviceId, Long caseId, Instant startTime, Instant endTime, String source, String location) {
        validate(0, 1, "id", "asc", deviceId, caseId, startTime, endTime);
        return new EvidenceCountResponse(evidenceRepository.count(specification(search, artifactType, deviceId, caseId, startTime, endTime, source, location)));
    }
    public EvidenceResponse getEvidence(Long id) { return toResponse(find(id)); }
    public EvidencePageResponse getDeviceEvidence(Long deviceId, int page, int size, String sort, String direction, String search, String artifactType, Instant startTime, Instant endTime, String source, String location) { return listEvidence(page, size, sort, direction, search, artifactType, deviceId, null, startTime, endTime, source, location); }
    public List<RelatedEvidenceResponse> relatedEvidence(Long id) {
        Evidence evidence = find(id);
        if (evidence.getDevice() == null || evidence.getDevice().getId() == null) return List.of();
        return evidenceRepository.findByDeviceId(evidence.getDevice().getId()).stream().filter(item -> !item.getId().equals(id)).limit(25).map(item -> new RelatedEvidenceResponse(item.getId(), "DIRECT_RELATIONSHIP", "SAME_DEVICE", toResponse(item))).toList();
    }
    private EvidencePageResponse page(Page<Evidence> results) { return new EvidencePageResponse(results.getContent().stream().map(this::toResponse).toList(), results.getTotalElements(), results.getTotalPages(), results.getNumber(), results.getSize(), results.isFirst(), results.isLast()); }
    private Evidence find(Long id) { if (id == null || id < 1) throw bad("Evidence ID must be positive"); return evidenceRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evidence not found")); }
    private void validate(int page, int size, String sort, String direction, Long deviceId, Long caseId, Instant startTime, Instant endTime) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) throw bad("page must be zero or greater and size must be between 1 and " + MAX_PAGE_SIZE);
        if ((deviceId != null && deviceId < 1) || (caseId != null && caseId < 1)) throw bad("IDs must be positive");
        if (startTime != null && endTime != null && startTime.isAfter(endTime)) throw bad("startTime must be before or equal to endTime");
        if (!SORT_FIELDS.contains(sort)) throw bad("Unsupported sort field");
        try { Sort.Direction.fromString(direction); } catch (IllegalArgumentException ex) { throw bad("direction must be asc or desc"); }
    }
    private Specification<Evidence> specification(String search, String artifactType, Long deviceId, Long caseId, Instant startTime, Instant endTime, String source, String location) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (text(search)) { String term = "%" + search.trim().toLowerCase(Locale.ROOT) + "%"; String[] fields = {"title", "content", "artifactType", "source", "target", "phoneNumber", "contactName", "location"}; predicates.add(cb.or(java.util.Arrays.stream(fields).map(field -> cb.like(cb.lower(root.get(field)), term)).toArray(jakarta.persistence.criteria.Predicate[]::new))); }
            if (text(artifactType)) predicates.add(cb.equal(cb.lower(root.get("artifactType")), artifactType.trim().toLowerCase(Locale.ROOT)));
            if (deviceId != null) predicates.add(cb.equal(root.get("device").get("id"), deviceId));
            if (caseId != null) predicates.add(cb.equal(root.get("device").get("investigationCase").get("id"), caseId));
            if (startTime != null) predicates.add(cb.greaterThanOrEqualTo(root.get("eventTime"), startTime));
            if (endTime != null) predicates.add(cb.lessThanOrEqualTo(root.get("eventTime"), endTime));
            if (text(source)) predicates.add(cb.equal(cb.lower(root.get("source")), source.trim().toLowerCase(Locale.ROOT)));
            if (text(location)) predicates.add(cb.equal(cb.lower(root.get("location")), location.trim().toLowerCase(Locale.ROOT)));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
    private EvidenceResponse toResponse(Evidence e) { Device d = e.getDevice(); InvestigationCase c = d == null ? null : d.getInvestigationCase(); return new EvidenceResponse(e.getId(), e.getArtifactType(), e.getFileName(), e.getHashSha256(), e.getTitle(), e.getContent(), e.getSource(), e.getTarget(), e.getPhoneNumber(), e.getContactName(), e.getLocation(), e.getLatitude(), e.getLongitude(), e.getEventTime(), e.getCreatedAt(), d == null ? null : d.getId(), d == null ? null : d.getDeviceName(), c == null ? null : c.getId(), c == null ? null : c.getCaseNumber()); }
    private boolean text(String value) { return value != null && !value.isBlank(); }
    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
