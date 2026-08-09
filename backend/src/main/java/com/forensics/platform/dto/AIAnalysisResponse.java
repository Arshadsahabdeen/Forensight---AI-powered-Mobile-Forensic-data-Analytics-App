package com.forensics.platform.dto;

import java.util.List;
import java.util.Map;

public record AIAnalysisResponse(
        String status,
        String summary,
        Map<String, Object> entities,
        List<String> keywords,
        Map<String, Object> statistics,
        List<String> findings
) {
}