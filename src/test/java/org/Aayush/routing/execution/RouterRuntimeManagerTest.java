package org.Aayush.routing.execution;

import org.Aayush.routing.core.RouteCore;
import org.Aayush.routing.core.RoutePlanner;
import org.Aayush.routing.core.RouteRequest;
import org.Aayush.routing.core.RouteResponse;
import org.Aayush.routing.core.RoutingAlgorithm;
import org.Aayush.routing.heuristic.HeuristicType;
import org.Aayush.routing.testutil.RoutingFixtureFactory;
import org.Aayush.routing.traits.addressing.AddressingRuntimeConfig;
import org.Aayush.routing.traits.temporal.TemporalRuntimeConfig;
import org.Aayush.routing.traits.transition.TransitionRuntimeConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Router Runtime Manager Tests")
class RouterRuntimeManagerTest {

    @Test
    @DisplayName("Manager validates and applies execution-profile swaps")
    void testValidateAndApplyExecutionProfileSwap() {
        RoutingFixtureFactory.Fixture fixture = createFixture();
        RouterRuntimeFactory factory = executionRuntimeConfig -> createCore(fixture, executionRuntimeConfig, null);
        RouterRuntimeManager manager = new RouterRuntimeManager(factory, ExecutionRuntimeConfig.dijkstra());

        assertEquals(RoutingAlgorithm.DIJKSTRA, manager.currentExecutionProfileContext().getAlgorithm());

        ResolvedExecutionProfileContext validated = manager.validateExecutionRuntimeConfig(
                ExecutionRuntimeConfig.aStar(HeuristicType.NONE)
        );
        assertEquals(RoutingAlgorithm.A_STAR, validated.getAlgorithm());

        ResolvedExecutionProfileContext applied = manager.applyExecutionRuntimeConfig(
                ExecutionRuntimeConfig.aStar(HeuristicType.NONE)
        );
        assertEquals(RoutingAlgorithm.A_STAR, applied.getAlgorithm());
        assertEquals(RoutingAlgorithm.A_STAR, manager.currentExecutionProfileContext().getAlgorithm());

        RouteResponse response = manager.route(RouteRequest.builder()
                .sourceExternalId("N0")
                .targetExternalId("N4")
                .departureTicks(0L)
                .build());
        assertTrue(response.isReachable());
        assertEquals(RoutingAlgorithm.A_STAR, response.getAlgorithm());
    }

    @Test
    @DisplayName("In-flight route stays pinned to the router snapshot active at request entry")
    void testInFlightRouteRemainsPinnedAcrossSwap() throws Exception {
        RoutingFixtureFactory.Fixture fixture = createFixture();
        CountDownLatch enteredPlanner = new CountDownLatch(1);
        CountDownLatch releasePlanner = new CountDownLatch(1);
        RoutePlanner blockingAStarPlanner = (edgeGraph, costEngine, heuristic, request) -> {
            enteredPlanner.countDown();
            try {
                if (!releasePlanner.await(5L, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("timed out waiting for planner release");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while waiting for planner release", ex);
            }
            return new org.Aayush.routing.core.InternalRoutePlan(
                    true,
                    4.0f,
                    request.departureTicks() + 4L,
                    1,
                    new int[]{request.sourceNodeId(), request.targetNodeId()}
            );
        };
        RouterRuntimeFactory factory = executionRuntimeConfig -> {
            if (executionRuntimeConfig.getInlineExecutionProfileSpec() != null
                    && executionRuntimeConfig.getInlineExecutionProfileSpec().getAlgorithm() == RoutingAlgorithm.A_STAR) {
                return createCore(fixture, executionRuntimeConfig, blockingAStarPlanner);
            }
            return createCore(fixture, executionRuntimeConfig, null);
        };
        RouterRuntimeManager manager = new RouterRuntimeManager(factory, ExecutionRuntimeConfig.aStar(HeuristicType.NONE));

        CompletableFuture<RouteResponse> future = CompletableFuture.supplyAsync(() -> manager.route(RouteRequest.builder()
                .sourceExternalId("N0")
                .targetExternalId("N4")
                .departureTicks(0L)
                .build()));

        assertTrue(enteredPlanner.await(5L, TimeUnit.SECONDS));
        manager.applyExecutionRuntimeConfig(ExecutionRuntimeConfig.dijkstra());
        releasePlanner.countDown();

        RouteResponse inFlightResponse = future.get(5L, TimeUnit.SECONDS);
        assertEquals(RoutingAlgorithm.A_STAR, inFlightResponse.getAlgorithm());
        assertEquals(RoutingAlgorithm.DIJKSTRA, manager.currentExecutionProfileContext().getAlgorithm());
    }

    private RouteCore createCore(
            RoutingFixtureFactory.Fixture fixture,
            ExecutionRuntimeConfig executionRuntimeConfig,
            RoutePlanner aStarPlanner
    ) {
        RouteCore.RouteCoreBuilder builder = RouteCore.builder()
                .edgeGraph(fixture.edgeGraph())
                .profileStore(fixture.profileStore())
                .costEngine(fixture.costEngine())
                .nodeIdMapper(fixture.nodeIdMapper())
                .executionRuntimeConfig(executionRuntimeConfig)
                .temporalRuntimeConfig(TemporalRuntimeConfig.calendarUtc())
                .transitionRuntimeConfig(TransitionRuntimeConfig.edgeBased())
                .addressingRuntimeConfig(AddressingRuntimeConfig.defaultRuntime());
        if (aStarPlanner != null) {
            builder.aStarPlanner(aStarPlanner);
        }
        return builder.build();
    }

    private RoutingFixtureFactory.Fixture createFixture() {
        return RoutingFixtureFactory.createFixture(
                5,
                new int[]{0, 1, 2, 3, 4, 4},
                new int[]{1, 2, 3, 4},
                new int[]{0, 1, 2, 3},
                new float[]{1.0f, 1.0f, 1.0f, 1.0f},
                new int[]{1, 1, 1, 1},
                new double[]{
                        0.0d, 0.0d,
                        1.0d, 0.0d,
                        2.0d, 0.0d,
                        3.0d, 0.0d,
                        4.0d, 0.0d
                },
                new RoutingFixtureFactory.ProfileSpec(
                        1,
                        RoutingFixtureFactory.ALL_DAYS_MASK,
                        new float[]{1.0f},
                        1.0f
                )
        );
    }
}
