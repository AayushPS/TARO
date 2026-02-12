package org.Aayush.routing.core;

import org.Aayush.routing.heuristic.HeuristicType;
import org.Aayush.routing.testutil.RoutingFixtureFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Stage 12 RouteCore Stress and Perf Tests")
class RouteCoreStressPerfTest {

    @Test
    @Timeout(value = 12, unit = java.util.concurrent.TimeUnit.SECONDS)
    @DisplayName("Stress: random query workload remains stable")
    void testRandomQueryStress() {
        RouteCore core = createGridCore(20, 20);
        Random random = new Random(2026);

        int queries = 2_000;
        for (int i = 0; i < queries; i++) {
            int source = random.nextInt(400);
            int target = random.nextInt(400);
            RouteResponse response = core.route(RouteRequest.builder()
                    .sourceExternalId("N" + source)
                    .targetExternalId("N" + target)
                    .departureTicks(i)
                    .algorithm(RoutingAlgorithm.A_STAR)
                    .heuristicType(HeuristicType.NONE)
                    .build());
            assertTrue(response.getArrivalTicks() >= response.getDepartureTicks());
            assertTrue(response.getTotalCost() >= 0.0f || response.getTotalCost() == Float.POSITIVE_INFINITY);
        }
    }

    @Test
    @Timeout(value = 8, unit = java.util.concurrent.TimeUnit.SECONDS)
    @DisplayName("Perf smoke: route throughput is practical for medium workload")
    void testPerfSmoke() {
        RouteCore core = createGridCore(15, 15);
        int queries = 1_500;
        long start = System.nanoTime();
        for (int i = 0; i < queries; i++) {
            int source = i % 225;
            int target = (i * 17) % 225;
            core.route(RouteRequest.builder()
                    .sourceExternalId("N" + source)
                    .targetExternalId("N" + target)
                    .departureTicks(0L)
                    .algorithm(RoutingAlgorithm.A_STAR)
                    .heuristicType(HeuristicType.NONE)
                    .build());
        }
        long elapsed = System.nanoTime() - start;
        double avgMicros = (elapsed / 1_000.0d) / queries;
        assertTrue(avgMicros < 500.0d, "average query latency should stay below 500us in this smoke test");
    }

    @Test
    @Timeout(value = 10, unit = java.util.concurrent.TimeUnit.SECONDS)
    @DisplayName("Concurrency stress: repeated route calls remain deterministic")
    void testConcurrentDeterminism() throws InterruptedException {
        RouteCore core = createGridCore(12, 12);
        RouteRequest request = RouteRequest.builder()
                .sourceExternalId("N0")
                .targetExternalId("N143")
                .departureTicks(10L)
                .algorithm(RoutingAlgorithm.A_STAR)
                .heuristicType(HeuristicType.NONE)
                .build();

        RouteResponse baseline = core.route(request);
        int threads = 8;
        int loops = 300;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicBoolean failed = new AtomicBoolean(false);

        for (int t = 0; t < threads; t++) {
            executor.execute(() -> {
                try {
                    for (int i = 0; i < loops; i++) {
                        RouteResponse current = core.route(request);
                        if (Float.compare(current.getTotalCost(), baseline.getTotalCost()) != 0
                                || current.getArrivalTicks() != baseline.getArrivalTicks()
                                || !current.getPathExternalNodeIds().equals(baseline.getPathExternalNodeIds())) {
                            failed.set(true);
                            break;
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdownNow();
        assertTrue(!failed.get(), "concurrent route results diverged");
        assertEquals(baseline.getPathExternalNodeIds().get(0), "N0");
    }

    private RouteCore createGridCore(int rows, int cols) {
        int nodeCount = rows * cols;
        int[] outDegree = new int[nodeCount];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int node = r * cols + c;
                int degree = 0;
                if (c + 1 < cols) degree++;
                if (c - 1 >= 0) degree++;
                if (r + 1 < rows) degree++;
                if (r - 1 >= 0) degree++;
                outDegree[node] = degree;
            }
        }

        int[] firstEdge = new int[nodeCount + 1];
        int edgeCount = 0;
        for (int i = 0; i < nodeCount; i++) {
            firstEdge[i] = edgeCount;
            edgeCount += outDegree[i];
        }
        firstEdge[nodeCount] = edgeCount;

        int[] edgeTarget = new int[edgeCount];
        int[] edgeOrigin = new int[edgeCount];
        float[] baseWeights = new float[edgeCount];
        int[] edgeProfiles = new int[edgeCount];
        double[] coords = new double[nodeCount * 2];

        int cursor = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int node = r * cols + c;
                coords[node * 2] = r;
                coords[node * 2 + 1] = c;

                if (c + 1 < cols) {
                    edgeTarget[cursor] = node + 1;
                    edgeOrigin[cursor] = node;
                    baseWeights[cursor] = 1.0f;
                    edgeProfiles[cursor] = 1;
                    cursor++;
                }
                if (c - 1 >= 0) {
                    edgeTarget[cursor] = node - 1;
                    edgeOrigin[cursor] = node;
                    baseWeights[cursor] = 1.0f;
                    edgeProfiles[cursor] = 1;
                    cursor++;
                }
                if (r + 1 < rows) {
                    edgeTarget[cursor] = node + cols;
                    edgeOrigin[cursor] = node;
                    baseWeights[cursor] = 1.0f;
                    edgeProfiles[cursor] = 1;
                    cursor++;
                }
                if (r - 1 >= 0) {
                    edgeTarget[cursor] = node - cols;
                    edgeOrigin[cursor] = node;
                    baseWeights[cursor] = 1.0f;
                    edgeProfiles[cursor] = 1;
                    cursor++;
                }
            }
        }

        RoutingFixtureFactory.Fixture fixture = RoutingFixtureFactory.createFixture(
                nodeCount,
                firstEdge,
                edgeTarget,
                edgeOrigin,
                baseWeights,
                edgeProfiles,
                coords,
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
                .build();
    }
}

