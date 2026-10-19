package com.forensics.platform.controller;

import com.forensics.platform.dto.NormalizedEvidenceDtos.NormalizedEvidenceResponse;
import com.forensics.platform.entity.NormalizedEvidenceCategory;
import com.forensics.platform.repository.CaseRepository;
import com.forensics.platform.repository.DeviceRepository;
import com.forensics.platform.service.CaseAccessService;
import com.forensics.platform.service.EvidenceNormalizationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

/**
 * Normalized (derived) evidence views. Every endpoint is restricted to cases the caller may view.
 */
@RestController
@RequestMapping("/api/v1/normalized-evidence")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN','INVESTIGATOR','REVIEWER')")
public class NormalizedEvidenceController {

    private final EvidenceNormalizationService evidenceNormalizationService;
    private final DeviceRepository deviceRepository;
    private final CaseRepository caseRepository;
    private final CaseAccessService caseAccessService;

    public NormalizedEvidenceController(EvidenceNormalizationService evidenceNormalizationService, DeviceRepository deviceRepository,
                                        CaseRepository caseRepository, CaseAccessService caseAccessService) {
        this.evidenceNormalizationService = evidenceNormalizationService;
        this.deviceRepository = deviceRepository;
        this.caseRepository = caseRepository;
        this.caseAccessService = caseAccessService;
    }

    @GetMapping("/device/{deviceId}")
    public List<NormalizedEvidenceResponse> getByDeviceId(@PathVariable Long deviceId, Authentication authentication) {
        assertDeviceViewable(deviceId, authentication);
        return evidenceNormalizationService.getByDeviceId(deviceId);
    }

    @GetMapping("/{id}")
    public NormalizedEvidenceResponse getById(@PathVariable Long id, Authentication authentication) {
        NormalizedEvidenceResponse response = evidenceNormalizationService.getById(id);
        caseAccessService.requireViewable(response.caseId(), authentication);
        return response;
    }

    @GetMapping("/device/{deviceId}/category/{category}")
    public List<NormalizedEvidenceResponse> getByDeviceIdAndCategory(@PathVariable Long deviceId,
                                                                     @PathVariable NormalizedEvidenceCategory category,
                                                                     Authentication authentication) {
        assertDeviceViewable(deviceId, authentication);
        return evidenceNormalizationService.getByDeviceIdAndCategory(deviceId, category);
    }

    @GetMapping("/search")
    public List<NormalizedEvidenceResponse> searchByKeyword(@RequestParam String keyword, Authentication authentication) {
        var accessible = caseAccessService.accessibleCaseIds(authentication);
        List<NormalizedEvidenceResponse> results = evidenceNormalizationService.searchByKeyword(keyword);
        if (accessible.isEmpty()) {
            return results;
        }
        Set<Long> allowed = accessible.get();
        return results.stream().filter(item -> allowed.contains(item.caseId())).toList();
    }

    private void assertDeviceViewable(Long deviceId, Authentication authentication) {
        var device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Device not found"));
        if (device.getInvestigationCase() == null) {
            if (!caseAccessService.actor(authentication).admin()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this device");
            }
            return;
        }
        caseAccessService.requireViewable(device.getInvestigationCase().getId(), authentication);
    }
}
