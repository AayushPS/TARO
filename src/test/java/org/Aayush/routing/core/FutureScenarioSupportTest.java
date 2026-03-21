package org.Aayush.routing.core;

import org.Aayush.routing.cost.CostEngine;
import org.Aayush.routing.execution.ExecutionRuntimeConfig;
import org.Aayush.routing.future.ScenarioBundle;
import org.Aayush.routing.future.ScenarioDefinition;
import org.Aayush.routing.overlay.LiveUpdate;
import org.Aayush.routing.testutil.RoutingFixtureFactory;
import org.Aayush.routing.topology.FailureQuarantine;
import org.Aayush.routing.topology.TopologyRuntimeSnapshot;
import org.Aayush.routing.topology.TopologyVersion;
import org.Aayush.routing.traits.addressing.AddressingRuntimeConfig;
import org.Aayush.routing.traits.temporal.TemporalRuntimeConfig;
import org.Aayush.routing.traits.transition.TransitionRuntimeConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Future Scenario Support Tests")
class FutureScenarioSupportTest {

    @Test
    @DisplayName("Scenario cost engines inherit active overlay state and apply scenario updates without mutating the base")
    void testBuildScenarioCostEngineCopiesOverlay() {
        RouteCore routeCore = createRouteCore();
        RequestNormalizer.NormalizedRouteRequest normalized = routeCore.normalizeRouteRequest(
                RouteRequest.builder()
                        .sourceExternalId("N0")
                        .targetExternalId("N2")
                        .departureTicks(0L)
                        .algorithm(RoutingAlgorithm.DIJKSTRA)
                        .heuristicType(org.Aayush.routing.heuristic.HeuristicType.NONE)
                        .build()
        );
        CostEngine base = routeCore.costEngineContract();
        base.liveOverlay().upsert(LiveUpdate.of(1, 0.5f, 100L), 0L);

        CostEngine scenario = FutureScenarioSupport.buildScenarioCostEngine(
                base,
                0L,
                List.of(LiveUpdate.of(0, 0.25f, 100L))
        );

        float baseEdge0 = base.computeEdgeCost(
                0,
                0L,
                normalized.getInternalRequest().temporalContext(),
                normalized.getInternalRequest().transitionContext()
        );
        float baseEdge1 = base.computeEdgeCost(
                1,
                0L,
                normalized.getInternalRequest().temporalContext(),
                normalized.getInternalRequest().transitionContext()
        );
        float scenarioEdge0 = scenario.computeEdgeCost(
                0,
                0L,
                normalized.getInternalRequest().temporalContext(),
                normalized.getInternalRequest().transitionContext()
        );
        float scenarioEdge1 = scenario.computeEdgeCost(
                1,
                0L,
                normalized.getInternalRequest().temporalContext(),
                normalized.getInternalRequest().transitionContext()
        );

        assertEquals(1.0f, baseEdge0, 0.0001f);
        assertEquals(2.0f, baseEdge1, 0.0001f);
        assertEquals(4.0f, scenarioEdge0, 0.0001f);
        assertEquals(2.0f, scenarioEdge1, 0.0001f);
        assertEquals(1.0f, base.computeEdgeCost(
                0,
                0L,
                normalized.getInternalRequest().temporalContext(),
                normalized.getInternalRequest().transitionContext()
        ), 0.0001f);
    }

    @Test
    @DisplayName("Scenario bundle validation accepts matching topology and quarantine bindings")
    void testValidateScenarioBundleSuccess() {
        TopologyRuntimeSnapshot snapshot = snapshot();
        FailureQuarantine.Snapshot quarantineSnapshot = snapshot.getFailureQuarantine().snapshot(0L);
        ScenarioBundle bundle = ScenarioBundle.builder()
                .scenarioBundleId("bundle-ok")
                .generatedAt(Instant.EPOCH)
                .validUntil(Instant.EPOCH.plusSeconds(60))
                .horizonTicks(60L)
                .topologyVersion(snapshot.getTopologyVersion())
                .quarantineSnapshotId(quarantineSnapshot.snapshotId())
                .scenario(ScenarioDefinition.builder()
                        .scenarioId("baseline")
                        .label("baseline")
                        .probability(1.0d)
                        .build())
                .build();

        assertDoesNotThrow(() -> FutureScenarioSupport.validateScenarioBundle(bundle, snapshot, quarantineSnapshot));
    }

    @Test
    @DisplayName("Scenario bundle validation rejects missing scenarios and mismatched bindings")
    void testValidateScenarioBundleRejectsInvalidBundles() {
        TopologyRuntimeSnapshot snapshot = snapshot();
        FailureQuarantine.Snapshot quarantineSnapshot = snapshot.getFailureQuarantine().snapshot(0L);

        assertThrows(IllegalArgumentException.class, () -> FutureScenarioSupport.validateScenarioBundle(
                ScenarioBundle.builder()
                        .scenarioBundleId("empty")
                        .generatedAt(Instant.EPOCH)
                        .validUntil(Instant.EPOCH.plusSeconds(60))
                        .horizonTicks(60L)
                        .topologyVersion(snapshot.getTopologyVersion())
                        .quarantineSnapshotId(quarantineSnapshot.snapshotId())
                        .build(),
                snapshot,
                quarantineSnapshot
        ));

        assertThrows(IllegalArgumentException.class, () -> FutureScenarioSupport.validateScenarioBundle(
                bundleWithOneScenario(topologyVersion("other"), quarantineSnapshot.snapshotId(), "baseline", 1.0d),
                snapshot,
                quarantineSnapshot
        ));
        assertThrows(IllegalArgumentException.class, () -> FutureScenarioSupport.validateScenarioBundle(
                bundleWithOneScenario(snapshot.getTopologyVersion(), "wrong-quarantine", "baseline", 1.0d),
                snapshot,
                quarantineSnapshot
        ));
        assertThrows(IllegalArgumentException.class, () -> FutureScenarioSupport.validateScenarioBundle(
                bundleWithOneScenario(snapshot.getTopologyVersion(), quarantineSnapshot.snapshotId(), " ", 1.0d),
                snapshot,
                quarantineSnapshot
        ));
        assertThrows(IllegalArgumentException.class, () -> FutureScenarioSupport.validateScenarioBundle(
                bundleWithOneScenario(snapshot.getTopologyVersion(), quarantineSnapshot.snapshotId(), "baseline", 0.0d),
                snapshot,
                quarantineSnapshot
        ));
        assertThrows(IllegalArgumentException.class, () -> FutureScenarioSupport.validateScenarioBundle(
                ScenarioBundle.builder()
                        .scenarioBundleId("bad-total")
                        .generatedAt(Instant.EPOCH)
                        .validUntil(Instant.EPOCH.plusSeconds(60))
                        .horizonTicks(60L)
                        .topologyVersion(snapshot.getTopologyVersion())
                        .quarantineSnapshotId(quarantineSnapshot.snapshotId())
                        .scenario(ScenarioDefinition.builder().scenarioId("a").label("a").probability(0.4d).build())
                        .scenario(ScenarioDefinition.builder().scenarioId("b").label("b").probability(0.4d).build())
                        .build(),
                snapshot,
                quarantineSnapshot
        ));
    }

    private ScenarioBundle bundleWithOneScenario(
            TopologyVersion topologyVersion,
            String quarantineSnapshotId,
            String scenarioId,
            double probability
    ) {
        return ScenarioBundle.builder()
                .scenarioBundleId("bundle")
                .generatedAt(Instant.EPOCH)
                .validUntil(Instant.EPOCH.plusSeconds(60))
                .horizonTicks(60L)
                .topologyVersion(topologyVersion)
                .quarantineSnapshotId(quarantineSnapshotId)
                .scenario(ScenarioDefinition.builder()
                        .scenarioId(scenarioId)
                        .label("label")
                        .probability(probability)
                        .build())
                .build();
    }

    private TopologyRuntimeSnapshot snapshot() {
        return TopologyRuntimeSnapshot.builder()
                .routeCore(createRouteCore())
                .topologyVersion(topologyVersion("topo-1"))
                .failureQuarantine(new FailureQuarantine("q-1"))
                .build();
    }

    private TopologyVersion topologyVersion(String topologyId) {
        return TopologyVersion.builder()
                .modelVersion("model-v12")
                .topologyVersion(topologyId)
                .generatedAt(Instant.EPOCH)
                .sourceDataLineageHash("lineage-" + topologyId)
                .changeSetHash("change-" + topologyId)
                .build();
    }

    private RouteCore createRouteCore() {
        RoutingFixtureFactory.Fixture fixture = RoutingFixtureFactory.createFixture(
                3,
                new int[]{0, 1, 2, 2},
                new int[]{1, 2},
                new int[]{0, 1},
                new float[]{1.0f, 1.0f},
                new int[]{1, 1},
                new double[]{
                        0.0d, 0.0d,
                        1.0d, 0.0d,
                        2.0d, 0.0d
                },
                new RoutingFixtureFactory.ProfileSpec(
                        1,
                        RoutingFixtureFactory.ALL_DAYS_MASK,
                        new float[]{1.0f},
                        1.0f
                )
        );
        return RouteCore.builder()
                .edgeGraph(fixture.edgeGraph())
                .profileStore(fixture.profileStore())
                .costEngine(fixture.costEngine())
                .nodeIdMapper(fixture.nodeIdMapper())
                .executionRuntimeConfig(ExecutionRuntimeConfig.dijkstra())
                .temporalRuntimeConfig(TemporalRuntimeConfig.calendarUtc())
                .transitionRuntimeConfig(TransitionRuntimeConfig.edgeBased())
                .addressingRuntimeConfig(AddressingRuntimeConfig.defaultRuntime())
                .build();
    }
}
