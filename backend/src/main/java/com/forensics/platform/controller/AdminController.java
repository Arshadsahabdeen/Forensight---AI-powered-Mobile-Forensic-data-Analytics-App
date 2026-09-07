package com.forensics.platform.controller;

import com.forensics.platform.dto.AuthDtos;
import com.forensics.platform.dto.SettingsDtos;
import com.forensics.platform.entity.RoleName;
import com.forensics.platform.entity.User;
import com.forensics.platform.repository.PlatformSettingRepository;
import com.forensics.platform.repository.RoleRepository;
import com.forensics.platform.repository.UserRepository;
import com.forensics.platform.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
public class AdminController {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuditService auditService;
    private final PlatformSettingRepository settingRepository;

    public AdminController(UserRepository userRepository, RoleRepository roleRepository, AuditService auditService, PlatformSettingRepository settingRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.auditService = auditService;
        this.settingRepository = settingRepository;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<AuthDtos.AdminUserResponse> users() {
        return userRepository.findAll().stream().map(this::toResponse).toList();
    }

    @PatchMapping("/{id}/status")
    @Transactional
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestBody AuthDtos.UserStatusRequest request, Authentication authentication, HttpServletRequest httpRequest) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();
        if (authentication.getName().equals(user.getUsername()) && (!request.enabled() || request.accountLocked())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "You cannot disable or lock your own account"));
        }
        user.setEnabled(request.enabled());
        user.setAccountLocked(request.accountLocked());
        if (!request.accountLocked()) user.setLockedUntil(null);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        auditService.record(authentication.getName(), "USER_STATUS_UPDATED", "Updated account status for " + user.getUsername(), httpRequest, true);
        return ResponseEntity.ok(toResponse(user));
    }

    @PatchMapping("/{id}/role")
    @Transactional
    public ResponseEntity<?> updateRole(@PathVariable Long id, @RequestBody AuthDtos.UserRoleRequest request, Authentication authentication, HttpServletRequest httpRequest) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();
        if (request.role() == null) return ResponseEntity.badRequest().body(Map.of("message", "Role is required"));
        if (authentication.getName().equals(user.getUsername()) && request.role() != RoleName.ADMIN) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "You cannot remove your own administrator role"));
        }
        if (user.getRoles().stream().anyMatch(role -> role.getName() == RoleName.ADMIN) && request.role() != RoleName.ADMIN && userRepository.countByRoles_Name(RoleName.ADMIN) <= 1) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "The platform must retain at least one administrator"));
        }
        user.setRoles(java.util.Set.of(roleRepository.findByName(request.role()).orElseThrow()));
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        auditService.record(authentication.getName(), "USER_ROLE_UPDATED", "Changed role for " + user.getUsername() + " to " + request.role(), httpRequest, true);
        return ResponseEntity.ok(toResponse(user));
    }

    @GetMapping("/settings")
    public SettingsDtos.PlatformSettingsResponse settings() {
        return new SettingsDtos.PlatformSettingsResponse(
                setting("auditRetentionDays", 365), setting("sessionTimeoutMinutes", 30),
                setting("loginAlertsEnabled", true), setting("maintenanceMode", false), setting("defaultPageSize", 25));
    }

    @PatchMapping("/settings")
    @Transactional
    public ResponseEntity<?> updateSettings(@RequestBody SettingsDtos.PlatformSettingsRequest request, Authentication authentication, HttpServletRequest httpRequest) {
        if (request.auditRetentionDays() < 30 || request.auditRetentionDays() > 3650 || request.sessionTimeoutMinutes() < 5 || request.sessionTimeoutMinutes() > 1440 || request.defaultPageSize() < 10 || request.defaultPageSize() > 100) {
            return ResponseEntity.badRequest().body(Map.of("message", "Settings are outside the supported range"));
        }
        saveSetting("auditRetentionDays", request.auditRetentionDays());
        saveSetting("sessionTimeoutMinutes", request.sessionTimeoutMinutes());
        saveSetting("loginAlertsEnabled", request.loginAlertsEnabled());
        saveSetting("maintenanceMode", request.maintenanceMode());
        saveSetting("defaultPageSize", request.defaultPageSize());
        auditService.record(authentication.getName(), "PLATFORM_SETTINGS_UPDATED", "Updated platform administration settings", httpRequest, true);
        return ResponseEntity.ok(request);
    }

    private int setting(String key, int fallback) { return Integer.parseInt(settingRepository.findBySettingKey(key).map(value -> value.getSettingValue()).orElse(String.valueOf(fallback))); }
    private boolean setting(String key, boolean fallback) { return Boolean.parseBoolean(settingRepository.findBySettingKey(key).map(value -> value.getSettingValue()).orElse(String.valueOf(fallback))); }
    private void saveSetting(String key, Object value) { settingRepository.findBySettingKey(key).ifPresentOrElse(setting -> setting.setSettingValue(String.valueOf(value)), () -> settingRepository.save(new com.forensics.platform.entity.PlatformSetting(key, String.valueOf(value)))); }

    private AuthDtos.AdminUserResponse toResponse(User user) {
        return new AuthDtos.AdminUserResponse(
                user.getId(), user.getUsername(), user.getFullName(), user.getEmail(),
                user.getRoles().stream().map(role -> role.getName()).collect(java.util.stream.Collectors.toSet()),
                user.isEnabled(), user.isAccountLocked(), user.getLastLoginAt(), user.getCreatedAt(), user.getUpdatedAt()
        );
    }
}
