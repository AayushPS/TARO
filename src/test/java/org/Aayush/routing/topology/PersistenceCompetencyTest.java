package org.Aayush.routing.topology;

import org.Aayush.routing.profile.ProfileStore;
import org.Aayush.routing.profile.ProfileRecurrenceCalibrationStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Persistence Competency Tests")
@Tag("smoke")
class PersistenceCompetencyTest {

    @Test
    @DisplayName("Compiled artifacts publish explicit posture for persistent, periodic, mixed, and weak recurring profiles")
    void testArtifactLayerPublishesRecurringPatternPosture() {
        TopologyModelSource source = TopologyModelSource.builder()
                .modelVersion("b3-persistence-competency")
                .profileTimezone("UTC")
                .profile(profile(1, 0x7F, 2.0f, 2.0f, 2.0f, 2.0f))
                .profile(profile(2, 0x7F, 1.0f, 2.0f, 1.0f, 2.0f))
                .profile(profile(3, 0x7F, 1.5f, 2.5f, 1.5f, 2.5f))
                .profile(profile(4, 0x1F, 1.0f, 1.08f, 1.0f, 1.08f))
                .node(node("N0", 0.0d, 0.0d))
                .node(node("N1", 1.0d, 0.0d))
                .edge(edge("E01", "N0", "N1", 1.0f, 1))
                .edge(edge("E10", "N1", "N0", 1.0f, 2))
                .edge(edge("E01m", "N0", "N1", 1.0f, 3))
                .edge(edge("E10w", "N1", "N0", 1.0f, 4))
                .build();

        CompiledTopologyModel compiled = new TopologyModelCompiler().compile(source);
        ProfileStore store = ProfileStore.fromFlatBuffer(compiled.getModelBuffer().duplicate().order(ByteOrder.LITTLE_ENDIAN));

        assertEquals(
                ProfileStore.TemporalPatternClass.FULLY_PERSISTENT,
                store.getTemporalPatternMetadata(1).temporalPatternClass()
        );
        assertEquals(
                ProfileStore.RecurringCalibrationPosture.NO_RECURRING_SIGNAL,
                store.getTemporalPatternMetadata(1).recurringCalibrationPosture()
        );
        assertEquals(
                ProfileStore.TemporalPatternClass.STRICT_PERIODIC,
                store.getTemporalPatternMetadata(2).temporalPatternClass()
        );
        assertEquals(
                ProfileStore.RecurringCalibrationPosture.HIGH_CONFIDENCE,
                store.getTemporalPatternMetadata(2).recurringCalibrationPosture()
        );
        assertEquals(
                ProfileRecurrenceCalibrationStore.SignalFlavor.ROUTINE_PERIODIC,
                store.getTemporalPatternMetadata(2).recurringSignalFlavor()
        );
        assertEquals(
                ProfileStore.TemporalPatternClass.MIXED_PERSISTENT_AND_PERIODIC,
                store.getTemporalPatternMetadata(3).temporalPatternClass()
        );

        ProfileStore.TemporalPatternMetadata weakSignal = store.getTemporalPatternMetadata(4);
        assertEquals(ProfileStore.TemporalPatternClass.WEAK_SIGNAL_PERIODIC, weakSignal.temporalPatternClass());
        assertEquals(5, weakSignal.activeDayCount());
        assertTrue(weakSignal.effectiveWeeklyRelativeRange() >= 0.05f);
        assertEquals(ProfileStore.RecurringCalibrationPosture.WEAK_SIGNAL_REJECTED, weakSignal.recurringCalibrationPosture());
    }

    private TopologyModelSource.ProfileDefinition profile(int profileId, int dayMask, float... buckets) {
        TopologyModelSource.ProfileDefinition.ProfileDefinitionBuilder builder = TopologyModelSource.ProfileDefinition.builder()
                .profileId(profileId)
                .dayMask(dayMask)
                .multiplier(1.0f);
        for (float bucket : buckets) {
            builder.bucket(bucket);
        }
        return builder.build();
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
