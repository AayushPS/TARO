package org.Aayush.routing.topology;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Applies one typed v13 structural change set to source-level topology inputs.
 */
public final class StructuralChangeApplier {

    public TopologyModelSource apply(TopologyModelSource baseSource, StructuralChangeSet changeSet) {
        TopologyModelSource nonNullBaseSource = Objects.requireNonNull(baseSource, "baseSource");
        StructuralChangeSet nonNullChangeSet = Objects.requireNonNull(changeSet, "changeSet");
        nonNullBaseSource.validate();
        validateChangeSet(nonNullChangeSet);

        LinkedHashMap<Integer, TopologyModelSource.ProfileDefinition> profiles = indexProfiles(nonNullBaseSource.getProfiles());
        LinkedHashMap<String, TopologyModelSource.NodeDefinition> nodes = indexNodes(nonNullBaseSource.getNodes());
        LinkedHashMap<String, TopologyModelSource.EdgeDefinition> edges = indexEdges(nonNullBaseSource.getEdges());
        LinkedHashMap<TurnKey, TopologyModelSource.TurnCostDefinition> turnCosts =
                indexTurnCosts(nonNullBaseSource.getTurnCosts());

        boolean coordinatesEnabled = nonNullBaseSource.hasCoordinates();

        for (String removedNodeId : nonNullChangeSet.getRemovedNodes()) {
            if (nodes.remove(removedNodeId) == null) {
                throw new IllegalArgumentException("removed nodeId not present: " + removedNodeId);
            }
        }

        Set<String> removedEdgeIds = new LinkedHashSet<>(nonNullChangeSet.getRemovedEdges());
        for (String removedEdgeId : nonNullChangeSet.getRemovedEdges()) {
            if (!edges.containsKey(removedEdgeId)) {
                throw new IllegalArgumentException("removed edgeId not present: " + removedEdgeId);
            }
        }

        for (TopologyModelSource.EdgeDefinition edge : nonNullBaseSource.getEdges()) {
            if (!nodes.containsKey(edge.getOriginNodeId()) || !nodes.containsKey(edge.getDestinationNodeId())) {
                removedEdgeIds.add(edge.getEdgeId());
            }
        }

        removedEdgeIds.forEach(edges::remove);
        turnCosts.entrySet().removeIf(entry ->
                removedEdgeIds.contains(entry.getKey().fromEdgeId()) || removedEdgeIds.contains(entry.getKey().toEdgeId()));

        for (StructuralChangeSet.NodeAddition addedNode : nonNullChangeSet.getAddedNodes()) {
            if (nodes.containsKey(addedNode.getNodeId())) {
                throw new IllegalArgumentException("added nodeId already present: " + addedNode.getNodeId());
            }
            boolean hasX = addedNode.getX() != null;
            boolean hasY = addedNode.getY() != null;
            if (hasX != hasY) {
                throw new IllegalArgumentException("added node coordinates must provide both x and y: " + addedNode.getNodeId());
            }
            if (coordinatesEnabled != hasX) {
                throw new IllegalArgumentException(
                        "added node coordinates must match source coordinate posture: " + addedNode.getNodeId()
                );
            }
            nodes.put(addedNode.getNodeId(), TopologyModelSource.NodeDefinition.builder()
                    .nodeId(addedNode.getNodeId())
                    .x(addedNode.getX())
                    .y(addedNode.getY())
                    .build());
        }

        for (StructuralChangeSet.EdgeAddition addedEdge : nonNullChangeSet.getAddedEdges()) {
            if (edges.containsKey(addedEdge.getEdgeId())) {
                throw new IllegalArgumentException("added edgeId already present: " + addedEdge.getEdgeId());
            }
            if (!nodes.containsKey(addedEdge.getOriginNodeId())) {
                throw new IllegalArgumentException("added edge origin node missing: " + addedEdge.getOriginNodeId());
            }
            if (!nodes.containsKey(addedEdge.getDestinationNodeId())) {
                throw new IllegalArgumentException("added edge destination node missing: " + addedEdge.getDestinationNodeId());
            }
            edges.put(addedEdge.getEdgeId(), TopologyModelSource.EdgeDefinition.builder()
                    .edgeId(addedEdge.getEdgeId())
                    .originNodeId(addedEdge.getOriginNodeId())
                    .destinationNodeId(addedEdge.getDestinationNodeId())
                    .baseWeight(addedEdge.getBaseWeight())
                    .profileId(addedEdge.getProfileId())
                    .build());
        }

        for (StructuralChangeSet.CoordinateChange coordinateChange : nonNullChangeSet.getChangedCoordinates()) {
            if (!coordinatesEnabled) {
                throw new IllegalArgumentException("coordinate changes require a coordinate-enabled topology source");
            }
            TopologyModelSource.NodeDefinition node = nodes.get(coordinateChange.getNodeId());
            if (node == null) {
                throw new IllegalArgumentException("coordinate change nodeId not present: " + coordinateChange.getNodeId());
            }
            nodes.put(coordinateChange.getNodeId(), node.toBuilder()
                    .x(coordinateChange.getX())
                    .y(coordinateChange.getY())
                    .build());
        }

        for (StructuralChangeSet.ProfileAssignmentChange profileAssignment : nonNullChangeSet.getChangedProfileAssignments()) {
            TopologyModelSource.EdgeDefinition edge = edges.get(profileAssignment.getEdgeId());
            if (edge == null) {
                throw new IllegalArgumentException("profile-assignment edgeId not present: " + profileAssignment.getEdgeId());
            }
            edges.put(profileAssignment.getEdgeId(), edge.toBuilder()
                    .profileId(profileAssignment.getProfileId())
                    .build());
        }

        turnCosts.entrySet().removeIf(entry ->
                !edges.containsKey(entry.getKey().fromEdgeId()) || !edges.containsKey(entry.getKey().toEdgeId()));

        for (StructuralChangeSet.TurnRelationshipChange turnChange : nonNullChangeSet.getChangedTurnRelationships()) {
            TurnKey key = new TurnKey(turnChange.getFromEdgeId(), turnChange.getToEdgeId());
            if (turnChange.getOperation() == StructuralChangeSet.TurnChangeOperation.REMOVE) {
                turnCosts.remove(key);
                continue;
            }
            if (!edges.containsKey(turnChange.getFromEdgeId())) {
                throw new IllegalArgumentException("turn-change fromEdgeId missing: " + turnChange.getFromEdgeId());
            }
            if (!edges.containsKey(turnChange.getToEdgeId())) {
                throw new IllegalArgumentException("turn-change toEdgeId missing: " + turnChange.getToEdgeId());
            }
            turnCosts.put(key, TopologyModelSource.TurnCostDefinition.builder()
                    .fromEdgeId(turnChange.getFromEdgeId())
                    .toEdgeId(turnChange.getToEdgeId())
                    .penaltySeconds(turnChange.getPenaltySeconds())
                    .build());
        }

        TopologyModelSource.TopologyModelSourceBuilder builder = TopologyModelSource.builder()
                .engineTimeUnit(nonNullBaseSource.getEngineTimeUnit())
                .modelVersion(nonNullBaseSource.getModelVersion())
                .profileTimezone(nonNullBaseSource.getProfileTimezone());
        profiles.values().forEach(builder::profile);
        nodes.values().forEach(builder::node);
        edges.values().forEach(builder::edge);
        turnCosts.values().forEach(builder::turnCost);
        TopologyModelSource candidate = builder.build();
        candidate.validate();
        return candidate;
    }

    private static void validateChangeSet(StructuralChangeSet changeSet) {
        requireDistinct(changeSet.getRemovedNodes(), "removed nodeIds");
        requireDistinct(changeSet.getRemovedEdges(), "removed edgeIds");

        Set<String> addedNodeIds = new LinkedHashSet<>();
        for (StructuralChangeSet.NodeAddition nodeAddition : changeSet.getAddedNodes()) {
            if (!addedNodeIds.add(nodeAddition.getNodeId())) {
                throw new IllegalArgumentException("duplicate added nodeId: " + nodeAddition.getNodeId());
            }
        }
        Set<String> addedEdgeIds = new LinkedHashSet<>();
        for (StructuralChangeSet.EdgeAddition edgeAddition : changeSet.getAddedEdges()) {
            if (!addedEdgeIds.add(edgeAddition.getEdgeId())) {
                throw new IllegalArgumentException("duplicate added edgeId: " + edgeAddition.getEdgeId());
            }
        }
        for (String removedNodeId : changeSet.getRemovedNodes()) {
            if (addedNodeIds.contains(removedNodeId)) {
                throw new IllegalArgumentException("change set cannot remove and add the same nodeId: " + removedNodeId);
            }
        }
        for (String removedEdgeId : changeSet.getRemovedEdges()) {
            if (addedEdgeIds.contains(removedEdgeId)) {
                throw new IllegalArgumentException("change set cannot remove and add the same edgeId: " + removedEdgeId);
            }
        }
    }

    private static void requireDistinct(List<String> values, String fieldName) {
        Set<String> seen = new LinkedHashSet<>();
        for (String value : values) {
            if (!seen.add(value)) {
                throw new IllegalArgumentException("duplicate value in " + fieldName + ": " + value);
            }
        }
    }

    private static LinkedHashMap<Integer, TopologyModelSource.ProfileDefinition> indexProfiles(
            List<TopologyModelSource.ProfileDefinition> profiles
    ) {
        LinkedHashMap<Integer, TopologyModelSource.ProfileDefinition> indexed = new LinkedHashMap<>();
        profiles.forEach(profile -> indexed.put(profile.getProfileId(), profile));
        return indexed;
    }

    private static LinkedHashMap<String, TopologyModelSource.NodeDefinition> indexNodes(
            List<TopologyModelSource.NodeDefinition> nodes
    ) {
        LinkedHashMap<String, TopologyModelSource.NodeDefinition> indexed = new LinkedHashMap<>();
        nodes.forEach(node -> indexed.put(node.getNodeId(), node));
        return indexed;
    }

    private static LinkedHashMap<String, TopologyModelSource.EdgeDefinition> indexEdges(
            List<TopologyModelSource.EdgeDefinition> edges
    ) {
        LinkedHashMap<String, TopologyModelSource.EdgeDefinition> indexed = new LinkedHashMap<>();
        edges.forEach(edge -> indexed.put(edge.getEdgeId(), edge));
        return indexed;
    }

    private static LinkedHashMap<TurnKey, TopologyModelSource.TurnCostDefinition> indexTurnCosts(
            List<TopologyModelSource.TurnCostDefinition> turnCosts
    ) {
        LinkedHashMap<TurnKey, TopologyModelSource.TurnCostDefinition> indexed = new LinkedHashMap<>();
        turnCosts.forEach(turnCost -> indexed.put(new TurnKey(turnCost.getFromEdgeId(), turnCost.getToEdgeId()), turnCost));
        return indexed;
    }

    private record TurnKey(String fromEdgeId, String toEdgeId) {
        @Override
        public String toString() {
            return fromEdgeId + "->" + toEdgeId;
        }
    }
}
