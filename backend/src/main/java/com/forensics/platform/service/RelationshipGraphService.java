package com.forensics.platform.service;

import com.forensics.platform.dto.EvidenceDtos.RelatedEvidenceResponse;
import com.forensics.platform.dto.RelationshipGraphDto;
import com.forensics.platform.entity.Evidence;
import com.forensics.platform.mapper.EvidenceMapper;
import com.forensics.platform.repository.EvidenceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Case-scoped access to the {@link CorrelationEngine}.
 */
@Service
public class RelationshipGraphService {

    private static final int MAX_RELATED = 50;

    private final EvidenceRepository evidenceRepository;
    private final CaseAccessService caseAccessService;

    public RelationshipGraphService(EvidenceRepository evidenceRepository, CaseAccessService caseAccessService) {
        this.evidenceRepository = evidenceRepository;
        this.caseAccessService = caseAccessService;
    }

    @Transactional(readOnly = true)
    public RelationshipGraphDto buildCaseGraph(Long caseId, Long deviceId, boolean includeEvents, int windowMinutes,
                                               int maxEntities, Authentication authentication) {
        caseAccessService.requireViewable(caseId, authentication);
        if (windowMinutes < 0 || windowMinutes > 24 * 60) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "windowMinutes must be between 0 and 1440");
        }
        if (maxEntities < 10 || maxEntities > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "maxEntities must be between 10 and 2000");
        }
        List<Evidence> evidence = evidenceRepository.findAllForCase(caseId);
        if (deviceId != null) {
            evidence = evidence.stream().filter(e -> Objects.equals(e.getDevice().getId(), deviceId)).toList();
        }
        evidence = evidence.stream().sorted(Comparator.comparing(Evidence::getId)).toList();
        return CorrelationEngine.build(evidence, new CorrelationEngine.Options(caseId, deviceId, includeEvents,
                Duration.ofMinutes(windowMinutes), maxEntities));
    }

    /**
     * Records in the same case that share an identifier, name or location with {@code evidence},
     * or that appear to be the same communication captured on another device.
     */
    @Transactional(readOnly = true)
    public List<RelatedEvidenceResponse> relatedRecords(Evidence evidence) {
        Long caseId = evidence.getDevice().getInvestigationCase().getId();
        List<CorrelationEngine.Identifier> mine = CorrelationEngine.allIdentifiers(evidence);
        String corroboration = CorrelationEngine.corroborationKey(evidence);

        record Candidate(Evidence evidence, String type, String basis, String reason, int score) {}
        List<Candidate> candidates = new ArrayList<>();

        for (Evidence other : evidenceRepository.findAllForCase(caseId)) {
            if (other.getId().equals(evidence.getId())) {
                continue;
            }
            String otherCorroboration = CorrelationEngine.corroborationKey(other);
            if (corroboration != null && corroboration.equals(otherCorroboration)
                    && !Objects.equals(other.getDevice().getId(), evidence.getDevice().getId())
                    && Duration.between(evidence.getEventTime(), other.getEventTime()).abs().toMinutes() <= 2) {
                candidates.add(new Candidate(other, "SAME_COMMUNICATION_OTHER_DEVICE", CorrelationEngine.INFERRED,
                        "Same participants and content within 2 minutes on " + CorrelationEngine.deviceLabel(other.getDevice())
                                + "; likely the same communication seen from the other device.", 100));
                continue;
            }

            Map<String, CorrelationEngine.Identifier> theirs = new LinkedHashMap<>();
            for (CorrelationEngine.Identifier id : CorrelationEngine.allIdentifiers(other)) {
                theirs.putIfAbsent(id.key(), id);
            }
            List<String> verified = new ArrayList<>();
            List<String> normalized = new ArrayList<>();
            String firstType = null;
            for (CorrelationEngine.Identifier id : mine) {
                CorrelationEngine.Identifier match = theirs.get(id.key());
                if (match == null) {
                    continue;
                }
                if (firstType == null) {
                    firstType = "SHARED_" + id.type();
                }
                if (match.raw().equalsIgnoreCase(id.raw())) {
                    verified.add(id.raw());
                } else {
                    normalized.add(id.raw() + " ≈ " + match.raw());
                }
            }
            if (verified.isEmpty() && normalized.isEmpty()) {
                continue;
            }
            String basis = verified.isEmpty() ? CorrelationEngine.INFERRED : CorrelationEngine.VERIFIED;
            StringBuilder reason = new StringBuilder();
            if (!verified.isEmpty()) {
                reason.append("Both records contain ").append(String.join(", ", verified)).append(".");
            }
            if (!normalized.isEmpty()) {
                if (!reason.isEmpty()) {
                    reason.append(' ');
                }
                reason.append("Matched after normalizing phone formats: ").append(String.join(", ", normalized)).append(".");
            }
            candidates.add(new Candidate(other, firstType, basis, reason.toString(), 10 * (verified.size() + normalized.size())));
        }

        Comparator<Candidate> byTimeGap = Comparator.comparingLong(c -> gapSeconds(evidence, c.evidence()));
        return candidates.stream()
                .sorted(Comparator.comparingInt((Candidate c) -> -c.score()).thenComparing(byTimeGap).thenComparing(c -> c.evidence().getId()))
                .limit(MAX_RELATED)
                .map(c -> new RelatedEvidenceResponse(c.evidence().getId(), c.type(), c.type(), c.basis(), c.reason(),
                        EvidenceMapper.toResponse(c.evidence())))
                .toList();
    }

    private static long gapSeconds(Evidence a, Evidence b) {
        if (a.getEventTime() == null || b.getEventTime() == null) {
            return Long.MAX_VALUE;
        }
        return Duration.between(a.getEventTime(), b.getEventTime()).abs().getSeconds();
    }
}
