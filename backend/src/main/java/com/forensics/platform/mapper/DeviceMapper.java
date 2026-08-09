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

    public void applyRequest(
            Device device,
            DeviceRequest request,
            InvestigationCase investigationCase
    ) {

        device.setInvestigationCase(investigationCase);

        device.setManufacturer(request.manufacturer().trim());
        device.setBrand(request.brand().trim());
        device.setModel(request.model().trim());
        device.setDeviceName(request.deviceName().trim());

        device.setImei(request.imei().trim());

        device.setSerialNumber(request.serialNumber());

        device.setOperatingSystem(request.operatingSystem().trim());

        device.setOsVersion(request.osVersion());

        device.setDeviceType(request.deviceType());

        device.setOwnerName(request.ownerName());

        device.setOwnerContact(request.ownerContact());

        device.setInvestigator(request.investigator());

        device.setAcquisitionMethod(request.acquisitionMethod());

        device.setAcquisitionStatus(request.acquisitionStatus());

        device.setSeizedDate(request.seizedDate());

        device.setReceivedDate(request.receivedDate());

        device.setNotes(request.notes());

    }

    public DeviceResponse toResponse(Device device) {

        return new DeviceResponse(

                device.getId(),

                device.getInvestigationCase().getId(),

                device.getInvestigationCase().getCaseNumber(),

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

                device.getCreatedAt(),

                device.getUpdatedAt()

        );

    }

    public DeviceListItem toListItem(Device device) {

        return new DeviceListItem(

                device.getId(),

                device.getDeviceName(),

                device.getManufacturer(),

                device.getModel(),

                device.getImei(),

                device.getOperatingSystem(),

                device.getAcquisitionStatus(),

                device.getInvestigator(),

                device.getInvestigationCase().getCaseNumber(),

                device.getUpdatedAt()

        );

    }

}
