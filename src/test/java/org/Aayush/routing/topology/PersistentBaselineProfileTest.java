package org.Aayush.routing.topology;

import org.Aayush.routing.core.RouteCore;
import org.Aayush.routing.core.RouteRequest;
import org.Aayush.routing.core.RouteResponse;
import org.Aayush.routing.core.RoutingAlgorithm;
import org.Aayush.routing.heuristic.HeuristicType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Persistent Baseline Profile Tests")
@Tag("smoke")
class PersistentBaselineProfileTest {

    @Test
    @DisplayName("Constant congested and free-flow baselines remain directional across compile/load")
    void testConstantDirectionalBaselinesRemainStable() {
        TopologyRuntimeSnapshot snapshot = buildSnapshot(
                TopologyModelSource.builder()
                        .modelVersion("b2-constant-baselines")
                        .profileTimezone("UTC")
                        .profile(profile(1, 2.0f, 2.0f, 2.0f, 2.0f))
                        .profile(profile(2, 1.0f, 1.0f, 1.0f, 1.0f))
                        .node(node("N0", 0.0d, 0.0d))
                        .node(node("N1", 1.0d, 0.0d))
                        .edge(edge("E01", "N0", "N1", 10.0f, 1))
                        .edge(edge("E10", "N1", "N0", 10.0f, 2))
                        .build()
        );

        for (long departureTicks : new long[]{0L, 5L, 15L, 35L}) {
            assertRouteCost(snapshot.getRouteCore(), "N0", "N1", departureTicks, 20.0f);
            assertRouteCost(snapshot.getRouteCore(), "N1", "N0", departureTicks, 10.0f);
        }
    }

    @Test
    @DisplayName("Noisy persistent envelopes survive compile/load without flattening")
    void testNoisyPersistentEnvelopeSurvivesCompileAndLoad() {
        TopologyRuntimeSnapshot snapshot = buildSnapshot(
                TopologyModelSource.builder()
                        .modelVersion("b2-noisy-persistent-envelope")
                        .profileTimezone("UTC")
                        .profile(profile(1, 1.8f, 2.0f, 1.9f, 2.1f))
                        .node(node("N0", 0.0d, 0.0d))
                        .node(node("N1", 1.0d, 0.0d))
                        .edge(edge("E01", "N0", "N1", 10.0f, 1))
                        .build()
        );

        RouteCore routeCore = snapshot.getRouteCore();
        assertRouteCost(routeCore, "N0", "N1", 0L, 18.0f);
        assertRouteCost(routeCore, "N0", "N1", 10L, 20.0f);
        assertRouteCost(routeCore, "N0", "N1", 20L, 19.0f);
        assertRouteCost(routeCore, "N0", "N1", 30L, 21.0f);
    }

    private TopologyRuntimeSnapshot buildSnapshot(TopologyModelSource source) {
        TopologyRuntimeTemplate runtimeTemplate = TopologyTestFixtures.runtimeTemplate()
                .toBuilder()
                .bucketSizeSeconds(10)
                .build();
        TopologyRuntimeFactory runtimeFactory = new TopologyRuntimeFactory(runtimeTemplate);
        return runtimeFactory.buildSnapshot(source, TopologyTestFixtures.topologyVersion(source.getModelVersion()), 0L, null);
    }

    private void assertRouteCost(
            RouteCore routeCore,
            String sourceExternalId,
            String targetExternalId,
            long departureTicks,
            float expectedCost
    ) {
        RouteResponse response = routeCore.route(RouteRequest.builder()
                .sourceExternalId(sourceExternalId)
                .targetExternalId(targetExternalId)
                .departureTicks(departureTicks)
                .build());
        assertTrue(response.isReachable(), "expected route to remain reachable for " + sourceExternalId + " -> " + targetExternalId);
        assertEquals(expectedCost, response.getTotalCost(), 1e-6f);
    }

    private TopologyModelSource.ProfileDefinition profile(int profileId, float... buckets) {
        TopologyModelSource.ProfileDefinition.ProfileDefinitionBuilder builder = TopologyModelSource.ProfileDefinition.builder()
                .profileId(profileId)
                .dayMask(0x7F)
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
