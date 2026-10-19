package com.forensics.platform.service;

import com.forensics.platform.entity.InvestigationCase;
import com.forensics.platform.repository.CaseRepository;
import com.forensics.platform.repository.UserRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Single place where case-level authorization is decided.
 * <ul>
 *   <li>ADMIN: view and edit every case.</li>
 *   <li>REVIEWER: view every case; never edits investigation data.</li>
 *   <li>INVESTIGATOR: view and edit only cases where they are the lead investigator
 *       (matched on username or full name, case-insensitive).</li>
 * </ul>
 * All evidence, device, timeline, correlation, assistant, workspace and report access is
 * derived from the case the data belongs to, so cross-case access is blocked on the server.
 */
@Service
public class CaseAccessService {

    private final CaseRepository caseRepository;
    private final UserRepository userRepository;

    public CaseAccessService(CaseRepository caseRepository, UserRepository userRepository) {
        this.caseRepository = caseRepository;
        this.userRepository = userRepository;
    }

    public record Actor(String username, String fullName, boolean admin, boolean investigator, boolean reviewer) {
        public boolean unrestrictedView() {
            return admin || reviewer;
        }

        public boolean canWrite() {
            return admin || investigator;
        }
    }

    public Actor actor(Authentication authentication) {
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        Set<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        String username = authentication.getName();
        String fullName = userRepository.findByUsername(username).map(user -> user.getFullName()).orElse(username);
        return new Actor(
                username,
                fullName,
                roles.contains("ROLE_ADMIN"),
                roles.contains("ROLE_INVESTIGATOR"),
                roles.contains("ROLE_REVIEWER")
        );
    }

    @Transactional(readOnly = true)
    public InvestigationCase requireViewable(Long caseId, Authentication authentication) {
        InvestigationCase investigationCase = load(caseId);
        if (!canView(investigationCase, actor(authentication))) {
            throw forbidden();
        }
        return investigationCase;
    }

    @Transactional(readOnly = true)
    public InvestigationCase requireEditable(Long caseId, Authentication authentication) {
        InvestigationCase investigationCase = load(caseId);
        if (!canEdit(investigationCase, actor(authentication))) {
            throw forbidden();
        }
        return investigationCase;
    }

    public void assertCanView(InvestigationCase investigationCase, Authentication authentication) {
        if (investigationCase == null || !canView(investigationCase, actor(authentication))) {
            throw forbidden();
        }
    }

    public void assertCanEdit(InvestigationCase investigationCase, Authentication authentication) {
        if (investigationCase == null || !canEdit(investigationCase, actor(authentication))) {
            throw forbidden();
        }
    }

    public boolean canView(InvestigationCase investigationCase, Actor actor) {
        return actor.unrestrictedView() || (actor.investigator() && isLead(investigationCase, actor));
    }

    public boolean canEdit(InvestigationCase investigationCase, Actor actor) {
        return actor.admin() || (actor.investigator() && isLead(investigationCase, actor));
    }

    /**
     * Case IDs the actor may view, or empty when access is unrestricted.
     */
    @Transactional(readOnly = true)
    public Optional<Set<Long>> accessibleCaseIds(Authentication authentication) {
        Actor actor = actor(authentication);
        if (actor.unrestrictedView()) {
            return Optional.empty();
        }
        if (!actor.investigator()) {
            return Optional.of(Set.of());
        }
        Set<Long> ids = caseRepository.findAll((root, query, cb) -> leadPredicate(root, cb, actor)).stream()
                .map(InvestigationCase::getId)
                .collect(Collectors.toSet());
        return Optional.of(ids);
    }

    /**
     * Criteria predicate limiting a query to cases the actor may view.
     */
    public Predicate restrictToViewableCases(Path<InvestigationCase> casePath, CriteriaBuilder cb, Authentication authentication) {
        Actor actor = actor(authentication);
        if (actor.unrestrictedView()) {
            return cb.conjunction();
        }
        if (!actor.investigator()) {
            return cb.disjunction();
        }
        return leadPredicate(casePath, cb, actor);
    }

    private Predicate leadPredicate(Path<InvestigationCase> casePath, CriteriaBuilder cb, Actor actor) {
        var lead = cb.lower(casePath.get("leadInvestigator"));
        return cb.or(
                cb.equal(lead, actor.username().toLowerCase(Locale.ROOT)),
                cb.equal(lead, actor.fullName().toLowerCase(Locale.ROOT))
        );
    }

    private boolean isLead(InvestigationCase investigationCase, Actor actor) {
        String lead = investigationCase.getLeadInvestigator();
        return lead != null && (lead.equalsIgnoreCase(actor.username()) || lead.equalsIgnoreCase(actor.fullName()));
    }

    private InvestigationCase load(Long caseId) {
        if (caseId == null || caseId < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid case ID is required");
        }
        return caseRepository.findById(caseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found"));
    }

    private ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this case");
    }
}
