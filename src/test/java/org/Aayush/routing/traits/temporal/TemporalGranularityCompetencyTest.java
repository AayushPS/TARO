package org.Aayush.routing.traits.temporal;

import org.Aayush.routing.execution.ExecutionRuntimeConfig;
import org.Aayush.routing.topology.TopologyModelSource;
import org.Aayush.routing.topology.TopologyRuntimeFactory;
import org.Aayush.routing.topology.TopologyRuntimeTemplate;
import org.Aayush.routing.topology.TopologyVersion;
import org.Aayush.routing.traits.addressing.AddressingRuntimeConfig;
import org.Aayush.routing.traits.transition.TransitionRuntimeConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Temporal Granularity Competency Tests")
class TemporalGranularityCompetencyTest {

    @Test
    @DisplayName("Temporal binding publishes an explicit drift budget and loss policy")
    void testBindingPublishesGranularityContract() {
        ResolvedTemporalContext context = new TemporalRuntimeBinder().bind(
                TemporalRuntimeConfig.calendarUtc(),
                TemporalTraitCatalog.defaultCatalog(),
                TemporalStrategyRegistry.defaultRegistry(),
                TemporalTimezonePolicyRegistry.defaultRegistry(),
                TemporalPolicy.defaults()
        ).getResolvedTemporalContext();

        assertEquals(1_800L, context.getMaxDiscretizationDriftSeconds());
        assertEquals(TemporalGranularityLossPolicy.REJECT_EXCESS_DRIFT, context.getGranularityLossPolicy());
    }

    @Test
    @DisplayName("Snapshot build rejects bucket widths whose worst-case drift exceeds the configured budget")
    void testRejectsBucketWidthThatExceedsDriftBudget() {
        TopologyRuntimeFactory runtimeFactory = new TopologyRuntimeFactory(
                baseTemplate().toBuilder()
                        .bucketSizeSeconds(3_600)
                        .temporalRuntimeConfig(TemporalRuntimeConfig.builder()
                                .temporalTraitId(TemporalTraitCatalog.TRAIT_CALENDAR)
                                .timezonePolicyId(TemporalTimezonePolicyRegistry.POLICY_UTC)
                                .maxDiscretizationDrift(Duration.ofMinutes(15))
                                .granularityLossPolicy(TemporalGranularityLossPolicy.REJECT_EXCESS_DRIFT)
                                .build())
                        .build()
        );

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> runtimeFactory.buildSnapshot(source(), topologyVersion("granularity-reject"), 0L, null)
        );
        assertTrue(ex.getMessage().contains("drift budget"));
        assertTrue(ex.getMessage().contains("bucketSizeSeconds=3600"));
    }

    @Test
    @DisplayName("Snapshot build may allow a coarse bucket width when the policy is explicitly permissive")
    void testAllowsCoarseBucketWidthUnderExplicitPermissivePolicy() {
        TopologyRuntimeFactory runtimeFactory = new TopologyRuntimeFactory(
                baseTemplate().toBuilder()
                        .bucketSizeSeconds(3_600)
                        .temporalRuntimeConfig(TemporalRuntimeConfig.builder()
                                .temporalTraitId(TemporalTraitCatalog.TRAIT_CALENDAR)
                                .timezonePolicyId(TemporalTimezonePolicyRegistry.POLICY_UTC)
                                .maxDiscretizationDrift(Duration.ofMinutes(15))
                                .granularityLossPolicy(TemporalGranularityLossPolicy.ALLOW_WITHIN_BUDGET)
                                .build())
                        .build()
        );

        assertDoesNotThrow(() -> runtimeFactory.buildSnapshot(source(), topologyVersion("granularity-allow"), 0L, null));
    }

    private TopologyRuntimeTemplate baseTemplate() {
        return TopologyRuntimeTemplate.builder()
                .executionRuntimeConfig(ExecutionRuntimeConfig.dijkstra())
                .addressingRuntimeConfig(AddressingRuntimeConfig.defaultRuntime())
                .temporalRuntimeConfig(TemporalRuntimeConfig.calendarUtc())
                .transitionRuntimeConfig(TransitionRuntimeConfig.edgeBased())
                .liveOverlayCapacity(32)
                .build();
    }

    private TopologyModelSource source() {
        return TopologyModelSource.builder()
                .modelVersion("granularity-source")
                .profileTimezone("UTC")
                .profile(TopologyModelSource.ProfileDefinition.builder()
                        .profileId(1)
                        .dayMask(0x7F)
                        .bucket(1.0f)
                        .bucket(2.0f)
                        .bucket(1.0f)
                        .bucket(2.0f)
                        .multiplier(1.0f)
                        .build())
                .node(TopologyModelSource.NodeDefinition.builder().nodeId("N0").x(0.0d).y(0.0d).build())
                .node(TopologyModelSource.NodeDefinition.builder().nodeId("N1").x(1.0d).y(0.0d).build())
                .edge(TopologyModelSource.EdgeDefinition.builder()
                        .edgeId("E01")
                        .originNodeId("N0")
                        .destinationNodeId("N1")
                        .baseWeight(1.0f)
                        .profileId(1)
                        .build())
                .build();
    }

    private TopologyVersion topologyVersion(String topologyId) {
        return TopologyVersion.builder()
                .modelVersion(topologyId)
                .topologyVersion(topologyId)
                .generatedAt(Instant.parse("2026-03-22T00:00:00Z"))
                .sourceDataLineageHash("lineage-" + topologyId)
                .changeSetHash("changes-" + topologyId)
                .build();
    }
}
