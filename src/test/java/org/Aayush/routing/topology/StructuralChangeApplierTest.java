package org.Aayush.routing.topology;

import org.Aayush.routing.topology.TopologyModelSource.EdgeDefinition;
import org.Aayush.routing.topology.TopologyModelSource.NodeDefinition;
import org.Aayush.routing.topology.TopologyModelSource.ProfileDefinition;
import org.Aayush.routing.topology.TopologyModelSource.TurnCostDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Structural Change Applier Tests")
class StructuralChangeApplierTest {

    @Test
    @DisplayName("Typed structural changes update coordinates, profiles, edges, and turn relationships")
    void testAppliesTypedStructuralChanges() {
        TopologyModelSource source = baseSource();

        StructuralChangeSet changeSet = StructuralChangeSet.builder()
                .removedEdge("E12")
                .addedNode(StructuralChangeSet.NodeAddition.builder()
                        .nodeId("N4")
                        .x(4.0d)
                        .y(0.0d)
                        .build())
                .addedEdge(StructuralChangeSet.EdgeAddition.builder()
                        .edgeId("E34")
                        .originNodeId("N3")
                        .destinationNodeId("N4")
                        .baseWeight(1.0f)
                        .profileId(1)
                        .build())
                .changedCoordinate(StructuralChangeSet.CoordinateChange.builder()
                        .nodeId("N3")
                        .x(3.5d)
                        .y(1.5d)
                        .build())
                .changedProfileAssignment(StructuralChangeSet.ProfileAssignmentChange.builder()
                        .edgeId("E23")
                        .profileId(2)
                        .build())
                .changedTurnRelationship(StructuralChangeSet.TurnRelationshipChange.builder()
                        .operation(StructuralChangeSet.TurnChangeOperation.REMOVE)
                        .fromEdgeId("E12")
                        .toEdgeId("E23")
                        .build())
                .changedTurnRelationship(StructuralChangeSet.TurnRelationshipChange.builder()
                        .operation(StructuralChangeSet.TurnChangeOperation.UPSERT)
                        .fromEdgeId("E01")
                        .toEdgeId("E23")
                        .penaltySeconds(2.0f)
                        .build())
                .build();

        TopologyModelSource applied = new StructuralChangeApplier().apply(source, changeSet);

        assertEquals(5, applied.getNodes().size());
        assertTrue(applied.getNodes().stream().anyMatch(node -> node.getNodeId().equals("N4")));
        assertEquals(3.5d, findNode(applied, "N3").getX());
        assertEquals(1.5d, findNode(applied, "N3").getY());

        assertFalse(applied.getEdges().stream().anyMatch(edge -> edge.getEdgeId().equals("E12")));
        assertTrue(applied.getEdges().stream().anyMatch(edge -> edge.getEdgeId().equals("E34")));
        assertEquals(2, findEdge(applied, "E23").getProfileId());

        assertEquals(1, applied.getTurnCosts().size());
        TurnCostDefinition turnCost = applied.getTurnCosts().getFirst();
        assertEquals("E01", turnCost.getFromEdgeId());
        assertEquals("E23", turnCost.getToEdgeId());
        assertEquals(2.0f, turnCost.getPenaltySeconds());
    }

    @Test
    @DisplayName("Removing one node drops incident edges and orphaned turn relationships")
    void testRemovingNodeDropsIncidentEdges() {
        TopologyModelSource source = TopologyModelSource.builder()
                .modelVersion("remove-node")
                .profileTimezone("UTC")
                .profile(ProfileDefinition.builder()
                        .profileId(1)
                        .dayMask(0x7F)
                        .bucket(1.0f)
                        .multiplier(1.0f)
                        .build())
                .node(node("N0", 0.0d, 0.0d))
                .node(node("N1", 1.0d, 0.0d))
                .node(node("N2", 2.0d, 0.0d))
                .edge(edge("E01", "N0", "N1"))
                .edge(edge("E12", "N1", "N2"))
                .edge(edge("E02", "N0", "N2"))
                .turnCost(TurnCostDefinition.builder()
                        .fromEdgeId("E01")
                        .toEdgeId("E12")
                        .penaltySeconds(1.0f)
                        .build())
                .build();

        TopologyModelSource applied = new StructuralChangeApplier().apply(
                source,
                StructuralChangeSet.builder().removedNode("N1").build()
        );

        assertEquals(List.of("N0", "N2"), applied.getNodes().stream().map(NodeDefinition::getNodeId).toList());
        assertEquals(List.of("E02"), applied.getEdges().stream().map(EdgeDefinition::getEdgeId).toList());
        assertTrue(applied.getTurnCosts().isEmpty());
    }

    @Test
    @DisplayName("Change-set validation rejects duplicate and contradictory ids")
    void testRejectsDuplicateAndContradictoryChangeIds() {
        StructuralChangeApplier applier = new StructuralChangeApplier();
        TopologyModelSource source = baseSource();

        assertThrows(IllegalArgumentException.class, () -> applier.apply(
                source,
                StructuralChangeSet.builder().removedNode("N1").removedNode("N1").build()
        ));
        assertThrows(IllegalArgumentException.class, () -> applier.apply(
                source,
                StructuralChangeSet.builder().removedEdge("E12").removedEdge("E12").build()
        ));
        assertThrows(IllegalArgumentException.class, () -> applier.apply(
                source,
                StructuralChangeSet.builder()
                        .addedNode(StructuralChangeSet.NodeAddition.builder().nodeId("N4").x(4.0d).y(0.0d).build())
                        .addedNode(StructuralChangeSet.NodeAddition.builder().nodeId("N4").x(5.0d).y(0.0d).build())
                        .build()
        ));
        assertThrows(IllegalArgumentException.class, () -> applier.apply(
                source,
                StructuralChangeSet.builder()
                        .addedEdge(StructuralChangeSet.EdgeAddition.builder()
                                .edgeId("E34").originNodeId("N3").destinationNodeId("N0").baseWeight(1.0f).profileId(1).build())
                        .addedEdge(StructuralChangeSet.EdgeAddition.builder()
                                .edgeId("E34").originNodeId("N3").destinationNodeId("N1").baseWeight(1.0f).profileId(1).build())
                        .build()
        ));
        assertThrows(IllegalArgumentException.class, () -> applier.apply(
                source,
                StructuralChangeSet.builder()
                        .removedNode("N1")
                        .addedNode(StructuralChangeSet.NodeAddition.builder().nodeId("N1").x(1.0d).y(0.0d).build())
                        .build()
        ));
        assertThrows(IllegalArgumentException.class, () -> applier.apply(
                source,
                StructuralChangeSet.builder()
                        .removedEdge("E12")
                        .addedEdge(StructuralChangeSet.EdgeAddition.builder()
                                .edgeId("E12").originNodeId("N1").destinationNodeId("N2").baseWeight(1.0f).profileId(1).build())
                        .build()
        ));
    }

    @Test
    @DisplayName("Change application rejects missing subjects and coordinate posture mismatches")
    void testRejectsMissingSubjectsAndCoordinateMismatches() {
        StructuralChangeApplier applier = new StructuralChangeApplier();

        assertThrows(IllegalArgumentException.class, () -> applier.apply(
                baseSource(),
                StructuralChangeSet.builder().removedNode("missing").build()
        ));
        assertThrows(IllegalArgumentException.class, () -> applier.apply(
                baseSource(),
                StructuralChangeSet.builder().removedEdge("missing").build()
        ));
        assertThrows(IllegalArgumentException.class, () -> applier.apply(
                baseSource(),
                StructuralChangeSet.builder()
                        .addedNode(StructuralChangeSet.NodeAddition.builder().nodeId("N4").x(4.0d).build())
                        .build()
        ));

        TopologyModelSource noCoordinateSource = noCoordinateSource();
        assertThrows(IllegalArgumentException.class, () -> applier.apply(
                noCoordinateSource,
                StructuralChangeSet.builder()
                        .addedNode(StructuralChangeSet.NodeAddition.builder().nodeId("N2").x(2.0d).y(0.0d).build())
                        .build()
        ));
        assertThrows(IllegalArgumentException.class, () -> applier.apply(
                noCoordinateSource,
                StructuralChangeSet.builder()
                        .changedCoordinate(StructuralChangeSet.CoordinateChange.builder()
                                .nodeId("N0").x(1.0d).y(1.0d).build())
                        .build()
        ));
        assertThrows(IllegalArgumentException.class, () -> applier.apply(
                baseSource(),
                StructuralChangeSet.builder()
                        .changedCoordinate(StructuralChangeSet.CoordinateChange.builder()
                                .nodeId("missing").x(1.0d).y(1.0d).build())
                        .build()
        ));
    }

    @Test
    @DisplayName("Change application rejects missing edge references for additions, profile assignments, and turn upserts")
    void testRejectsMissingEdgeReferences() {
        StructuralChangeApplier applier = new StructuralChangeApplier();
        TopologyModelSource source = baseSource();

        assertThrows(IllegalArgumentException.class, () -> applier.apply(
                source,
                StructuralChangeSet.builder()
                        .addedEdge(StructuralChangeSet.EdgeAddition.builder()
                                .edgeId("E34").originNodeId("missing").destinationNodeId("N3").baseWeight(1.0f).profileId(1).build())
                        .build()
        ));
        assertThrows(IllegalArgumentException.class, () -> applier.apply(
                source,
                StructuralChangeSet.builder()
                        .addedEdge(StructuralChangeSet.EdgeAddition.builder()
                                .edgeId("E34").originNodeId("N3").destinationNodeId("missing").baseWeight(1.0f).profileId(1).build())
                        .build()
        ));
        assertThrows(IllegalArgumentException.class, () -> applier.apply(
                source,
                StructuralChangeSet.builder()
                        .changedProfileAssignment(StructuralChangeSet.ProfileAssignmentChange.builder()
                                .edgeId("missing").profileId(1).build())
                        .build()
        ));
        assertThrows(IllegalArgumentException.class, () -> applier.apply(
                source,
                StructuralChangeSet.builder()
                        .changedTurnRelationship(StructuralChangeSet.TurnRelationshipChange.builder()
                                .operation(StructuralChangeSet.TurnChangeOperation.UPSERT)
                                .fromEdgeId("missing")
                                .toEdgeId("E23")
                                .penaltySeconds(1.0f)
                                .build())
                        .build()
        ));
        assertThrows(IllegalArgumentException.class, () -> applier.apply(
                source,
                StructuralChangeSet.builder()
                        .changedTurnRelationship(StructuralChangeSet.TurnRelationshipChange.builder()
                                .operation(StructuralChangeSet.TurnChangeOperation.UPSERT)
                                .fromEdgeId("E01")
                                .toEdgeId("missing")
                                .penaltySeconds(1.0f)
                                .build())
                        .build()
        ));
    }

    @Test
    @DisplayName("TurnKey string form stays stable for diagnostics")
    void testTurnKeyToString() throws Exception {
        Class<?> turnKeyClass = Class.forName("org.Aayush.routing.topology.StructuralChangeApplier$TurnKey");
        var constructor = turnKeyClass.getDeclaredConstructor(String.class, String.class);
        constructor.setAccessible(true);
        Object turnKey = constructor.newInstance("E01", "E12");

        assertEquals("E01->E12", turnKey.toString());
    }

    private static TopologyModelSource baseSource() {
        return TopologyModelSource.builder()
                .modelVersion("base-source")
                .profileTimezone("UTC")
                .profile(ProfileDefinition.builder()
                        .profileId(1)
                        .dayMask(0x7F)
                        .bucket(1.0f)
                        .multiplier(1.0f)
                        .build())
                .profile(ProfileDefinition.builder()
                        .profileId(2)
                        .dayMask(0x7F)
                        .bucket(1.4f)
                        .multiplier(1.0f)
                        .build())
                .node(node("N0", 0.0d, 0.0d))
                .node(node("N1", 1.0d, 0.0d))
                .node(node("N2", 2.0d, 0.0d))
                .node(node("N3", 3.0d, 0.0d))
                .edge(edge("E01", "N0", "N1"))
                .edge(edge("E12", "N1", "N2"))
                .edge(edge("E23", "N2", "N3"))
                .turnCost(TurnCostDefinition.builder()
                        .fromEdgeId("E01")
                        .toEdgeId("E12")
                        .penaltySeconds(0.5f)
                        .build())
                .turnCost(TurnCostDefinition.builder()
                        .fromEdgeId("E12")
                        .toEdgeId("E23")
                        .penaltySeconds(0.5f)
                        .build())
                .build();
    }

    private static TopologyModelSource noCoordinateSource() {
        return TopologyModelSource.builder()
                .modelVersion("no-coordinates")
                .profileTimezone("UTC")
                .profile(ProfileDefinition.builder()
                        .profileId(1)
                        .dayMask(0x7F)
                        .bucket(1.0f)
                        .multiplier(1.0f)
                        .build())
                .node(NodeDefinition.builder().nodeId("N0").build())
                .node(NodeDefinition.builder().nodeId("N1").build())
                .edge(edge("E01", "N0", "N1"))
                .build();
    }

    private static NodeDefinition node(String nodeId, double x, double y) {
        return NodeDefinition.builder().nodeId(nodeId).x(x).y(y).build();
    }

    private static EdgeDefinition edge(String edgeId, String originNodeId, String destinationNodeId) {
        return EdgeDefinition.builder()
                .edgeId(edgeId)
                .originNodeId(originNodeId)
                .destinationNodeId(destinationNodeId)
                .baseWeight(1.0f)
                .profileId(1)
                .build();
    }

    private static NodeDefinition findNode(TopologyModelSource source, String nodeId) {
        return source.getNodes().stream()
                .filter(node -> node.getNodeId().equals(nodeId))
                .findFirst()
                .orElseThrow();
    }

    private static EdgeDefinition findEdge(TopologyModelSource source, String edgeId) {
        return source.getEdges().stream()
                .filter(edge -> edge.getEdgeId().equals(edgeId))
                .findFirst()
                .orElseThrow();
    }
}
