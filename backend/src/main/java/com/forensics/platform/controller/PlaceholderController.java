package com.forensics.platform.controller;

import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class PlaceholderController {
    @GetMapping({"/devices", "/evidence", "/timeline", "/audit", "/ai"})
    public Map<String, Object> placeholder() {
        return Map.of(
                "status", "UNDER_DEVELOPMENT",
                "message", "This forensic module is routed and secured for later milestones.",
                "timestamp", Instant.now()
        );
    }
}
