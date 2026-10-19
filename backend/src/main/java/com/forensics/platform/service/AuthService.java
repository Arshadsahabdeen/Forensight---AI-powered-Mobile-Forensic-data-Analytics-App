package com.forensics.platform.service;

import com.forensics.platform.dto.AuthDtos.*;
import com.forensics.platform.entity.User;
import com.forensics.platform.exception.AccountLockedException;
import com.forensics.platform.exception.RefreshTokenException;
import com.forensics.platform.entity.RoleName;
import com.forensics.platform.repository.UserRepository;
import com.forensics.platform.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.stream.Collectors;

@Service
public class AuthService {
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final RefreshTokenService refreshTokenService;
    private final long lockDurationMs;
    private java.util.function.LongSupplier idleTimeoutMinutes;

    /** Supplies the server-side idle limit (minutes) enforced on refresh; null disables the check. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setPlatformSettingsService(PlatformSettingsService settingsService) {
        this.idleTimeoutMinutes = settingsService == null ? null : settingsService::effectiveIdleTimeoutMinutes;
    }

    public AuthService(AuthenticationManager authenticationManager, UserDetailsService userDetailsService, UserRepository userRepository, JwtService jwtService, AuditService auditService, RefreshTokenService refreshTokenService, @Value("${app.security.account-lock-duration-ms}") long lockDurationMs) {
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.auditService = auditService;
        this.refreshTokenService = refreshTokenService;
        this.lockDurationMs = lockDurationMs;
    }

    @Transactional
    public LoginResponse login(@Valid LoginRequest request, HttpServletRequest httpRequest) {
        User user = userRepository.findByUsernameOrEmail(request.username(), request.username()).orElse(null);
        if (user != null) {
            unlockIfExpired(user);
            if (!user.isEnabled()) {
                auditService.record(user, "LOGIN_FAILED", "Login rejected for disabled account", httpRequest, false);
                throw new DisabledException("Account is disabled");
            }
            if (user.isTemporarilyLocked(Instant.now())) {
                auditService.record(user, "LOGIN_FAILED", "Login rejected for locked account", httpRequest, false);
                throw new AccountLockedException("Account is temporarily locked");
            }
        }
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (BadCredentialsException ex) {
            registerFailedLogin(user, request.username(), httpRequest);
            throw ex;
        }
        var details = userDetailsService.loadUserByUsername(request.username());
        User authenticatedUser = userRepository.findByUsername(details.getUsername()).orElseThrow();
        authenticatedUser.setFailedLoginAttempts(0);
        authenticatedUser.setAccountLocked(false);
        authenticatedUser.setLockedUntil(null);
        authenticatedUser.setLastLoginAt(Instant.now());
        authenticatedUser.setUpdatedAt(Instant.now());
        var refresh = refreshTokenService.issue(authenticatedUser, clientIp(httpRequest), httpRequest.getHeader("User-Agent"));
        auditService.record(authenticatedUser, "LOGIN_SUCCESS", "User authenticated successfully", httpRequest, true);
        return new LoginResponse(
                jwtService.generateToken(details),
                refresh.rawToken(),
                "Bearer",
                jwtService.getAccessExpirationSeconds(),
                toUserResponse(authenticatedUser)
        );
    }

    @Transactional(noRollbackFor = RefreshTokenException.class)
    public RefreshTokenResponse refresh(@Valid RefreshTokenRequest request, HttpServletRequest httpRequest) {
        var oldToken = refreshTokenService.validateAndRevoke(request.refreshToken());
        User user = oldToken.getUser();
        // Each refresh issues a new token, so the old token's creation time is the last time the session was used.
        if (idleTimeoutMinutes != null && oldToken.getCreatedAt() != null
                && oldToken.getCreatedAt().plus(java.time.Duration.ofMinutes(idleTimeoutMinutes.getAsLong())).isBefore(Instant.now())) {
            auditService.record(user, "SESSION_EXPIRED", "Session ended after exceeding the idle timeout", httpRequest, false);
            throw new RefreshTokenException("Session expired due to inactivity. Please sign in again.");
        }
        if (!user.isEnabled() || user.isTemporarilyLocked(Instant.now())) {
            throw new RefreshTokenException("Account is disabled or locked");
        }
        var details = userDetailsService.loadUserByUsername(user.getUsername());
        var newRefresh = refreshTokenService.issue(user, clientIp(httpRequest), httpRequest.getHeader("User-Agent"));
        auditService.record(user, "TOKEN_REFRESHED", "Refresh token rotated", httpRequest, true);
        return new RefreshTokenResponse(jwtService.generateToken(details), newRefresh.rawToken(), "Bearer", jwtService.getAccessExpirationSeconds());
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse currentUser(Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        UserResponse response = toUserResponse(user);
        return new CurrentUserResponse(response.id(), response.username(), response.fullName(), response.email(), response.roles(), response.updatedAt());
    }

    public void logout(String username, String refreshToken, HttpServletRequest request) {
        refreshTokenService.revoke(refreshToken);
        User user = username == null ? null : userRepository.findByUsername(username).orElse(null);
        auditService.record(user, "LOGOUT", "User session ended", request, true);
    }

    private void registerFailedLogin(User user, String login, HttpServletRequest request) {
        if (user == null) {
            auditService.record(login, "LOGIN_FAILED", "Failed login attempt", request, false);
            return;
        }
        user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
        user.setLastFailedLoginAt(Instant.now());
        if (user.getFailedLoginAttempts() >= 5) {
            user.setAccountLocked(true);
            user.setLockedUntil(Instant.now().plusMillis(lockDurationMs));
            auditService.record(user, "ACCOUNT_LOCKED", "Account locked after repeated failed login attempts", request, false);
        } else {
            auditService.record(user, "LOGIN_FAILED", "Failed login attempt", request, false);
        }
        user.setUpdatedAt(Instant.now());
    }

    private void unlockIfExpired(User user) {
        if (user.isAccountLocked() && user.getLockedUntil() != null && !user.getLockedUntil().isAfter(Instant.now())) {
            user.setAccountLocked(false);
            user.setLockedUntil(null);
            user.setFailedLoginAttempts(0);
        }
    }

    private UserResponse toUserResponse(User user) {
        var roles = user.getRoles().stream().map(role -> role.getName()).collect(Collectors.toSet());
        return new UserResponse(user.getId(), user.getUsername(), user.getFullName(), user.getEmail(), roles, user.getUpdatedAt());
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
