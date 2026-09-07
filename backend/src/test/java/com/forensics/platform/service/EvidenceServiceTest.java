package com.forensics.platform.service;

import com.forensics.platform.entity.Evidence;
import com.forensics.platform.repository.EvidenceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EvidenceServiceTest {
    @Test
    void returnsDatabasePageWithoutMaterializingAllEvidence() {
        EvidenceRepository repository = mock(EvidenceRepository.class);
        Evidence evidence = new Evidence(); evidence.setId(42L); evidence.setArtifactType("SMS");
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(evidence)));
        var result = new EvidenceService(repository).listEvidence(0, 50, "eventTime", "desc", "hello", null, null, null, null, null, null, null);
        assertEquals(1, result.totalElements()); assertEquals(42L, result.items().getFirst().id());
        verify(repository).findAll(any(Specification.class), any(Pageable.class));
    }
    @Test
    void rejectsUnsafePaginationAndSorting() {
        EvidenceService service = new EvidenceService(mock(EvidenceRepository.class));
        assertThrows(ResponseStatusException.class, () -> service.listEvidence(0, 101, "id", "asc", null, null, null, null, null, null, null, null));
        assertThrows(ResponseStatusException.class, () -> service.listEvidence(0, 50, "title", "asc", null, null, null, null, null, null, null, null));
    }
}
