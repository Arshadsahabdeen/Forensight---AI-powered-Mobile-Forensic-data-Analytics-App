package com.forensics.platform.dto;

import com.forensics.platform.entity.RoleName;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

public class AuthDtos {
    /** At least 10 characters with an upper-case letter, a lower-case letter and a digit. */
    public static final String PASSWORD_POLICY = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{10,128}$";
    public static final String PASSWORD_MESSAGE = "Password must be 10-128 characters and include upper-case, lower-case and a digit";

    public record LoginRequest(@NotBlank @Size(max = 255) String username, @NotBlank @Size(max = 255) String password) {}
    public record UserResponse(Long id, String username, String fullName, String email, Set<RoleName> roles, java.time.Instant updatedAt) {}
    public record AdminUserResponse(Long id, String username, String fullName, String email, Set<RoleName> roles, boolean enabled, boolean accountLocked, java.time.Instant lastLoginAt, java.time.Instant createdAt, java.time.Instant updatedAt) {}
    public record UserStatusRequest(boolean enabled, boolean accountLocked) {}
    public record UserRoleRequest(RoleName role) {}
    public record CreateUserRequest(
            @NotBlank @Size(min = 3, max = 60) @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Use letters, digits, '.', '_' or '-' only") String username,
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Pattern(regexp = PASSWORD_POLICY, message = PASSWORD_MESSAGE) String password,
            @NotNull RoleName role
    ) {}
    public record ResetPasswordRequest(@NotBlank @Pattern(regexp = PASSWORD_POLICY, message = PASSWORD_MESSAGE) String newPassword) {}
    public record LoginResponse(String accessToken, String refreshToken, String tokenType, long expiresIn, UserResponse user) {}
    public record RefreshTokenRequest(@NotBlank String refreshToken) {}
    public record RefreshTokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {}
    public record LogoutRequest(String refreshToken) {}
    public record CurrentUserResponse(Long id, String username, String fullName, String email, Set<RoleName> roles, java.time.Instant updatedAt) {}
    public record DirectoryUser(String username, String fullName, Set<RoleName> roles) {}
    public record SessionPolicy(int sessionTimeoutMinutes, long serverIdleTimeoutMinutes, long accessTokenMinutes, boolean maintenanceMode, int defaultPageSize) {}
}
