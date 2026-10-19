package com.forensics.platform.dto;

import com.forensics.platform.entity.AcquisitionMethod;
import com.forensics.platform.entity.DeviceStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public class DeviceDtos {

    public record DeviceRequest(
            @NotNull Long caseId,
            @NotBlank @Size(max = 120) String manufacturer,
            @Size(max = 120) String brand,
            @NotBlank @Size(max = 120) String model,
            @NotBlank @Size(max = 160) String deviceName,
            @Size(max = 40)
            @Pattern(regexp = "^$|^[0-9]{14,16}$", message = "IMEI must be 14 to 16 digits")
            String imei,
            @Size(max = 120) String serialNumber,
            @NotBlank @Size(max = 120) String operatingSystem,
            @Size(max = 80) String osVersion,
            @Size(max = 80) String deviceType,
            @Size(max = 160) String ownerName,
            @Size(max = 160) String ownerContact,
            @Size(max = 120) String investigator,
            @NotNull AcquisitionMethod acquisitionMethod,
            @NotNull DeviceStatus acquisitionStatus,
            LocalDate seizedDate,
            LocalDate receivedDate,
            @Size(max = 4000) String notes
    ) {}

    public record DeviceResponse(
            Long id,
            Long caseId,
            String caseNumber,
            String manufacturer,
            String brand,
            String model,
            String deviceName,
            String imei,
            String serialNumber,
            String operatingSystem,
            String osVersion,
            String deviceType,
            String ownerName,
            String ownerContact,
            String investigator,
            AcquisitionMethod acquisitionMethod,
            DeviceStatus acquisitionStatus,
            LocalDate seizedDate,
            LocalDate receivedDate,
            String notes,
            String createdBy,
            String updatedBy,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record DeviceListItem(
            Long id,
            String deviceName,
            String manufacturer,
            String model,
            String imei,
            String operatingSystem,
            DeviceStatus acquisitionStatus,
            String investigator,
            Long caseId,
            String caseNumber,
            Instant updatedAt
    ) {}

    public record DevicePageResponse(
            List<DeviceListItem> items,
            long totalElements,
            int totalPages,
            int page,
            int size,
            boolean first,
            boolean last
    ) {}

    public record DeviceDetailResponse(
            DeviceResponse device,
            long evidenceCount,
            long reportCount,
            long timelineCount,
            long evidenceFileCount,
            boolean canEdit,
            boolean canDelete
    ) {}
}
