package com.forensics.platform.service;

import com.forensics.platform.dto.DeviceDtos.DeviceDetailResponse;
import com.forensics.platform.dto.DeviceDtos.DeviceListItem;
import com.forensics.platform.dto.DeviceDtos.DevicePageResponse;
import com.forensics.platform.dto.DeviceDtos.DeviceRequest;
import com.forensics.platform.dto.DeviceDtos.DeviceResponse;
import com.forensics.platform.entity.Device;
import com.forensics.platform.entity.DeviceStatus;
import com.forensics.platform.entity.InvestigationCase;
import com.forensics.platform.mapper.DeviceMapper;
import com.forensics.platform.repository.CaseRepository;
import com.forensics.platform.repository.DeviceRepository;
import com.forensics.platform.repository.EvidenceRepository;
import com.forensics.platform.repository.ReportRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final CaseRepository caseRepository;
    private final EvidenceRepository evidenceRepository;
    private final ReportRepository reportRepository;
    private final DeviceMapper deviceMapper;
    private final AuditService auditService;

    public DeviceService(
            DeviceRepository deviceRepository,
            CaseRepository caseRepository,
            EvidenceRepository evidenceRepository,
            ReportRepository reportRepository,
            DeviceMapper deviceMapper,
            AuditService auditService
    ) {
        this.deviceRepository = deviceRepository;
        this.caseRepository = caseRepository;
        this.evidenceRepository = evidenceRepository;
        this.reportRepository = reportRepository;
        this.deviceMapper = deviceMapper;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public DevicePageResponse listDevices(
            int page,
            int size,
            String sortBy,
            String sortDir,
            String search,
            String status,
            String operatingSystem,
            Long caseId,
            Authentication authentication
    ) {

        Sort sort = Sort.by(
                "desc".equalsIgnoreCase(sortDir)
                        ? Sort.Direction.DESC
                        : Sort.Direction.ASC,
                sortBy
        );

        Pageable pageable = PageRequest.of(page, size, sort);

        Specification<Device> specification = (root, query, cb) -> {

            List<Predicate> predicates = new ArrayList<>();

            if (search != null && !search.isBlank()) {

                String like = "%" + search.toLowerCase() + "%";

                predicates.add(
                        cb.or(
                                cb.like(cb.lower(root.get("deviceName")), like),
                                cb.like(cb.lower(root.get("manufacturer")), like),
                                cb.like(cb.lower(root.get("brand")), like),
                                cb.like(cb.lower(root.get("model")), like),
                                cb.like(cb.lower(root.get("imei")), like)
                        )
                );
            }

            if (status != null && !status.isBlank()) {
                predicates.add(
                        cb.equal(
                                root.get("acquisitionStatus"),
                                DeviceStatus.valueOf(status)
                        )
                );
            }

            if (operatingSystem != null && !operatingSystem.isBlank()) {
                predicates.add(
                        cb.equal(
                                cb.lower(root.get("operatingSystem")),
                                operatingSystem.toLowerCase()
                        )
                );
            }

            if (caseId != null) {
                predicates.add(
                        cb.equal(
                                root.get("investigationCase").get("id"),
                                caseId
                        )
                );
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Device> devices =
                deviceRepository.findAll(specification, pageable);

        List<DeviceListItem> items =
                devices.getContent()
                        .stream()
                        .map(deviceMapper::toListItem)
                        .toList();

        return new DevicePageResponse(
                items,
                devices.getTotalElements(),
                devices.getTotalPages(),
                devices.getNumber(),
                devices.getSize(),
                devices.isFirst(),
                devices.isLast()
        );
    }
    @Transactional(readOnly = true)
public DeviceDetailResponse getDevice(Long id, Authentication authentication) {

    Device device = deviceRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Device not found"
            ));

    DeviceResponse response = deviceMapper.toResponse(device);

    long evidenceCount = evidenceRepository.countByDeviceId(id);

    long reportCount = reportRepository.countByInvestigationCaseId(
            device.getInvestigationCase().getId()
    );

    long timelineCount = 0;

    return new DeviceDetailResponse(
            response,
            evidenceCount,
            reportCount,
            timelineCount
    );
}

public DeviceResponse createDevice(
        DeviceRequest request,
        Authentication authentication
) {

    if (deviceRepository.existsByImeiIgnoreCase(request.imei())) {

        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "IMEI already exists."
        );
    }

    InvestigationCase investigationCase =
            caseRepository.findById(request.caseId())
                    .orElseThrow(() ->
                            new ResponseStatusException(
                                    HttpStatus.NOT_FOUND,
                                    "Case not found."
                            ));

    Device device =
            deviceMapper.toEntity(request, investigationCase);

    device.setCreatedBy(authentication.getName());
    device.setUpdatedBy(authentication.getName());

    device = deviceRepository.save(device);

    auditService.record(
            authentication.getName(),
            "DEVICE_CREATED",
            "Created device " + device.getDeviceName(),
            null,
            true
    );

    return deviceMapper.toResponse(device);
}
public DeviceResponse updateDevice(
        Long id,
        DeviceRequest request,
        Authentication authentication
) {

    Device device = deviceRepository.findById(id)
            .orElseThrow(() ->
                    new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Device not found."
                    ));

    if (!device.getImei().equalsIgnoreCase(request.imei())
            && deviceRepository.existsByImeiIgnoreCase(request.imei())) {

        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "IMEI already exists."
        );
    }

    InvestigationCase investigationCase =
            caseRepository.findById(request.caseId())
                    .orElseThrow(() ->
                            new ResponseStatusException(
                                    HttpStatus.NOT_FOUND,
                                    "Case not found."
                            ));

    deviceMapper.applyRequest(
            device,
            request,
            investigationCase
    );

    device.setUpdatedBy(authentication.getName());

    device = deviceRepository.save(device);

    auditService.record(
            authentication.getName(),
            "DEVICE_UPDATED",
            "Updated device " + device.getDeviceName(),
            null,
            true
    );

    return deviceMapper.toResponse(device);
}

public void deleteDevice(
        Long id,
        Authentication authentication
) {

    Device device = deviceRepository.findById(id)
            .orElseThrow(() ->
                    new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Device not found."
                    ));

    deviceRepository.delete(device);

    auditService.record(
            authentication.getName(),
            "DEVICE_DELETED",
            "Deleted device " + device.getDeviceName(),
            null,
            true
    );
}
}