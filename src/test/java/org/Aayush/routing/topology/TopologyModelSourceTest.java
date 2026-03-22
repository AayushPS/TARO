package org.Aayush.routing.topology;

import org.Aayush.routing.profile.ProfileRecurrenceCalibrationStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Topology Model Source Tests")
class TopologyModelSourceTest {

    @Test
    @DisplayName("Coordinate posture is reported correctly for enabled and disabled sources")
    void testCoordinatePostureDetection() {
        TopologyModelSource withCoordinates = coordinateSource();
        TopologyModelSource withoutCoordinates = noCoordinateSource();

        assertTrue(withCoordinates.hasCoordinates());
        assertFalse(withoutCoordinates.hasCoordinates());
        assertDoesNotThrow(withCoordinates::validate);
        assertDoesNotThrow(withoutCoordinates::validate);
    }

    @Test
    @DisplayName("Validation rejects missing model metadata and empty node sets")
    void testRejectsMissingMetadataAndNodes() {
        assertThrows(IllegalArgumentException.class, () -> TopologyModelSource.builder()
                .modelVersion(" ")
                .profileTimezone("UTC")
                .build()
                .validate());
        assertThrows(IllegalArgumentException.class, () -> TopologyModelSource.builder()
                .modelVersion("model")
                .profileTimezone(" ")
                .node(TopologyModelSource.NodeDefinition.builder().nodeId("N0").x(0.0d).y(0.0d).build())
                .build()
                .validate());
        assertThrows(IllegalArgumentException.class, () -> TopologyModelSource.builder()
                .modelVersion("model")
                .profileTimezone("UTC")
                .build()
                .validate());
    }

    @Test
    @DisplayName("Validation rejects duplicate nodes and inconsistent coordinate payloads")
    void testRejectsInvalidNodeDefinitions() {
        assertThrows(IllegalArgumentException.class, () -> TopologyModelSource.builder()
                .modelVersion("dup-node")
                .profileTimezone("UTC")
                .node(node("N0", 0.0d, 0.0d))
                .node(node("N0", 1.0d, 0.0d))
                .build()
                .validate());
        assertThrows(IllegalArgumentException.class, () -> TopologyModelSource.builder()
                .modelVersion("partial-coordinates")
                .profileTimezone("UTC")
                .node(TopologyModelSource.NodeDefinition.builder().nodeId("N0").x(0.0d).build())
                .build()
                .validate());
        assertThrows(IllegalArgumentException.class, () -> TopologyModelSource.builder()
                .modelVersion("mixed-coordinates")
                .profileTimezone("UTC")
                .node(node("N0", 0.0d, 0.0d))
                .node(TopologyModelSource.NodeDefinition.builder().nodeId("N1").build())
                .build()
                .validate());
    }

    @Test
    @DisplayName("Validation rejects invalid profiles and edge contracts")
    void testRejectsInvalidProfilesAndEdges() {
        assertThrows(IllegalArgumentException.class, () -> TopologyModelSource.builder()
                .modelVersion("dup-profile")
                .profileTimezone("UTC")
                .profile(profile(1, 0x7F, 1.0f, 1.0f))
                .profile(profile(1, 0x7F, 1.0f, 1.0f))
                .node(node("N0", 0.0d, 0.0d))
                .build()
                .validate());
        assertThrows(IllegalArgumentException.class, () -> TopologyModelSource.builder()
                .modelVersion("bad-daymask")
                .profileTimezone("UTC")
                .profile(profile(1, 0, 1.0f, 1.0f))
                .node(node("N0", 0.0d, 0.0d))
                .build()
                .validate());
        assertThrows(IllegalArgumentException.class, () -> TopologyModelSource.builder()
                .modelVersion("empty-buckets")
                .profileTimezone("UTC")
                .profile(TopologyModelSource.ProfileDefinition.builder().profileId(1).dayMask(0x7F).multiplier(1.0f).build())
                .node(node("N0", 0.0d, 0.0d))
                .build()
                .validate());
        assertThrows(IllegalArgumentException.class, () -> TopologyModelSource.builder()
                .modelVersion("bad-multiplier")
                .profileTimezone("UTC")
                .profile(profile(1, 0x7F, 1.0f, 0.0f))
                .node(node("N0", 0.0d, 0.0d))
                .build()
                .validate());
        assertThrows(IllegalArgumentException.class, () -> TopologyModelSource.builder()
                .modelVersion("bad-bucket")
                .profileTimezone("UTC")
                .profile(profile(1, 0x7F, Float.NaN, 1.0f))
                .node(node("N0", 0.0d, 0.0d))
                .build()
                .validate());
        assertThrows(IllegalArgumentException.class, () -> TopologyModelSource.builder()
                .modelVersion("scaled-bucket-overflow")
                .profileTimezone("UTC")
                .profile(profile(1, 0x7F, Float.MAX_VALUE, Float.MAX_VALUE))
                .node(node("N0", 0.0d, 0.0d))
                .build()
                .validate());

        assertThrows(IllegalArgumentException.class, () -> TopologyModelSource.builder()
                .modelVersion("duplicate-edge")
                .profileTimezone("UTC")
                .profile(profile(1, 0x7F, 1.0f, 1.0f))
                .node(node("N0", 0.0d, 0.0d))
                .node(node("N1", 1.0d, 0.0d))
                .edge(edge("E01", "N0", "N1", 1.0f, 1))
                .edge(edge("E01", "N0", "N1", 1.0f, 1))
                .build()
                .validate());
        assertThrows(IllegalArgumentException.class, () -> TopologyModelSource.builder()
                .modelVersion("missing-origin")
                .profileTimezone("UTC")
                .profile(profile(1, 0x7F, 1.0f, 1.0f))
                .node(node("N1", 1.0d, 0.0d))
                .edge(edge("E01", "N0", "N1", 1.0f, 1))
                .build()
                .validate());
        assertThrows(IllegalArgumentException.class, () -> TopologyModelSource.builder()
                .modelVersion("missing-destination")
                .profileTimezone("UTC")
                .profile(profile(1, 0x7F, 1.0f, 1.0f))
                .node(node("N0", 0.0d, 0.0d))
                .edge(edge("E01", "N0", "N1", 1.0f, 1))
                .build()
                .validate());
        assertThrows(IllegalArgumentException.class, () -> coordinateSource().toBuilder()
                .edge(edge("Ebad", "N0", "N1", Float.NaN, 1))
                .build()
                .validate());
        assertThrows(IllegalArgumentException.class, () -> coordinateSource().toBuilder()
                .edge(edge("Ebad", "N0", "N1", -1.0f, 1))
                .build()
                .validate());
        assertThrows(IllegalArgumentException.class, () -> coordinateSource().toBuilder()
                .edge(edge("Ebad", "N0", "N1", 1.0f, 70_000))
                .build()
                .validate());
        assertThrows(IllegalArgumentException.class, () -> coordinateSource().toBuilder()
                .edge(edge("Ebad", "N0", "N1", 1.0f, 2))
                .build()
                .validate());
        assertThrows(IllegalArgumentException.class, () -> TopologyModelSource.builder()
                .modelVersion("partial-recurrence-override")
                .profileTimezone("UTC")
                .profile(TopologyModelSource.ProfileDefinition.builder()
                        .profileId(1)
                        .dayMask(0x1F)
                        .bucket(1.0f)
                        .multiplier(1.0f)
                        .recurringSignalFlavor(ProfileRecurrenceCalibrationStore.SignalFlavor.RECURRING_INCIDENT)
                        .build())
                .node(node("N0", 0.0d, 0.0d))
                .build()
                .validate());
        assertDoesNotThrow(() -> TopologyModelSource.builder()
                .modelVersion("explicit-recurrence-override")
                .profileTimezone("UTC")
                .profile(TopologyModelSource.ProfileDefinition.builder()
                        .profileId(1)
                        .dayMask(0x1F)
                        .bucket(1.0f)
                        .bucket(4.0f)
                        .multiplier(1.0f)
                        .recurringSignalFlavor(ProfileRecurrenceCalibrationStore.SignalFlavor.RECURRING_INCIDENT)
                        .recurringConfidence(0.7f)
                        .recurringObservationCount(12)
                        .lastObservedAtTicks(1_234_567L)
                        .build())
                .node(node("N0", 0.0d, 0.0d))
                .build()
                .validate());
        assertDoesNotThrow(() -> coordinateSource().toBuilder()
                .edge(edge("Eneutral", "N0", "N1", 1.0f, 0))
                .build()
                .validate());
    }

    @Test
    @DisplayName("Validation rejects invalid turn-cost relationships and penalties")
    void testRejectsInvalidTurnCosts() {
        TopologyModelSource base = coordinateSource();

        assertThrows(IllegalArgumentException.class, () -> base.toBuilder()
                .turnCost(TopologyModelSource.TurnCostDefinition.builder()
                        .fromEdgeId("missing")
                        .toEdgeId("E12")
                        .penaltySeconds(1.0f)
                        .build())
                .build()
                .validate());
        assertThrows(IllegalArgumentException.class, () -> base.toBuilder()
                .turnCost(TopologyModelSource.TurnCostDefinition.builder()
                        .fromEdgeId("E01")
                        .toEdgeId("missing")
                        .penaltySeconds(1.0f)
                        .build())
                .build()
                .validate());
        assertThrows(IllegalArgumentException.class, () -> base.toBuilder()
                .turnCost(TopologyModelSource.TurnCostDefinition.builder()
                        .fromEdgeId("E01")
                        .toEdgeId("E12")
                        .penaltySeconds(1.0f)
                        .build())
                .turnCost(TopologyModelSource.TurnCostDefinition.builder()
                        .fromEdgeId("E01")
                        .toEdgeId("E12")
                        .penaltySeconds(2.0f)
                        .build())
                .build()
                .validate());
        assertThrows(IllegalArgumentException.class, () -> base.toBuilder()
                .turnCost(TopologyModelSource.TurnCostDefinition.builder()
                        .fromEdgeId("E01")
                        .toEdgeId("E12")
                        .penaltySeconds(Float.NaN)
                        .build())
                .build()
                .validate());
        assertThrows(IllegalArgumentException.class, () -> base.toBuilder()
                .turnCost(TopologyModelSource.TurnCostDefinition.builder()
                        .fromEdgeId("E01")
                        .toEdgeId("E12")
                        .penaltySeconds(Float.NEGATIVE_INFINITY)
                        .build())
                .build()
                .validate());
        assertThrows(IllegalArgumentException.class, () -> base.toBuilder()
                .turnCost(TopologyModelSource.TurnCostDefinition.builder()
                        .fromEdgeId("E01")
                        .toEdgeId("E12")
                        .penaltySeconds(-1.0f)
                        .build())
                .build()
                .validate());
    }

    private TopologyModelSource coordinateSource() {
        return TopologyModelSource.builder()
                .modelVersion("coordinate-source")
                .profileTimezone("UTC")
                .profile(profile(1, 0x7F, 1.0f, 1.0f))
                .node(node("N0", 0.0d, 0.0d))
                .node(node("N1", 1.0d, 0.0d))
                .node(node("N2", 2.0d, 0.0d))
                .edge(edge("E01", "N0", "N1", 1.0f, 1))
                .edge(edge("E12", "N1", "N2", 1.0f, 1))
                .build();
    }

    private TopologyModelSource noCoordinateSource() {
        return TopologyModelSource.builder()
                .modelVersion("no-coordinate-source")
                .profileTimezone("UTC")
                .profile(profile(1, 0x7F, 1.0f, 1.0f))
                .node(TopologyModelSource.NodeDefinition.builder().nodeId("N0").build())
                .node(TopologyModelSource.NodeDefinition.builder().nodeId("N1").build())
                .edge(edge("E01", "N0", "N1", 1.0f, 1))
                .build();
    }

    private TopologyModelSource.ProfileDefinition profile(int profileId, int dayMask, float bucket, float multiplier) {
        return TopologyModelSource.ProfileDefinition.builder()
                .profileId(profileId)
                .dayMask(dayMask)
                .bucket(bucket)
                .multiplier(multiplier)
                .build();
    }

    private TopologyModelSource.NodeDefinition node(String nodeId, double x, double y) {
        return TopologyModelSource.NodeDefinition.builder()
                .nodeId(nodeId)
                .x(x)
                .y(y)
                .build();
    }

    private TopologyModelSource.EdgeDefinition edge(
            String edgeId,
            String originNodeId,
            String destinationNodeId,
            float baseWeight,
            int profileId
    ) {
        return TopologyModelSource.EdgeDefinition.builder()
                .edgeId(edgeId)
                .originNodeId(originNodeId)
                .destinationNodeId(destinationNodeId)
                .baseWeight(baseWeight)
                .profileId(profileId)
                .build();
    }
}
