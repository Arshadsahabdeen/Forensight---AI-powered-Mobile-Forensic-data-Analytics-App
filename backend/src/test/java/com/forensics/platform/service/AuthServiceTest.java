package com.forensics.platform.service;

import com.forensics.platform.dto.AuthDtos.LoginRequest;
import com.forensics.platform.entity.RefreshToken;
import com.forensics.platform.entity.Role;
import com.forensics.platform.entity.RoleName;
import com.forensics.platform.entity.User;
import com.forensics.platform.exception.AccountLockedException;
import com.forensics.platform.repository.UserRepository;
import com.forensics.platform.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    private AuthenticationManager authenticationManager;
    private UserDetailsService userDetailsService;
    private UserRepository userRepository;
    private JwtService jwtService;
    private AuditService auditService;
    private RefreshTokenService refreshTokenService;
    private AuthService authService;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        authenticationManager = mock(AuthenticationManager.class);
        userDetailsService = mock(UserDetailsService.class);
        userRepository = mock(UserRepository.class);
        jwtService = mock(JwtService.class);
        auditService = mock(AuditService.class);
        refreshTokenService = mock(RefreshTokenService.class);
        authService = new AuthService(authenticationManager, userDetailsService, userRepository, jwtService, auditService, refreshTokenService, 900000);
        request = new MockHttpServletRequest();
    }

    @Test
    void loginReturnsAccessRefreshAndSafeUser() {
        User user = user("admin", RoleName.ADMIN);
        when(userRepository.findByUsernameOrEmail("admin", "admin")).thenReturn(Optional.of(user));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(userDetailsService.loadUserByUsername("admin")).thenReturn(org.springframework.security.core.userdetails.User.withUsername("admin").password("hash").roles("ADMIN").build());
        when(jwtService.generateToken(any())).thenReturn("access-token");
        when(jwtService.getAccessExpirationSeconds()).thenReturn(900L);
        when(refreshTokenService.issue(eq(user), any(), any())).thenReturn(new RefreshTokenService.IssuedRefreshToken("refresh-token", new RefreshToken()));

        var response = authService.login(new LoginRequest("admin", "Admin@123"), request);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.user().email()).isEqualTo("admin@forensics.local");
        assertThat(response.user().roles()).containsExactly(RoleName.ADMIN);
    }

    @Test
    void invalidCredentialsIncrementFailedAttempts() {
        User user = user("investigator", RoleName.INVESTIGATOR);
        when(userRepository.findByUsernameOrEmail("investigator", "investigator")).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("investigator", "wrong"), request))
                .isInstanceOf(BadCredentialsException.class);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        verify(auditService).record(eq(user), eq("LOGIN_FAILED"), anyString(), eq(request), eq(false));
    }

    @Test
    void disabledAccountIsRejectedBeforePasswordCheck() {
        User user = user("reviewer", RoleName.REVIEWER);
        user.setEnabled(false);
        when(userRepository.findByUsernameOrEmail("reviewer", "reviewer")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("reviewer", "Reviewer@123"), request))
                .isInstanceOf(DisabledException.class);

        verifyNoInteractions(authenticationManager);
    }

    @Test
    void accountLocksAfterFiveFailures() {
        User user = user("admin", RoleName.ADMIN);
        user.setFailedLoginAttempts(4);
        when(userRepository.findByUsernameOrEmail("admin", "admin")).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("admin", "wrong"), request))
                .isInstanceOf(BadCredentialsException.class);

        assertThat(user.isAccountLocked()).isTrue();
        assertThat(user.getLockedUntil()).isNotNull();
        verify(auditService).record(eq(user), eq("ACCOUNT_LOCKED"), anyString(), eq(request), eq(false));
    }

    @Test
    void activelyLockedAccountReturnsLockedError() {
        User user = user("admin", RoleName.ADMIN);
        user.setAccountLocked(true);
        user.setLockedUntil(java.time.Instant.now().plusSeconds(60));
        when(userRepository.findByUsernameOrEmail("admin", "admin")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("admin", "Admin@123"), request))
                .isInstanceOf(AccountLockedException.class);
    }

    private User user(String username, RoleName roleName) {
        Role role = new Role(roleName);
        User user = new User();
        user.setId(1L);
        user.setUsername(username);
        user.setEmail(username + "@forensics.local");
        user.setFullName("Test User");
        user.setPassword("$2a$10$hash");
        user.setRoles(Set.of(role));
        return user;
    }
}
