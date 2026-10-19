package com.forensics.platform.controller;

import com.forensics.platform.dto.AuthDtos;
import com.forensics.platform.entity.RoleName;
import com.forensics.platform.repository.UserRepository;
import com.forensics.platform.service.PlatformSettingsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Minimal, non-sensitive data every signed-in user needs: names of investigators (for case assignment)
 * and the enforced session policy. No email addresses or account states are exposed here.
 */
@RestController
public class DirectoryController {

    private final UserRepository userRepository;
    private final PlatformSettingsService settingsService;

    public DirectoryController(UserRepository userRepository, PlatformSettingsService settingsService) {
        this.userRepository = userRepository;
        this.settingsService = settingsService;
    }

    @GetMapping("/api/v1/directory/investigators")
    @PreAuthorize("hasAnyRole('ADMIN','INVESTIGATOR','REVIEWER')")
    public List<AuthDtos.DirectoryUser> investigators() {
        return userRepository.findAll().stream()
                .filter(user -> user.isEnabled() && user.getRoles().stream().anyMatch(role -> role.getName() == RoleName.INVESTIGATOR))
                .sorted(Comparator.comparing(user -> user.getFullName().toLowerCase()))
                .map(user -> new AuthDtos.DirectoryUser(user.getUsername(), user.getFullName(),
                        user.getRoles().stream().map(role -> role.getName()).collect(Collectors.toSet())))
                .toList();
    }

    @GetMapping("/api/v1/platform/policy")
    @PreAuthorize("isAuthenticated()")
    public AuthDtos.SessionPolicy policy() {
        var settings = settingsService.get();
        return new AuthDtos.SessionPolicy(settings.sessionTimeoutMinutes(), settingsService.effectiveIdleTimeoutMinutes(),
                settingsService.accessTokenMinutes(), settings.maintenanceMode(), settings.defaultPageSize());
    }

    @GetMapping("/api/v1/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
