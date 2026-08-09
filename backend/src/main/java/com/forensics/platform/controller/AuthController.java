package com.forensics.platform.controller;

import com.forensics.platform.dto.AuthDtos.*;
import com.forensics.platform.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return authService.login(request, httpRequest);
    }

    @PostMapping("/refresh")
    public RefreshTokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request, HttpServletRequest httpRequest) {
        return authService.refresh(request, httpRequest);
    }

    @GetMapping("/me")
    public CurrentUserResponse me(Authentication authentication) {
        return authService.currentUser(authentication);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(@RequestBody(required = false) LogoutRequest request, Authentication authentication, HttpServletRequest httpRequest) {
        authService.logout(authentication == null ? null : authentication.getName(), request == null ? null : request.refreshToken(), httpRequest);
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }
}
