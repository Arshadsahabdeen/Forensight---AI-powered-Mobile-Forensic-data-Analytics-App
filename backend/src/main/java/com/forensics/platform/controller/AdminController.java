package com.forensics.platform.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
public class AdminController {
    @GetMapping
    public Map<String, Object> usersPlaceholder() {
        return Map.of(
                "status", "UNDER_DEVELOPMENT",
                "message", "User management is reserved for administrators.",
                "timestamp", Instant.now()
        );
    }
}
