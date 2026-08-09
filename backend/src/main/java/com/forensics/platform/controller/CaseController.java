package com.forensics.platform.controller;

import com.forensics.platform.dto.CaseDtos.CaseDetailResponse;
import com.forensics.platform.dto.CaseDtos.CasePageResponse;
import com.forensics.platform.dto.CaseDtos.CaseRequest;
import com.forensics.platform.dto.CaseDtos.CaseResponse;
import com.forensics.platform.service.CaseService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cases")
public class CaseController {
    private final CaseService caseService;

    public CaseController(CaseService caseService) {
        this.caseService = caseService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','INVESTIGATOR','REVIEWER')")
    public CasePageResponse listCases(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "updatedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            Authentication authentication
    ) {
        return caseService.listCases(page, size, sortBy, sortDir, search, status, priority, authentication);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','INVESTIGATOR','REVIEWER')")
    public CaseDetailResponse getCase(@PathVariable Long id, Authentication authentication) {
        return caseService.getCase(id, authentication);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','INVESTIGATOR')")
    public CaseResponse createCase(@Valid @RequestBody CaseRequest request, Authentication authentication) {
        return caseService.createCase(request, authentication);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','INVESTIGATOR')")
    public CaseResponse updateCase(@PathVariable Long id, @Valid @RequestBody CaseRequest request, Authentication authentication) {
        return caseService.updateCase(id, request, authentication);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteCase(@PathVariable Long id, Authentication authentication) {
        caseService.deleteCase(id, authentication);
    }
}
