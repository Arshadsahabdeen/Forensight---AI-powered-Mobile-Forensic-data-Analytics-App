package com.forensics.platform.controller;

import com.forensics.platform.dto.AuthDtos;
import com.forensics.platform.dto.SettingsDtos;
import com.forensics.platform.entity.RoleName;
import com.forensics.platform.entity.User;
import com.forensics.platform.repository.RefreshTokenRepository;
import com.forensics.platform.repository.RoleRepository;
import com.forensics.platform.repository.UserRepository;
import com.forensics.platform.service.AuditService;
import com.forensics.platform.service.PlatformSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Administrator-only account and platform administration. Accounts are never deleted (audit
 * attribution must remain resolvable); they are disabled instead.
 */
@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditService auditService;
    private final PlatformSettingsService settingsService;
    private final PasswordEncoder passwordEncoder;

    public AdminController(UserRepository userRepository, RoleRepository roleRepository, RefreshTokenRepository refreshTokenRepository,
                           AuditService auditService, PlatformSettingsService settingsService, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.auditService = auditService;
        this.settingsService = settingsService;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public List<AuthDtos.AdminUserResponse> users() {
        return userRepository.findAll().stream()
                .sorted(Comparator.comparing(User::getUsername))
                .map(this::toResponse).toList();
    }

    @PostMapping
    @Transactional
    public ResponseEntity<AuthDtos.AdminUserResponse> create(@Valid @RequestBody AuthDtos.CreateUserRequest request, Authentication authentication,
                                                             HttpServletRequest httpRequest) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.findByUsername(username).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username is already in use");
        }
        if (userRepository.findByEmail(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already in use");
        }
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setFullName(request.fullName().trim());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRoles(Set.of(roleRepository.findByName(request.role()).orElseThrow()));
        user = userRepository.save(user);
        auditService.record(authentication.getName(), "USER_CREATED", "Created account " + username + " with role " + request.role(), httpRequest, true);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(user));
    }

    @PostMapping("/{id}/password")
    @Transactional
    public AuthDtos.AdminUserResponse resetPassword(@PathVariable Long id, @Valid @RequestBody AuthDtos.ResetPasswordRequest request,
                                                    Authentication authentication, HttpServletRequest httpRequest) {
        User user = userRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setFailedLoginAttempts(0);
        user.setAccountLocked(false);
        user.setLockedUntil(null);
        user.setUpdatedAt(Instant.now());
        // Existing sessions end when the password changes.
        refreshTokenRepository.findByUserIdAndRevokedAtIsNull(user.getId()).forEach(token -> token.setRevokedAt(Instant.now()));
        auditService.record(authentication.getName(), "USER_PASSWORD_RESET", "Reset password for " + user.getUsername(), httpRequest, true);
        return toResponse(user);
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
        if (!request.accountLocked()) {
            user.setLockedUntil(null);
            user.setFailedLoginAttempts(0);
        }
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        auditService.record(authentication.getName(), "USER_STATUS_UPDATED", "Updated account status for " + user.getUsername()
                + " (enabled=" + request.enabled() + ", locked=" + request.accountLocked() + ")", httpRequest, true);
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
        user.setRoles(new java.util.HashSet<>(Set.of(roleRepository.findByName(request.role()).orElseThrow())));
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        auditService.record(authentication.getName(), "USER_ROLE_UPDATED", "Changed role for " + user.getUsername() + " to " + request.role(), httpRequest, true);
        return ResponseEntity.ok(toResponse(user));
    }

    @GetMapping("/settings")
    public SettingsDtos.PlatformSettingsResponse settings() {
        return settingsService.get();
    }

    @PatchMapping("/settings")
    public SettingsDtos.PlatformSettingsResponse updateSettings(@RequestBody SettingsDtos.PlatformSettingsRequest request, Authentication authentication,
                                                                HttpServletRequest httpRequest) {
        SettingsDtos.PlatformSettingsResponse updated = settingsService.update(request);
        auditService.record(authentication.getName(), "PLATFORM_SETTINGS_UPDATED", "Updated platform settings (sessionTimeoutMinutes="
                + updated.sessionTimeoutMinutes() + ", maintenanceMode=" + updated.maintenanceMode() + ")", httpRequest, true);
        return updated;
    }

    private AuthDtos.AdminUserResponse toResponse(User user) {
        return new AuthDtos.AdminUserResponse(
                user.getId(), user.getUsername(), user.getFullName(), user.getEmail(),
                user.getRoles().stream().map(role -> role.getName()).collect(Collectors.toSet()),
                user.isEnabled(), user.isAccountLocked(), user.getLastLoginAt(), user.getCreatedAt(), user.getUpdatedAt()
        );
    }
}
