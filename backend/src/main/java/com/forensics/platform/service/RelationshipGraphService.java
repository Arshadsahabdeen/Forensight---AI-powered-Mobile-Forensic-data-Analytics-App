package com.forensics.platform.service;

import com.forensics.platform.dto.RelationshipGraphDto;
import com.forensics.platform.entity.Evidence;
import com.forensics.platform.repository.EvidenceRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class RelationshipGraphService {

    private final EvidenceRepository evidenceRepository;

    public RelationshipGraphService(
            EvidenceRepository evidenceRepository
    ) {
        this.evidenceRepository = evidenceRepository;
    }

    public RelationshipGraphDto buildDeviceGraph(Long deviceId) {

        List<Evidence> evidence =
                evidenceRepository.findByDeviceId(deviceId);

        Map<String, RelationshipGraphDto.Node> nodes =
                new LinkedHashMap<>();

        List<RelationshipGraphDto.Edge> edges =
                new ArrayList<>();

        for (Evidence e : evidence) {

            String source = clean(e.getSource());
            String target = clean(e.getTarget());
            String phone = clean(e.getPhoneNumber());
            String contact = clean(e.getContactName());

            if (!source.isBlank()) {
                addNode(
                        nodes,
                        "PHONE:" + source,
                        source,
                        "PHONE"
                );
            }

            if (!target.isBlank()) {
                addNode(
                        nodes,
                        "PHONE:" + target,
                        target,
                        "PHONE"
                );
            }

            if (!contact.isBlank()) {
                addNode(
                        nodes,
                        "CONTACT:" + contact,
                        contact,
                        "PERSON"
                );
            }

            if (!phone.isBlank() && !contact.isBlank()) {

                edges.add(
                        new RelationshipGraphDto.Edge(
                                "CONTACT:" + contact,
                                "PHONE:" + phone,
                                "CONTACT_NUMBER",
                                e.getId()
                        )
                );
            }

            if (!source.isBlank() &&
                    !target.isBlank()) {

                String relationship =
                        switch (e.getArtifactType()
                                .toUpperCase()) {

                            case "SMS" ->
                                    "SMS";

                            case "CALL" ->
                                    "CALL";

                            default ->
                                    e.getArtifactType();
                        };

                edges.add(
                        new RelationshipGraphDto.Edge(
                                "PHONE:" + source,
                                "PHONE:" + target,
                                relationship,
                                e.getId()
                        )
                );
            }

            if (!phone.isBlank() &&
                    e.getArtifactType() != null &&
                    e.getArtifactType()
                            .equalsIgnoreCase("CALL")) {

                addNode(
                        nodes,
                        "PHONE:" + phone,
                        phone,
                        "PHONE"
                );
            }
        }

        return new RelationshipGraphDto(
                new ArrayList<>(nodes.values()),
                edges
        );
    }

    private void addNode(
            Map<String, RelationshipGraphDto.Node> nodes,
            String id,
            String label,
            String type
    ) {

        nodes.putIfAbsent(
                id,
                new RelationshipGraphDto.Node(
                        id,
                        label,
                        type
                )
        );
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}