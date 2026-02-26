package org.Aayush.routing.core;

import org.Aayush.core.time.TimeUtils;
import org.Aayush.routing.cost.CostEngine;
import org.Aayush.routing.heuristic.HeuristicType;
import org.Aayush.routing.overlay.LiveOverlay;
import org.Aayush.routing.testutil.RoutingFixtureFactory;
import org.Aayush.routing.testutil.TransitionTestContexts;
import org.Aayush.routing.traits.temporal.TemporalOffsetCache;
import org.Aayush.routing.traits.temporal.TemporalResolutionStrategy;
import org.Aayush.routing.traits.temporal.ResolvedTemporalContext;
import org.Aayush.routing.traits.temporal.TemporalRuntimeConfig;
import org.Aayush.routing.traits.temporal.TemporalStrategyRegistry;
import org.Aayush.routing.traits.temporal.TemporalTimezonePolicyRegistry;
import org.Aayush.routing.traits.temporal.TemporalTrait;
import org.Aayush.routing.traits.temporal.TemporalTraitCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Stage 16 Temporal Trait Tests")
class Stage16TemporalTraitTest {

    @Test
    @DisplayName("LINEAR ignores day mask while CALENDAR honors it")
    void testLinearIgnoresDayMask() {
        // Profile active Mon..Fri only; Sunday in CALENDAR should fallback to neutral multiplier.
        RoutingFixtureFactory.Fixture fixture = createSingleEdgeFixture(0x1F, 2.0f);
        CostEngine discreteEngine = createDiscreteEngine(fixture);

        RouteCore calendarCore = createCore(
                fixture,
                discreteEngine,
                TemporalRuntimeConfig.calendarUtc()
        );
        RouteCore linearCore = createCore(
                fixture,
                discreteEngine,
                TemporalRuntimeConfig.linear()
        );

        long sundayUtcTicks = 259_200L; // 1970-01-04T00:00:00Z (Sunday)
        RouteRequest request = RouteRequest.builder()
                .sourceExternalId("N0")
                .targetExternalId("N1")
                .departureTicks(sundayUtcTicks)
                .algorithm(RoutingAlgorithm.DIJKSTRA)
                .heuristicType(HeuristicType.NONE)
                .build();

        RouteResponse calendar = calendarCore.route(request);
        RouteResponse linear = linearCore.route(request);

        assertTrue(calendar.isReachable());
        assertTrue(linear.isReachable());
        assertEquals(1.0f, calendar.getTotalCost(), 1e-6f);
        assertEquals(2.0f, linear.getTotalCost(), 1e-6f);
    }

    @Test
    @DisplayName("CALENDAR MODEL_TIMEZONE uses model timezone for local day selection")
    void testCalendarModelTimezoneUsesLocalDay() {
        // Monday-only profile. Timestamp is Sunday UTC but Monday in Pacific/Kiritimati (+14).
        RoutingFixtureFactory.Fixture fixture = createSingleEdgeFixture(0x01, 2.0f);
        CostEngine discreteEngine = createDiscreteEngine(fixture);

        RouteCore calendarUtcCore = createCore(
                fixture,
                discreteEngine,
                TemporalRuntimeConfig.calendarUtc()
        );
        RouteCore calendarModelTimezoneCore = createCore(
                fixture,
                discreteEngine,
                TemporalRuntimeConfig.calendarModelTimezone("Pacific/Kiritimati")
        );

        long departureTicks = ZonedDateTime.of(2026, 1, 4, 12, 0, 0, 0, ZoneOffset.UTC).toEpochSecond();
        RouteRequest request = RouteRequest.builder()
                .sourceExternalId("N0")
                .targetExternalId("N1")
                .departureTicks(departureTicks)
                .algorithm(RoutingAlgorithm.DIJKSTRA)
                .heuristicType(HeuristicType.NONE)
                .build();

        RouteResponse utc = calendarUtcCore.route(request);
        RouteResponse modelTimezone = calendarModelTimezoneCore.route(request);

        assertEquals(1.0f, utc.getTotalCost(), 1e-6f);
        assertEquals(2.0f, modelTimezone.getTotalCost(), 1e-6f);
    }

    @Test
    @DisplayName("Route temporal resolution failures map to H16_TEMPORAL_RESOLUTION_FAILURE")
    void testRouteTemporalResolutionFailureMapping() {
        RoutingFixtureFactory.Fixture fixture = createSingleEdgeFixture(RoutingFixtureFactory.ALL_DAYS_MASK, 1.0f);
        RouteCore core = createCoreWithFailingTemporalStrategy(fixture);

        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> core.route(RouteRequest.builder()
                        .sourceExternalId("N0")
                        .targetExternalId("N1")
                        .departureTicks(0L)
                        .algorithm(RoutingAlgorithm.DIJKSTRA)
                        .heuristicType(HeuristicType.NONE)
                        .build())
        );
        assertEquals(RouteCore.REASON_TEMPORAL_RESOLUTION_FAILURE, ex.getReasonCode());
    }

    @Test
    @DisplayName("Matrix temporal resolution failures map to H16_TEMPORAL_RESOLUTION_FAILURE")
    void testMatrixTemporalResolutionFailureMapping() {
        RoutingFixtureFactory.Fixture fixture = createSingleEdgeFixture(RoutingFixtureFactory.ALL_DAYS_MASK, 1.0f);
        RouteCore core = createCoreWithFailingTemporalStrategy(fixture);

        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> core.matrix(MatrixRequest.builder()
                        .sourceExternalId("N0")
                        .targetExternalId("N1")
                        .departureTicks(0L)
                        .algorithm(RoutingAlgorithm.DIJKSTRA)
                        .heuristicType(HeuristicType.NONE)
                        .build())
        );
        assertEquals(RouteCore.REASON_TEMPORAL_RESOLUTION_FAILURE, ex.getReasonCode());
    }

    @Test
    @DisplayName("MODEL_TIMEZONE spring-forward and fall-back route sampling stays deterministic")
    void testModelTimezoneDstBoundaryDeterminism() {
        RoutingFixtureFactory.Fixture fixture = createHourlyProfileFixture();
        CostEngine discreteEngine = createDiscreteEngine(fixture);
        RouteCore core = createCore(
                fixture,
                discreteEngine,
                TemporalRuntimeConfig.calendarModelTimezone("America/New_York")
        );

        long springBefore = ZonedDateTime.of(2026, 3, 8, 6, 30, 0, 0, ZoneOffset.UTC).toEpochSecond();
        long springAfter = ZonedDateTime.of(2026, 3, 8, 7, 30, 0, 0, ZoneOffset.UTC).toEpochSecond();
        long fallBefore = ZonedDateTime.of(2026, 11, 1, 5, 30, 0, 0, ZoneOffset.UTC).toEpochSecond();
        long fallAfter = ZonedDateTime.of(2026, 11, 1, 6, 30, 0, 0, ZoneOffset.UTC).toEpochSecond();

        RouteResponse springBeforeA = routeSingleEdge(core, springBefore);
        RouteResponse springBeforeB = routeSingleEdge(core, springBefore);
        RouteResponse springAfterA = routeSingleEdge(core, springAfter);
        RouteResponse fallBeforeA = routeSingleEdge(core, fallBefore);
        RouteResponse fallAfterA = routeSingleEdge(core, fallAfter);

        assertEquals(springBeforeA.getTotalCost(), springBeforeB.getTotalCost(), 1e-6f);
        assertEquals(2.0f, springBeforeA.getTotalCost(), 1e-6f); // local 01:30 -> bucket 1
        assertEquals(4.0f, springAfterA.getTotalCost(), 1e-6f);  // local 03:30 -> bucket 3
        assertEquals(2.0f, fallBeforeA.getTotalCost(), 1e-6f);   // local 01:30 (EDT)
        assertEquals(2.0f, fallAfterA.getTotalCost(), 1e-6f);    // local 01:30 (EST)
    }

    @Test
    @DisplayName("InternalRouteRequest requires explicit temporal context (no implicit default)")
    void testInternalRouteRequestRequiresExplicitTemporalContext() {
        assertThrows(
                NullPointerException.class,
                () -> new InternalRouteRequest(
                        0,
                        1,
                        0L,
                        RoutingAlgorithm.DIJKSTRA,
                        HeuristicType.NONE,
                        null,
                        TransitionTestContexts.edgeBased()
                )
        );
    }

    @Test
    @DisplayName("InternalMatrixRequest requires explicit temporal context (no implicit default)")
    void testInternalMatrixRequestRequiresExplicitTemporalContext() {
        assertThrows(
                NullPointerException.class,
                () -> new InternalMatrixRequest(
                        new int[]{0},
                        new int[]{1},
                        0L,
                        RoutingAlgorithm.DIJKSTRA,
                        HeuristicType.NONE,
                        null,
                        TransitionTestContexts.edgeBased()
                )
        );
    }

    @Test
    @DisplayName("ResolvedTemporalContext exposes no global default fallback helper")
    void testResolvedTemporalContextHasNoGlobalDefaultFallbackMethod() {
        assertThrows(
                NoSuchMethodException.class,
                () -> ResolvedTemporalContext.class.getDeclaredMethod("defaultCalendarUtc")
        );
    }

    private RouteCore createCore(
            RoutingFixtureFactory.Fixture fixture,
            CostEngine costEngine,
            TemporalRuntimeConfig temporalRuntimeConfig
    ) {
        return RouteCore.builder()
                .edgeGraph(fixture.edgeGraph())
                .profileStore(fixture.profileStore())
                .costEngine(costEngine)
                .nodeIdMapper(fixture.nodeIdMapper())
                .temporalRuntimeConfig(temporalRuntimeConfig)
                .transitionRuntimeConfig(org.Aayush.routing.traits.transition.TransitionRuntimeConfig.defaultRuntime())
                .addressingRuntimeConfig(org.Aayush.routing.traits.addressing.AddressingRuntimeConfig.defaultRuntime())
                .build();
    }

    private CostEngine createDiscreteEngine(RoutingFixtureFactory.Fixture fixture) {
        return new CostEngine(
                fixture.edgeGraph(),
                fixture.profileStore(),
                new LiveOverlay(16),
                null,
                TimeUtils.EngineTimeUnit.SECONDS,
                RoutingFixtureFactory.BUCKET_SIZE_SECONDS,
                CostEngine.TemporalSamplingPolicy.DISCRETE
        );
    }

    private RouteCore createCoreWithFailingTemporalStrategy(RoutingFixtureFactory.Fixture fixture) {
        TemporalTrait failingCalendarTrait = new TemporalTrait() {
            @Override
            public String id() {
                return TemporalTraitCatalog.TRAIT_CALENDAR;
            }

            @Override
            public String strategyId() {
                return "FAILING_STRATEGY";
            }
        };

        TemporalResolutionStrategy failingStrategy = new TemporalResolutionStrategy() {
            @Override
            public String id() {
                return "FAILING_STRATEGY";
            }

            @Override
            public boolean dayMaskAware() {
                return true;
            }

            @Override
            public int resolveDayOfWeek(
                    long entryTicks,
                    TimeUtils.EngineTimeUnit unit,
                    ZoneId zoneId,
                    TemporalOffsetCache offsetCache
            ) {
                throw new IllegalStateException("forced temporal resolver failure");
            }

            @Override
            public int resolveBucketIndex(
                    long entryTicks,
                    int bucketSizeSeconds,
                    TimeUtils.EngineTimeUnit unit,
                    ZoneId zoneId,
                    TemporalOffsetCache offsetCache
            ) {
                throw new IllegalStateException("forced temporal resolver failure");
            }

            @Override
            public double resolveFractionalBucket(
                    long entryTicks,
                    long bucketSizeTicks,
                    TimeUtils.EngineTimeUnit unit,
                    ZoneId zoneId,
                    TemporalOffsetCache offsetCache
            ) {
                throw new IllegalStateException("forced temporal resolver failure");
            }
        };

        return RouteCore.builder()
                .edgeGraph(fixture.edgeGraph())
                .profileStore(fixture.profileStore())
                .costEngine(createDiscreteEngine(fixture))
                .nodeIdMapper(fixture.nodeIdMapper())
                .temporalRuntimeConfig(TemporalRuntimeConfig.calendarUtc())
                .transitionRuntimeConfig(org.Aayush.routing.traits.transition.TransitionRuntimeConfig.defaultRuntime())
                .addressingRuntimeConfig(org.Aayush.routing.traits.addressing.AddressingRuntimeConfig.defaultRuntime())
                .temporalTraitCatalog(new TemporalTraitCatalog(List.of(failingCalendarTrait)))
                .temporalStrategyRegistry(new TemporalStrategyRegistry(List.of(failingStrategy)))
                .temporalTimezonePolicyRegistry(TemporalTimezonePolicyRegistry.defaultRegistry())
                .build();
    }

    private RouteResponse routeSingleEdge(RouteCore core, long departureTicks) {
        return core.route(RouteRequest.builder()
                .sourceExternalId("N0")
                .targetExternalId("N1")
                .departureTicks(departureTicks)
                .algorithm(RoutingAlgorithm.DIJKSTRA)
                .heuristicType(HeuristicType.NONE)
                .build());
    }

    private RoutingFixtureFactory.Fixture createSingleEdgeFixture(int dayMask, float bucketMultiplier) {
        float[] buckets = new float[24];
        for (int i = 0; i < buckets.length; i++) {
            buckets[i] = bucketMultiplier;
        }
        return createSingleEdgeFixture(dayMask, buckets);
    }

    private RoutingFixtureFactory.Fixture createSingleEdgeFixture(int dayMask, float[] buckets) {
        return RoutingFixtureFactory.createFixture(
                2,
                new int[]{0, 1, 1},
                new int[]{1},
                new int[]{0},
                new float[]{1.0f},
                new int[]{1},
                new double[]{
                        0.0d, 0.0d,
                        1.0d, 0.0d
                },
                new RoutingFixtureFactory.ProfileSpec(
                        1,
                        dayMask,
                        buckets,
                        1.0f
                )
        );
    }

    private RoutingFixtureFactory.Fixture createHourlyProfileFixture() {
        float[] buckets = new float[24];
        for (int i = 0; i < buckets.length; i++) {
            buckets[i] = i + 1.0f;
        }
        return createSingleEdgeFixture(RoutingFixtureFactory.ALL_DAYS_MASK, buckets);
    }
}
