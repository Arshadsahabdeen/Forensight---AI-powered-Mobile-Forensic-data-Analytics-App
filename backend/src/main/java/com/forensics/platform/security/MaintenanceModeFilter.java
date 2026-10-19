package com.forensics.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forensics.platform.service.PlatformSettingsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * While maintenance mode is enabled, only administrators may change data. Reads and sign-in keep working.
 * Registered inside the security filter chain (after authentication), not as a standalone servlet filter.
 */
public class MaintenanceModeFilter extends OncePerRequestFilter {

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final PlatformSettingsService settingsService;
    private final ObjectMapper objectMapper;

    public MaintenanceModeFilter(PlatformSettingsService settingsService, ObjectMapper objectMapper) {
        this.settingsService = settingsService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (WRITE_METHODS.contains(request.getMethod()) && !request.getRequestURI().startsWith("/api/v1/auth/")
                && !isAdmin() && settingsService.maintenanceMode()) {
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            response.setContentType("application/json");
            objectMapper.writeValue(response.getWriter(), Map.of(
                    "timestamp", Instant.now().toString(),
                    "status", 503,
                    "error", "Service Unavailable",
                    "message", "The platform is in maintenance mode. Changes are temporarily disabled.",
                    "path", request.getRequestURI()));
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isAdmin() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}
