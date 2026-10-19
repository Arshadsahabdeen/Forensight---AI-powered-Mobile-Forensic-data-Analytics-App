package com.forensics.platform.mapper;

import com.forensics.platform.dto.DeviceDtos.DeviceListItem;
import com.forensics.platform.dto.DeviceDtos.DeviceRequest;
import com.forensics.platform.dto.DeviceDtos.DeviceResponse;
import com.forensics.platform.entity.Device;
import com.forensics.platform.entity.InvestigationCase;
import org.springframework.stereotype.Component;

@Component
public class DeviceMapper {

    public Device toEntity(DeviceRequest request, InvestigationCase investigationCase) {
        Device device = new Device();
        applyRequest(device, request, investigationCase);
        return device;
    }

    public void applyRequest(Device device, DeviceRequest request, InvestigationCase investigationCase) {
        device.setInvestigationCase(investigationCase);
        device.setManufacturer(trim(request.manufacturer()));
        device.setBrand(trim(request.brand()));
        device.setModel(trim(request.model()));
        device.setDeviceName(trim(request.deviceName()));
        device.setImei(trim(request.imei()));
        device.setSerialNumber(trim(request.serialNumber()));
        device.setOperatingSystem(trim(request.operatingSystem()));
        device.setOsVersion(trim(request.osVersion()));
        device.setDeviceType(trim(request.deviceType()));
        device.setOwnerName(trim(request.ownerName()));
        device.setOwnerContact(trim(request.ownerContact()));
        device.setInvestigator(trim(request.investigator()));
        device.setAcquisitionMethod(request.acquisitionMethod());
        device.setAcquisitionStatus(request.acquisitionStatus());
        device.setSeizedDate(request.seizedDate());
        device.setReceivedDate(request.receivedDate());
        device.setNotes(trim(request.notes()));
    }

    public DeviceResponse toResponse(Device device) {
        var investigationCase = device.getInvestigationCase();
        return new DeviceResponse(
                device.getId(),
                investigationCase == null ? null : investigationCase.getId(),
                investigationCase == null ? null : investigationCase.getCaseNumber(),
                device.getManufacturer(),
                device.getBrand(),
                device.getModel(),
                device.getDeviceName(),
                device.getImei(),
                device.getSerialNumber(),
                device.getOperatingSystem(),
                device.getOsVersion(),
                device.getDeviceType(),
                device.getOwnerName(),
                device.getOwnerContact(),
                device.getInvestigator(),
                device.getAcquisitionMethod(),
                device.getAcquisitionStatus(),
                device.getSeizedDate(),
                device.getReceivedDate(),
                device.getNotes(),
                device.getCreatedBy(),
                device.getUpdatedBy(),
                device.getCreatedAt(),
                device.getUpdatedAt()
        );
    }

    public DeviceListItem toListItem(Device device) {
        var investigationCase = device.getInvestigationCase();
        return new DeviceListItem(
                device.getId(),
                device.getDeviceName(),
                device.getManufacturer(),
                device.getModel(),
                device.getImei(),
                device.getOperatingSystem(),
                device.getAcquisitionStatus(),
                device.getInvestigator(),
                investigationCase == null ? null : investigationCase.getId(),
                investigationCase == null ? null : investigationCase.getCaseNumber(),
                device.getUpdatedAt()
        );
    }

    /** Trims text and stores blanks as null (so optional unique fields such as IMEI never collide on ""). */
    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
