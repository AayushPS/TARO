package org.Aayush.api;

import org.Aayush.routing.core.FutureMatrixEvaluator;
import org.Aayush.routing.core.FutureRouteEvaluator;
import org.Aayush.routing.core.RouteCore;
import org.Aayush.routing.execution.ExecutionRuntimeConfig;
import org.Aayush.routing.future.FutureMatrixService;
import org.Aayush.routing.future.FutureRouteService;
import org.Aayush.routing.future.InMemoryEphemeralMatrixResultStore;
import org.Aayush.routing.future.InMemoryEphemeralRouteResultStore;
import org.Aayush.routing.future.ScenarioBundle;
import org.Aayush.routing.future.ScenarioBundleRequest;
import org.Aayush.routing.future.ScenarioBundleResolver;
import org.Aayush.routing.future.ScenarioDefinition;
import org.Aayush.routing.future.TopologyAwareFutureMatrixService;
import org.Aayush.routing.future.TopologyAwareFutureRouteService;
import org.Aayush.routing.overlay.LiveUpdate;
import org.Aayush.routing.testutil.RoutingFixtureFactory;
import org.Aayush.routing.topology.FailureQuarantine;
import org.Aayush.routing.topology.ReloadCompatibilityPolicy;
import org.Aayush.routing.topology.TopologyReloadCoordinator;
import org.Aayush.routing.topology.TopologyRuntimeSnapshot;
import org.Aayush.routing.topology.TopologyVersion;
import org.Aayush.routing.traits.addressing.AddressingRuntimeConfig;
import org.Aayush.routing.traits.temporal.TemporalRuntimeConfig;
import org.Aayush.routing.traits.transition.TransitionRuntimeConfig;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Stage F1 Spring test wiring for the HTTP/API contract suites.
 * Satisfies closure criterion: frontend retrieval flow is verified through the actual API layer without recomputation.
 */
@TestConfiguration
public class FutureApiTestConfiguration {
    public static final Instant BASE_INSTANT = Instant.parse("2026-03-21T00:00:00Z");

    @Bean
    ApiMutableClock apiMutableClock() {
        return new ApiMutableClock(BASE_INSTANT);
    }

    @Bean
    InMemoryEphemeralRouteResultStore routeResultStore(Clock clock) {
        return new InMemoryEphemeralRouteResultStore(clock);
    }

    @Bean
    InMemoryEphemeralMatrixResultStore matrixResultStore(Clock clock) {
        return new InMemoryEphemeralMatrixResultStore(clock);
    }

    @Bean
    ScenarioBundleResolver apiScenarioBundleResolver() {
        return (
                ScenarioBundleRequest request,
                org.Aayush.routing.cost.CostEngine baseCostEngine,
                org.Aayush.routing.traits.temporal.ResolvedTemporalContext temporalContext,
                TopologyVersion topologyVersion,
                FailureQuarantine.Snapshot quarantineSnapshot,
                Clock clock
        ) -> ScenarioBundle.builder()
                .scenarioBundleId("bundle-api")
                .generatedAt(clock.instant())
                .validUntil(clock.instant().plus(Duration.ofMinutes(10)))
                .horizonTicks(request.getHorizonTicks())
                .topologyVersion(topologyVersion)
                .quarantineSnapshotId(quarantineSnapshot.snapshotId())
                .scenario(ScenarioDefinition.builder()
                        .scenarioId("baseline")
                        .label("baseline")
                        .probability(0.6d)
                        .build())
                .scenario(ScenarioDefinition.builder()
                        .scenarioId("incident_persists")
                        .label("incident_persists")
                        .probability(0.4d)
                        .explanationTag("incident_persists")
                        .liveUpdate(LiveUpdate.of(2, 0.4f, 10_000L))
                        .build())
                .build();
    }

    @Bean
    FutureRouteService futureRouteService(
            ScenarioBundleResolver apiScenarioBundleResolver,
            Clock clock,
            InMemoryEphemeralRouteResultStore routeResultStore
    ) {
        return new FutureRouteService(
                new FutureRouteEvaluator(apiScenarioBundleResolver, clock),
                routeResultStore
        );
    }

    @Bean
    FutureMatrixService futureMatrixService(
            ScenarioBundleResolver apiScenarioBundleResolver,
            Clock clock,
            InMemoryEphemeralMatrixResultStore matrixResultStore
    ) {
        return new FutureMatrixService(
                new FutureMatrixEvaluator(apiScenarioBundleResolver, clock),
                matrixResultStore
        );
    }

    @Bean
    TopologyReloadCoordinator topologyReloadCoordinator(
            OperationalMetricsService operationalMetricsService,
            InMemoryEphemeralRouteResultStore routeResultStore,
            InMemoryEphemeralMatrixResultStore matrixResultStore
    ) {
        return new TopologyReloadCoordinator(
                initialSnapshot(),
                ReloadCompatibilityPolicy.invalidateStaleTopologyResults(),
                List.of(routeResultStore, matrixResultStore),
                operationalMetricsService
        );
    }

    @Bean
    TopologyAwareFutureRouteService topologyAwareFutureRouteService(
            TopologyReloadCoordinator topologyReloadCoordinator,
            FutureRouteService futureRouteService
    ) {
        return new TopologyAwareFutureRouteService(topologyReloadCoordinator, futureRouteService);
    }

    @Bean
    TopologyAwareFutureMatrixService topologyAwareFutureMatrixService(
            TopologyReloadCoordinator topologyReloadCoordinator,
            FutureMatrixService futureMatrixService
    ) {
        return new TopologyAwareFutureMatrixService(topologyReloadCoordinator, futureMatrixService);
    }

    /**
     * Stage F1 mutable clock for deterministic HTTP/API tests.
     * Satisfies closure criterion: retained-result expiry posture remains testable and explicit.
     */
    public static final class ApiMutableClock extends Clock {
        private volatile Instant instant;
        private final ZoneId zoneId;

        public ApiMutableClock(Instant instant) {
            this(instant, ZoneOffset.UTC);
        }

        public ApiMutableClock(Instant instant, ZoneId zoneId) {
            this.instant = instant;
            this.zoneId = zoneId;
        }

        @Override
        public ZoneId getZone() {
            return zoneId;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new ApiMutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        public void set(Instant instant) {
            this.instant = instant;
        }
    }

    public static TopologyRuntimeSnapshot initialSnapshot() {
        return snapshot(createRouteCore(createAlternativeRouteFixture()), "topo-api");
    }

    private static RouteCore createRouteCore(RoutingFixtureFactory.Fixture fixture) {
        return RouteCore.builder()
                .edgeGraph(fixture.edgeGraph())
                .profileStore(fixture.profileStore())
                .costEngine(fixture.costEngine())
                .nodeIdMapper(fixture.nodeIdMapper())
                .executionRuntimeConfig(ExecutionRuntimeConfig.dijkstra())
                .addressingRuntimeConfig(AddressingRuntimeConfig.defaultRuntime())
                .temporalRuntimeConfig(TemporalRuntimeConfig.calendarUtc())
                .transitionRuntimeConfig(TransitionRuntimeConfig.edgeBased())
                .build();
    }

    private static TopologyRuntimeSnapshot snapshot(RouteCore routeCore, String topologyId) {
        TopologyVersion topologyVersion = TopologyVersion.builder()
                .modelVersion("model-api")
                .topologyVersion(topologyId)
                .generatedAt(BASE_INSTANT)
                .sourceDataLineageHash("lineage-" + topologyId)
                .changeSetHash("change-" + topologyId)
                .build();
        return TopologyRuntimeSnapshot.builder()
                .routeCore(routeCore)
                .topologyVersion(topologyVersion)
                .failureQuarantine(new FailureQuarantine("quarantine-" + topologyId))
                .build();
    }

    private static RoutingFixtureFactory.Fixture createAlternativeRouteFixture() {
        return RoutingFixtureFactory.createFixture(
                4,
                new int[]{0, 2, 3, 4, 4},
                new int[]{1, 2, 3, 3},
                new int[]{0, 0, 1, 2},
                new float[]{1.0f, 2.0f, 1.0f, 1.0f},
                new int[]{1, 1, 1, 1},
                new double[]{
                        0.0d, 0.0d,
                        1.0d, 0.0d,
                        0.0d, 1.0d,
                        1.0d, 1.0d
                },
                new RoutingFixtureFactory.ProfileSpec(1, RoutingFixtureFactory.ALL_DAYS_MASK, new float[]{1.0f}, 1.0f)
        );
    }
}
