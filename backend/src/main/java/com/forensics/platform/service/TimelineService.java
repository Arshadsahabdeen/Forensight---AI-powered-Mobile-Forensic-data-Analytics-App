package com.forensics.platform.service;

import com.forensics.platform.dto.TimelineEventDto;
import com.forensics.platform.entity.Evidence;
import com.forensics.platform.repository.EvidenceRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class TimelineService {

    private final EvidenceRepository evidenceRepository;

    public TimelineService(EvidenceRepository evidenceRepository) {
        this.evidenceRepository = evidenceRepository;
    }

    public List<TimelineEventDto> getDeviceTimeline(Long deviceId) {

        return evidenceRepository
                .findByDeviceId(deviceId)
                .stream()
                .filter(e -> e.getEventTime() != null)
                .sorted(
                        Comparator.comparing(
                                Evidence::getEventTime
                        )
                )
                .map(this::toDto)
                .toList();
    }

    public List<TimelineEventDto> getAllTimeline() {

        return evidenceRepository
                .findAll()
                .stream()
                .filter(e -> e.getEventTime() != null)
                .sorted(
                        Comparator.comparing(
                                Evidence::getEventTime
                        )
                )
                .map(this::toDto)
                .toList();
    }

    private TimelineEventDto toDto(Evidence e) {

        return new TimelineEventDto(
                e.getId(),
                e.getArtifactType(),
                e.getTitle(),
                e.getContent(),
                e.getSource(),
                e.getTarget(),
                e.getPhoneNumber(),
                e.getContactName(),
                e.getLocation(),
                e.getLatitude(),
                e.getLongitude(),
                e.getEventTime(),
                e.getDevice() != null
                        ? e.getDevice().getId()
                        : null
        );
    }
}