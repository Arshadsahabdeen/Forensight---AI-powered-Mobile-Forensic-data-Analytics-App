package com.forensics.platform.ufdr;

import com.forensics.platform.entity.AcquisitionMethod;
import com.forensics.platform.entity.Device;
import com.forensics.platform.entity.DeviceStatus;
import com.forensics.platform.entity.EvidenceSourceFile;
import com.forensics.platform.entity.InvestigationCase;
import com.forensics.platform.repository.DeviceRepository;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Decides which device an imported evidence file belongs to.
 * <ol>
 *   <li>A device chosen at upload time is always used.</li>
 *   <li>Otherwise a device in the same case with the same IMEI is reused.</li>
 *   <li>Otherwise a new device is registered in the case from the file's device metadata
 *       (the same handset may also be registered in other cases; IMEI is unique per case).</li>
 * </ol>
 * Devices are never moved between cases, and existing device details are never overwritten.
 */
@Service
public class UFDRImportService {

    private final DeviceRepository deviceRepository;

    public UFDRImportService(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    public record Resolution(Device device, boolean created) {}

    public static class DeviceConflictException extends RuntimeException {
        public DeviceConflictException(String message) {
            super(message);
        }
    }

    public Resolution resolve(EvidenceSourceFile file, EvidenceExtractor.DeviceInfo info, String actor) {
        InvestigationCase investigationCase = file.getInvestigationCase();
        if (file.getDevice() != null) {
            if (!Objects.equals(file.getDevice().getInvestigationCase().getId(), investigationCase.getId())) {
                throw new DeviceConflictException("The selected device belongs to a different case.");
            }
            return new Resolution(file.getDevice(), false);
        }

        String imei = blankToNull(info.imei());
        if (imei != null) {
            var existing = deviceRepository.findByInvestigationCaseIdAndImei(investigationCase.getId(), imei);
            if (existing.isPresent()) {
                return new Resolution(existing.get(), false);
            }
        }

        Device device = new Device();
        device.setInvestigationCase(investigationCase);
        device.setManufacturer(blankToNull(info.manufacturer()));
        device.setBrand(blankToNull(info.brand()));
        device.setModel(blankToNull(info.model()));
        device.setImei(imei);
        device.setSerialNumber(blankToNull(info.serialNumber()));
        device.setOperatingSystem(info.operatingSystem() == null || info.operatingSystem().isBlank() ? "Unknown" : info.operatingSystem().trim());
        device.setOsVersion(blankToNull(info.osVersion()));
        device.setDeviceName(buildDeviceName(info, file.getOriginalFileName()));
        device.setAcquisitionMethod(file.getSourceFormat() == EvidenceSourceFile.SourceFormat.UFDR
                ? AcquisitionMethod.IMPORTED_UFDR : AcquisitionMethod.OTHER);
        device.setAcquisitionStatus(DeviceStatus.EXTRACTION_COMPLETED);
        device.setCreatedBy(actor);
        device.setUpdatedBy(actor);
        device.setNotes("Registered automatically from evidence file '" + file.getOriginalFileName() + "' (SHA-256 " + file.getSha256() + ").");
        return new Resolution(deviceRepository.save(device), true);
    }

    private static String buildDeviceName(EvidenceExtractor.DeviceInfo info, String fileName) {
        String name = ((info.brand() == null ? "" : info.brand().trim()) + " " + (info.model() == null ? "" : info.model().trim())).trim();
        if (!name.isBlank()) {
            return truncate(name, 160);
        }
        return truncate("Imported device (" + fileName + ")", 160);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String truncate(String value, int max) {
        return value.length() > max ? value.substring(0, max) : value;
    }
}
