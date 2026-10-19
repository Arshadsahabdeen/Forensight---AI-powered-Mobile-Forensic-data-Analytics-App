package com.forensics.platform.service;

import com.forensics.platform.entity.Evidence;
import com.forensics.platform.repository.EvidenceBookmarkRepository;
import com.forensics.platform.repository.EvidenceRepository;
import com.forensics.platform.repository.InvestigationFindingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EvidenceServiceTest {

    private EvidenceService service(EvidenceRepository repository) {
        return new EvidenceService(repository, mock(CaseAccessService.class), mock(AuditService.class),
                mock(EvidenceNormalizationService.class), mock(EvidenceBookmarkRepository.class),
                mock(InvestigationFindingRepository.class), mock(RelationshipGraphService.class));
    }

    @Test
    void returnsDatabasePageWithoutMaterializingAllEvidence() {
        EvidenceRepository repository = mock(EvidenceRepository.class);
        Evidence evidence = new Evidence();
        evidence.setId(42L);
        evidence.setArtifactType("SMS");
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(evidence)));

        var query = new EvidenceService.EvidenceQuery(0, 50, "eventTime", "desc", "hello", null, null, null, null, null, null, null, null, null, null, null);
        var result = service(repository).listEvidence(query, mock(Authentication.class));

        assertEquals(1, result.totalElements());
        assertEquals(42L, result.items().getFirst().id());
        verify(repository).findAll(any(Specification.class), any(Pageable.class));
        verify(repository, never()).findAll();
    }

    @Test
    void rejectsUnsafePaginationAndSorting() {
        EvidenceService service = service(mock(EvidenceRepository.class));
        Authentication auth = mock(Authentication.class);
        assertThrows(ResponseStatusException.class, () -> service.listEvidence(EvidenceService.EvidenceQuery.of(0, 101, "id", "asc"), auth));
        assertThrows(ResponseStatusException.class, () -> service.listEvidence(EvidenceService.EvidenceQuery.of(0, 50, "title", "asc"), auth));
        assertThrows(ResponseStatusException.class, () -> service.listEvidence(EvidenceService.EvidenceQuery.of(-1, 50, "id", "asc"), auth));
        assertThrows(ResponseStatusException.class, () -> service.listEvidence(EvidenceService.EvidenceQuery.of(0, 50, "id", "sideways"), auth));
    }
}
