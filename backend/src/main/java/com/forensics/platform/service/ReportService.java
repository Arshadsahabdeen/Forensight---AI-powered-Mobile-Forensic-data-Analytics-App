package com.forensics.platform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forensics.platform.dto.*;
import com.forensics.platform.entity.Device;
import com.forensics.platform.entity.Evidence;
import com.forensics.platform.entity.Report;
import com.forensics.platform.repository.DeviceRepository;
import com.forensics.platform.repository.EvidenceRepository;
import com.forensics.platform.repository.ReportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Year;
import java.util.*;

@Service
public class ReportService {

    private final DeviceRepository deviceRepository;
    private final EvidenceRepository evidenceRepository;
    private final ReportRepository reportRepository;
    private final TimelineService timelineService;
    private final RelationshipGraphService relationshipGraphService;
    private final AIAnalysisService aiAnalysisService;
    private final ObjectMapper objectMapper;

    public ReportService(
            DeviceRepository deviceRepository,
            EvidenceRepository evidenceRepository,
            ReportRepository reportRepository,
            TimelineService timelineService,
            RelationshipGraphService relationshipGraphService,
            AIAnalysisService aiAnalysisService,
            ObjectMapper objectMapper
    ) {
        this.deviceRepository = deviceRepository;
        this.evidenceRepository = evidenceRepository;
        this.reportRepository = reportRepository;
        this.timelineService = timelineService;
        this.relationshipGraphService = relationshipGraphService;
        this.aiAnalysisService = aiAnalysisService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public InvestigationReportDto generateReport(Long deviceId)
            throws Exception {

        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Device not found: " + deviceId
                        )
                );

        List<Evidence> evidence =
                evidenceRepository.findByDeviceId(deviceId);

        /*
         * DEVICE INFORMATION
         */

        Map<String, Object> deviceData =
                new LinkedHashMap<>();

        deviceData.put("id", device.getId());
        deviceData.put(
                "deviceName",
                device.getDeviceName()
        );
        deviceData.put(
                "manufacturer",
                device.getManufacturer()
        );
        deviceData.put(
                "brand",
                device.getBrand()
        );
        deviceData.put(
                "model",
                device.getModel()
        );
        deviceData.put(
                "imei",
                device.getImei()
        );
        deviceData.put(
                "operatingSystem",
                device.getOperatingSystem()
        );

        /*
         * EVIDENCE SUMMARY
         */

        Map<String, Object> summary =
                new LinkedHashMap<>();

        summary.put(
                "totalEvidence",
                evidence.size()
        );

        Map<String, Long> byType =
                new LinkedHashMap<>();

        for (Evidence e : evidence) {

            String type =
                    e.getArtifactType() == null
                            ? "UNKNOWN"
                            : e.getArtifactType();

            byType.put(
                    type,
                    byType.getOrDefault(type, 0L) + 1
            );
        }

        summary.put(
                "byArtifactType",
                byType
        );

        /*
         * TIMELINE
         */

        List<?> timeline =
                timelineService.getDeviceTimeline(deviceId);

        /*
         * RELATIONSHIP GRAPH
         */

        RelationshipGraphDto graph =
                relationshipGraphService
                        .buildDeviceGraph(deviceId);

        /*
         * AI ANALYSIS
         */

        AIAnalysisResponse ai =
                aiAnalysisService.analyzeDevice(deviceId);

        /*
         * CREATE STORED REPORT
         */

        Report report = new Report();

        report.setDeviceId(deviceId);

        report.setReportNumber(
                generateReportNumber()
        );

        report.setReportTitle(
                "Mobile Forensic Investigation Report"
        );

        report.setReportType(
                "DEVICE_INVESTIGATION"
        );

        report.setVersion("1.0");

        report.setStatus("GENERATED");

        report.setGeneratedAt(
                Instant.now()
        );

        report.setGeneratedBy(
                "AI FORENSIGHT"
        );

        report.setPreparedBy(
                "AI FORENSIGHT"
        );

        report.setReviewedBy(
                "Pending Review"
        );

        report.setApprovedBy(
                "Pending Approval"
        );

        report.setNotesHtml("");

        report.setAiSummary(
                safeJson(ai)
        );

        report.setTimelineSummary(
                safeJson(timeline)
        );

        report.setExtractedKeywords(
                safeJson(
                        ai == null
                                ? Collections.emptyList()
                                : ai.keywords()
                )
        );

        report.setNamedEntities(
                safeJson(
                        ai == null
                                ? Collections.emptyMap()
                                : ai.entities()
                )
        );

        report.setSuspiciousFindings(
                safeJson(
                        ai == null
                                ? Collections.emptyList()
                                : ai.findings()
                )
        );

        report.setRiskLevel(
                determineRiskLevel(ai)
        );

        /*
         * Link report to investigation case.
         */

        if (device.getInvestigationCase() != null) {

            report.setInvestigationCase(
                    device.getInvestigationCase()
            );
        }

        /*
         * SAVE PERMANENTLY
         */

        reportRepository.save(report);

        /*
         * RETURN COMPLETE REPORT
         */

        return new InvestigationReportDto(
                deviceId,
                deviceData,
                summary,
                timeline,
                graph,
                ai
        );
    }

    /*
     * ALL STORED REPORTS
     */

    @Transactional(readOnly = true)
    public List<Report> getAllReports() {

        return reportRepository
                .findAllByOrderByGeneratedAtDesc(
                        org.springframework.data.domain
                                .PageRequest.of(0, 100)
                )
                .getContent();
    }

    /*
     * REPORTS FOR DEVICE
     */

    @Transactional(readOnly = true)
    public List<Report> getReportsByDevice(
            Long deviceId
    ) {
        return reportRepository
                .findByDeviceIdOrderByGeneratedAtDesc(
                        deviceId
                );
    }

    /*
     * REPORTS FOR CASE
     */

    @Transactional(readOnly = true)
    public List<Report> getReportsByCase(
            Long caseId
    ) {
        return reportRepository
                .findByInvestigationCaseId(caseId);
    }

    /*
     * SINGLE STORED REPORT
     */

    @Transactional(readOnly = true)
    public Report getReport(Long reportId) {

        return reportRepository
                .findById(reportId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Report not found: " + reportId
                        )
                );
    }

    /*
     * DELETE REPORT
     */

    @Transactional
    public void deleteReport(Long reportId) {

        if (!reportRepository.existsById(reportId)) {
            throw new RuntimeException(
                    "Report not found: " + reportId
            );
        }

        reportRepository.deleteById(reportId);
    }

    /*
     * REPORT NUMBER
     */

    private String generateReportNumber() {

        long nextNumber =
                reportRepository.count() + 1;

        return String.format(
                "FR-%d-%06d",
                Year.now().getValue(),
                nextNumber
        );
    }

    /*
     * SAFE JSON
     */

    private String safeJson(Object value) {

        if (value == null) {
            return "null";
        }

        try {

            return objectMapper.writeValueAsString(
                    value
            );

        } catch (JsonProcessingException e) {

            return String.valueOf(value);
        }
    }

    /*
     * RISK LEVEL
     */

    private String determineRiskLevel(
            AIAnalysisResponse ai
    ) {

        if (ai == null) {
            return "UNKNOWN";
        }

        int findings =
                ai.findings() == null
                        ? 0
                        : ai.findings().size();

        if (findings >= 5) {
            return "HIGH";
        }

        if (findings >= 2) {
            return "MEDIUM";
        }

        return "LOW";
    }
}