package com.forensics.platform.dto;

import java.util.List;
import java.util.Map;

public record InvestigationReportDto(

        Long deviceId,

        Map<String, Object> device,

        Map<String, Object> evidenceSummary,

        List<?> timeline,

        RelationshipGraphDto relationshipGraph,

        AIAnalysisResponse aiAnalysis

) {
}