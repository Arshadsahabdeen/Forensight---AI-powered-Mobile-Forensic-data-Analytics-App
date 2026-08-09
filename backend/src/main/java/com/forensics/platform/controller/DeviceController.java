package com.forensics.platform.controller;

import com.forensics.platform.dto.DeviceDtos.DeviceDetailResponse;
import com.forensics.platform.dto.DeviceDtos.DevicePageResponse;
import com.forensics.platform.dto.DeviceDtos.DeviceRequest;
import com.forensics.platform.dto.DeviceDtos.DeviceResponse;
import com.forensics.platform.service.DeviceService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','INVESTIGATOR','REVIEWER')")
    public DevicePageResponse listDevices(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "updatedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String operatingSystem,
            @RequestParam(required = false) Long caseId,
            Authentication authentication
    ) {
        return deviceService.listDevices(
                page,
                size,
                sortBy,
                sortDir,
                search,
                status,
                operatingSystem,
                caseId,
                authentication
        );
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','INVESTIGATOR','REVIEWER')")
    public DeviceDetailResponse getDevice(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return deviceService.getDevice(id, authentication);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','INVESTIGATOR')")
    public DeviceResponse createDevice(
            @Valid @RequestBody DeviceRequest request,
            Authentication authentication
    ) {
        return deviceService.createDevice(request, authentication);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','INVESTIGATOR')")
    public DeviceResponse updateDevice(
            @PathVariable Long id,
            @Valid @RequestBody DeviceRequest request,
            Authentication authentication
    ) {
        return deviceService.updateDevice(
                id,
                request,
                authentication
        );
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteDevice(
            @PathVariable Long id,
            Authentication authentication
    ) {
        deviceService.deleteDevice(
                id,
                authentication
        );
    }
}