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
import com.forensics.platform.repository.DeviceRepository;
import com.forensics.platform.repository.EvidenceRepository;
import com.forensics.platform.repository.EvidenceSourceFileRepository;
import com.forensics.platform.repository.ReportRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@Transactional
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final EvidenceRepository evidenceRepository;
    private final EvidenceSourceFileRepository fileRepository;
    private final ReportRepository reportRepository;
    private final DeviceMapper deviceMapper;
    private final AuditService auditService;
    private final CaseAccessService caseAccessService;

    public DeviceService(DeviceRepository deviceRepository, EvidenceRepository evidenceRepository, EvidenceSourceFileRepository fileRepository,
                         ReportRepository reportRepository, DeviceMapper deviceMapper, AuditService auditService,
                         CaseAccessService caseAccessService) {
        this.deviceRepository = deviceRepository;
        this.evidenceRepository = evidenceRepository;
        this.fileRepository = fileRepository;
        this.reportRepository = reportRepository;
        this.deviceMapper = deviceMapper;
        this.auditService = auditService;
        this.caseAccessService = caseAccessService;
    }

    @Transactional(readOnly = true)
    public DevicePageResponse listDevices(int page, int size, String sortBy, String sortDir, String search, String status,
                                          String operatingSystem, Long caseId, Authentication authentication) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(size, 1), 100), sort(sortBy, sortDir));
        DeviceStatus statusFilter = parseStatus(status);
        if (caseId != null) {
            caseAccessService.requireViewable(caseId, authentication);
        }

        Specification<Device> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            jakarta.persistence.criteria.Join<Device, InvestigationCase> investigationCase =
                    root.join("investigationCase", jakarta.persistence.criteria.JoinType.LEFT);
            if (caseAccessService.actor(authentication).admin()) {
                predicates.add(cb.conjunction());
            } else {
                // Non-admins never see devices that are not attached to a case they can view.
                predicates.add(cb.isNotNull(investigationCase.get("id")));
                predicates.add(caseAccessService.restrictToViewableCases(investigationCase, cb, authentication));
            }
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("deviceName")), like),
                        cb.like(cb.lower(root.get("manufacturer")), like),
                        cb.like(cb.lower(root.get("brand")), like),
                        cb.like(cb.lower(root.get("model")), like),
                        cb.like(cb.lower(root.get("imei")), like),
                        cb.like(cb.lower(root.get("serialNumber")), like),
                        cb.like(cb.lower(investigationCase.get("caseNumber")), like)
                ));
            }
            if (statusFilter != null) {
                predicates.add(cb.equal(root.get("acquisitionStatus"), statusFilter));
            }
            if (operatingSystem != null && !operatingSystem.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("operatingSystem")), operatingSystem.trim().toLowerCase(Locale.ROOT)));
            }
            if (caseId != null) {
                predicates.add(cb.equal(investigationCase.get("id"), caseId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Device> devices = deviceRepository.findAll(specification, pageable);
        List<DeviceListItem> items = devices.getContent().stream().map(deviceMapper::toListItem).toList();
        return new DevicePageResponse(items, devices.getTotalElements(), devices.getTotalPages(), devices.getNumber(),
                devices.getSize(), devices.isFirst(), devices.isLast());
    }

    @Transactional(readOnly = true)
    public DeviceDetailResponse getDevice(Long id, Authentication authentication) {
        Device device = requireDevice(id);
        assertCanView(device, authentication);
        long evidenceCount = evidenceRepository.countByDeviceId(id);
        long fileCount = fileRepository.countByDeviceId(id);
        long reportCount = device.getInvestigationCase() == null ? 0 : reportRepository.countByInvestigationCaseId(device.getInvestigationCase().getId());
        long timelineCount = evidenceRepository.countByDeviceIdAndEventTimeIsNotNull(id);
        CaseAccessService.Actor actor = caseAccessService.actor(authentication);
        boolean canEdit = actor.admin() || (device.getInvestigationCase() != null && caseAccessService.canEdit(device.getInvestigationCase(), actor));
        return new DeviceDetailResponse(deviceMapper.toResponse(device), evidenceCount, reportCount, timelineCount, fileCount,
                canEdit, actor.admin() && evidenceCount == 0 && fileCount == 0);
    }

    public DeviceResponse createDevice(DeviceRequest request, Authentication authentication) {
        InvestigationCase investigationCase = caseAccessService.requireEditable(request.caseId(), authentication);
        String imei = blankToNull(request.imei());
        if (imei != null && deviceRepository.existsByInvestigationCaseIdAndImeiIgnoreCase(investigationCase.getId(), imei)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A device with this IMEI is already registered in this case.");
        }
        Device device = deviceMapper.toEntity(request, investigationCase);
        device.setCreatedBy(authentication.getName());
        device.setUpdatedBy(authentication.getName());
        device = deviceRepository.save(device);
        auditService.record(new AuditService.Entry(null, authentication.getName(), "DEVICE_CREATED",
                "Registered device '" + device.getDeviceName() + "' in case " + investigationCase.getCaseNumber(),
                true, investigationCase.getId(), "DEVICE", String.valueOf(device.getId()), null));
        return deviceMapper.toResponse(device);
    }

    public DeviceResponse updateDevice(Long id, DeviceRequest request, Authentication authentication) {
        Device device = requireDevice(id);
        if (device.getInvestigationCase() != null) {
            caseAccessService.assertCanEdit(device.getInvestigationCase(), authentication);
        } else if (!caseAccessService.actor(authentication).admin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this device");
        }
        InvestigationCase targetCase = caseAccessService.requireEditable(request.caseId(), authentication);
        boolean caseChanged = device.getInvestigationCase() == null || !Objects.equals(device.getInvestigationCase().getId(), targetCase.getId());
        if (caseChanged && device.getInvestigationCase() != null
                && (evidenceRepository.countByDeviceId(id) > 0 || fileRepository.countByDeviceId(id) > 0)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A device with imported evidence cannot be moved to another case.");
        }
        String imei = blankToNull(request.imei());
        boolean imeiOrCaseChanged = caseChanged || !Objects.equals(imei == null ? null : imei.toLowerCase(Locale.ROOT),
                device.getImei() == null ? null : device.getImei().toLowerCase(Locale.ROOT));
        if (imei != null && imeiOrCaseChanged && deviceRepository.existsByInvestigationCaseIdAndImeiIgnoreCase(targetCase.getId(), imei)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A device with this IMEI is already registered in this case.");
        }
        deviceMapper.applyRequest(device, request, targetCase);
        device.setUpdatedBy(authentication.getName());
        device = deviceRepository.save(device);
        auditService.record(new AuditService.Entry(null, authentication.getName(), "DEVICE_UPDATED",
                "Updated device '" + device.getDeviceName() + "'", true, targetCase.getId(), "DEVICE", String.valueOf(id), null));
        return deviceMapper.toResponse(device);
    }

    /**
     * Only devices without evidence can be deleted; evidence and its source files are never removed implicitly.
     */
    public void deleteDevice(Long id, Authentication authentication) {
        if (!caseAccessService.actor(authentication).admin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only administrators can delete devices");
        }
        Device device = requireDevice(id);
        if (evidenceRepository.countByDeviceId(id) > 0 || fileRepository.countByDeviceId(id) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This device has imported evidence and cannot be deleted. Set its status to ARCHIVED instead.");
        }
        Long caseId = device.getInvestigationCase() == null ? null : device.getInvestigationCase().getId();
        deviceRepository.delete(device);
        auditService.record(new AuditService.Entry(null, authentication.getName(), "DEVICE_DELETED",
                "Deleted device '" + device.getDeviceName() + "' (no evidence was attached)", true, caseId, "DEVICE", String.valueOf(id), null));
    }

    private void assertCanView(Device device, Authentication authentication) {
        if (device.getInvestigationCase() == null) {
            if (!caseAccessService.actor(authentication).admin()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this device");
            }
            return;
        }
        caseAccessService.assertCanView(device.getInvestigationCase(), authentication);
    }

    private Device requireDevice(Long id) {
        return deviceRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Device not found"));
    }

    private DeviceStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return DeviceStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid device status filter");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Sort sort(String sortBy, String sortDir) {
        String property = switch (sortBy == null ? "updatedAt" : sortBy) {
            case "deviceName", "manufacturer", "brand", "model", "imei", "operatingSystem", "acquisitionStatus", "investigator", "createdAt" -> sortBy;
            default -> "updatedAt";
        };
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, property).and(Sort.by(Sort.Direction.ASC, "id"));
    }
}
