package com.forensics.platform.ufdr;

import com.forensics.platform.entity.Device;
import com.forensics.platform.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class UFDRImportService {

    private final DeviceRepository deviceRepository;

    public Device importDevice(Map<String, Object> data) {

    Map<String, Object> deviceData =
            (Map<String, Object>) data.get("device");

    if (deviceData == null || deviceData.isEmpty()) {
        throw new IllegalArgumentException(
                "No device information found in UFDR file"
        );
    }

    String manufacturer =
            stringValue(deviceData.get("manufacturer"));

    String brand =
            stringValue(deviceData.get("brand"));

    String model =
            stringValue(deviceData.get("model"));

    String imei =
            stringValue(deviceData.get("imei"));

    String operatingSystem =
            stringValue(deviceData.get("operatingSystem"));

    /*
     * If this device already exists, reuse it.
     */
    if (imei != null && !imei.isBlank()) {

        var existing =
                deviceRepository.findByImei(imei);

        if (existing.isPresent()) {
            return existing.get();
        }
    }

    Device device = new Device();

    device.setManufacturer(manufacturer);
    device.setBrand(brand);
    device.setModel(model);
    device.setImei(imei);

    device.setDeviceName(
            buildDeviceName(brand, model)
    );

    device.setOperatingSystem(
            operatingSystem == null ||
            operatingSystem.isBlank()
                    ? "Unknown"
                    : operatingSystem
    );

    return deviceRepository.save(device);
}

    private String stringValue(Object value) {
        if (value == null) {
            return "";
        }

        return value.toString().trim();
    }

    private String buildDeviceName(String brand, String model) {

        String name = ((brand == null ? "" : brand) + " " +
                (model == null ? "" : model)).trim();

        return name.isBlank()
                ? "Imported Device"
                : name;
    }
}