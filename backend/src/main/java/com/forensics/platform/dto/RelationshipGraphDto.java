package com.forensics.platform.dto;

import java.util.List;

public record RelationshipGraphDto(
        List<Node> nodes,
        List<Edge> edges
) {

    public record Node(
            String id,
            String label,
            String type
    ) {}

    public record Edge(
            String source,
            String target,
            String relationship,
            Long evidenceId
    ) {}
}