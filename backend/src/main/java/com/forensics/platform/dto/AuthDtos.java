package com.forensics.platform.dto;

import com.forensics.platform.entity.RoleName;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;

public class AuthDtos {
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record UserResponse(Long id, String username, String fullName, String email, Set<RoleName> roles, java.time.Instant updatedAt) {}
    public record LoginResponse(String accessToken, String refreshToken, String tokenType, long expiresIn, UserResponse user) {}
    public record RefreshTokenRequest(@NotBlank String refreshToken) {}
    public record RefreshTokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {}
    public record LogoutRequest(String refreshToken) {}
    public record CurrentUserResponse(Long id, String username, String fullName, String email, Set<RoleName> roles, java.time.Instant updatedAt) {}
}
