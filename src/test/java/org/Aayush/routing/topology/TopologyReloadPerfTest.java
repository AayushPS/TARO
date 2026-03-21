package org.Aayush.routing.topology;

import org.Aayush.routing.core.MatrixRequest;
import org.Aayush.routing.core.RouteRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("perf")
@Tag("integration")
@DisplayName("Topology Reload Perf Tests")
class TopologyReloadPerfTest {

    @Test
    @Timeout(value = 25, unit = TimeUnit.SECONDS)
    @DisplayName("Published topology keeps route and matrix throughput practical after reload")
    void testPublishedTopologyPerf() {
        TopologyModelSource source = TopologyTestFixtures.gridSource(16, 16);
        TopologyTestFixtures.Harness harness = TopologyTestFixtures.createHarness(source);

        harness.publicationService().publish(
                StructuralChangeSet.builder()
                        .addedEdge(StructuralChangeSet.EdgeAddition.builder()
                                .edgeId("reload-shortcut")
                                .originNodeId("N0")
                                .destinationNodeId("N255")
                                .baseWeight(4.0f)
                                .profileId(1)
                                .build())
                        .build()
        );

        int routeQueries = 700;
        int matrixQueries = 80;

        for (int i = 0; i < 100; i++) {
            harness.currentRouteCore().route(routeRequest(i));
        }

        long startNanos = System.nanoTime();
        for (int i = 0; i < routeQueries; i++) {
            assertTrue(harness.currentRouteCore().route(routeRequest(i)).isReachable());
        }
        for (int i = 0; i < matrixQueries; i++) {
            assertTrue(Float.isFinite(harness.currentRouteCore().matrix(matrixRequest(i)).getTotalCosts()[0][0]));
        }
        long elapsedNanos = System.nanoTime() - startNanos;

        double throughput = (routeQueries + matrixQueries) / (elapsedNanos / 1_000_000_000.0d);
        assertTrue(throughput > 35.0d, "post-reload throughput dropped below floor: " + throughput + " ops/sec");
    }

    private static RouteRequest routeRequest(int index) {
        int source = (index * 17) % 256;
        int target = (index * 37 + 11) % 256;
        return TopologyTestFixtures.routeRequest("N" + source, "N" + target);
    }

    private static MatrixRequest matrixRequest(int index) {
        MatrixRequest.MatrixRequestBuilder builder = MatrixRequest.builder()
                .departureTicks(0L)
                .algorithm(org.Aayush.routing.core.RoutingAlgorithm.DIJKSTRA)
                .heuristicType(org.Aayush.routing.heuristic.HeuristicType.NONE);
        int base = (index * 13) % 256;
        for (int i = 0; i < 3; i++) {
            builder.sourceExternalId("N" + ((base + (i * 7)) % 256));
            builder.targetExternalId("N" + ((base + 100 + (i * 11)) % 256));
        }
        return builder.build();
    }
}
