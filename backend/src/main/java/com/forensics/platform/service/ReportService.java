package com.forensics.platform.service;

import com.forensics.platform.dto.*;
import com.forensics.platform.entity.Device;
import com.forensics.platform.entity.Evidence;
import com.forensics.platform.repository.DeviceRepository;
import com.forensics.platform.repository.EvidenceRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ReportService {

    private final DeviceRepository deviceRepository;
    private final EvidenceRepository evidenceRepository;
    private final TimelineService timelineService;
    private final RelationshipGraphService relationshipGraphService;
    private final AIAnalysisService aiAnalysisService;

    public ReportService(
            DeviceRepository deviceRepository,
            EvidenceRepository evidenceRepository,
            TimelineService timelineService,
            RelationshipGraphService relationshipGraphService,
            AIAnalysisService aiAnalysisService
    ) {
        this.deviceRepository = deviceRepository;
        this.evidenceRepository = evidenceRepository;
        this.timelineService = timelineService;
        this.relationshipGraphService = relationshipGraphService;
        this.aiAnalysisService = aiAnalysisService;
    }

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

        Map<String, Object> deviceData =
                new LinkedHashMap<>();

        deviceData.put("id", device.getId());
        deviceData.put("deviceName", device.getDeviceName());
        deviceData.put("manufacturer", device.getManufacturer());
        deviceData.put("brand", device.getBrand());
        deviceData.put("model", device.getModel());
        deviceData.put("imei", device.getImei());
        deviceData.put(
                "operatingSystem",
                device.getOperatingSystem()
        );

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

        summary.put("byArtifactType", byType);

        List<?> timeline =
                timelineService.getDeviceTimeline(deviceId);

        RelationshipGraphDto graph =
                relationshipGraphService
                        .buildDeviceGraph(deviceId);

        AIAnalysisResponse ai =
                aiAnalysisService.analyzeDevice(deviceId);

        return new InvestigationReportDto(
                deviceId,
                deviceData,
                summary,
                timeline,
                graph,
                ai
        );
    }
}