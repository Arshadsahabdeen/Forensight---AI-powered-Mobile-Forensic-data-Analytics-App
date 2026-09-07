package com.forensics.platform.controller;

import com.forensics.platform.dto.NormalizedEvidenceDtos.NormalizedEvidenceResponse;
import com.forensics.platform.entity.NormalizedEvidenceCategory;
import com.forensics.platform.service.EvidenceNormalizationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/normalized-evidence")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class NormalizedEvidenceController {

    private final EvidenceNormalizationService evidenceNormalizationService;

    @GetMapping("/device/{deviceId}")
    @PreAuthorize("hasAnyRole('ADMIN','INVESTIGATOR','REVIEWER')")
    public List<NormalizedEvidenceResponse> getByDeviceId(@PathVariable Long deviceId) {
        return evidenceNormalizationService.getByDeviceId(deviceId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','INVESTIGATOR','REVIEWER')")
    public NormalizedEvidenceResponse getById(@PathVariable Long id) {
        return evidenceNormalizationService.getById(id);
    }

    @GetMapping("/device/{deviceId}/category/{category}")
    @PreAuthorize("hasAnyRole('ADMIN','INVESTIGATOR','REVIEWER')")
    public List<NormalizedEvidenceResponse> getByDeviceIdAndCategory(
            @PathVariable Long deviceId,
            @PathVariable NormalizedEvidenceCategory category
    ) {
        return evidenceNormalizationService.getByDeviceIdAndCategory(deviceId, category);
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN','INVESTIGATOR','REVIEWER')")
    public List<NormalizedEvidenceResponse> searchByKeyword(@RequestParam String keyword) {
        return evidenceNormalizationService.searchByKeyword(keyword);
    }
}
