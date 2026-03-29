package org.Aayush.routing.traits.temporal;

import org.Aayush.core.time.TimeUtils;
import org.Aayush.routing.core.FutureRouteEvaluator;
import org.Aayush.routing.core.RouteRequest;
import org.Aayush.routing.execution.ExecutionRuntimeConfig;
import org.Aayush.routing.future.FutureRouteRequest;
import org.Aayush.routing.future.FutureRouteResultSet;
import org.Aayush.routing.future.FutureRouteService;
import org.Aayush.routing.future.InMemoryEphemeralRouteResultStore;
import org.Aayush.routing.future.ScenarioBundle;
import org.Aayush.routing.future.ScenarioBundleResolver;
import org.Aayush.routing.future.ScenarioDefinition;
import org.Aayush.routing.topology.TopologyModelSource;
import org.Aayush.routing.topology.TopologyRuntimeFactory;
import org.Aayush.routing.topology.TopologyRuntimeSnapshot;
import org.Aayush.routing.topology.TopologyRuntimeTemplate;
import org.Aayush.routing.topology.TopologyVersion;
import org.Aayush.routing.traits.addressing.AddressingRuntimeConfig;
import org.Aayush.routing.traits.transition.TransitionRuntimeConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Temporal Granularity Competency Tests")
class TemporalGranularityCompetencyTest {
    private static final long SECONDS_PER_DAY = 86_400L;
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-03-21T00:00:00Z"), ZoneOffset.UTC);

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

    @Test
    @DisplayName("Same request at two allowed granularities stays within the published drift budget")
    void testSameRequestAtTwoGranularitiesStaysWithinPublishedDriftBudget() {
        ResolvedTemporalContext context = new TemporalRuntimeBinder().bind(
                TemporalRuntimeConfig.calendarUtc(),
                TemporalTraitCatalog.defaultCatalog(),
                TemporalStrategyRegistry.defaultRegistry(),
                TemporalTimezonePolicyRegistry.defaultRegistry(),
                TemporalPolicy.defaults()
        ).getResolvedTemporalContext();

        long departureTicks = Instant.parse("2026-03-08T07:43:00Z").getEpochSecond();
        long actualSecondsOfDay = TimeUtils.getTimeOfDay(departureTicks);

        int halfHourBucket = context.getResolver().resolveBucketIndex(
                departureTicks,
                1_800,
                TimeUtils.EngineTimeUnit.SECONDS
        );
        int hourBucket = context.getResolver().resolveBucketIndex(
                departureTicks,
                3_600,
                TimeUtils.EngineTimeUnit.SECONDS
        );

        long halfHourRepresentative = bucketMidpointSeconds(halfHourBucket, 1_800);
        long hourRepresentative = bucketMidpointSeconds(hourBucket, 3_600);

        assertEquals(15, halfHourBucket);
        assertEquals(7, hourBucket);
        assertEquals(120L, cyclicDifferenceSeconds(actualSecondsOfDay, halfHourRepresentative));
        assertEquals(780L, cyclicDifferenceSeconds(actualSecondsOfDay, hourRepresentative));
        assertEquals(900L, cyclicDifferenceSeconds(halfHourRepresentative, hourRepresentative));
        assertTrue(cyclicDifferenceSeconds(actualSecondsOfDay, hourRepresentative)
                <= context.getMaxDiscretizationDriftSeconds());
    }

    @Test
    @DisplayName("Fine-grained and daily-coarsened requests do not silently produce the same served future route")
    void testFineGrainedAndDailyCoarsenedRequestsDivergeInServedFutureOutput() {
        TopologyRuntimeSnapshot snapshot = new TopologyRuntimeFactory(baseTemplate())
                .buildSnapshot(
                        granularitySensitiveFutureRouteSource(),
                        topologyVersion("granularity-future-divergence"),
                        0L,
                        null
                );
        FutureRouteService service = new FutureRouteService(
                new FutureRouteEvaluator(baselineScenarioResolver(), FIXED_CLOCK),
                new InMemoryEphemeralRouteResultStore(FIXED_CLOCK)
        );

        FutureRouteResultSet fineGrained = service.evaluate(
                snapshot,
                futureRequest("2026-03-08T07:05:00Z")
        );
        FutureRouteResultSet dailyRepresentative = service.evaluate(
                snapshot,
                futureRequest("2026-03-08T12:00:00Z")
        );

        assertEquals(
                List.of("N0", "N2", "N3"),
                fineGrained.getExpectedRoute().getRoute().getPathExternalNodeIds()
        );
        assertEquals(
                List.of("N0", "N1", "N3"),
                dailyRepresentative.getExpectedRoute().getRoute().getPathExternalNodeIds()
        );
        assertNotEquals(
                fineGrained.getExpectedRoute().getRoute().getPathExternalNodeIds(),
                dailyRepresentative.getExpectedRoute().getRoute().getPathExternalNodeIds()
        );
        assertTrue(
                fineGrained.getExpectedRoute().getExpectedCost()
                        > dailyRepresentative.getExpectedRoute().getExpectedCost()
        );
    }

    @Test
    @DisplayName("TemporalContextResolver flags coarse and flattened timestamp collapse while preserving fine-grained separation")
    void testTemporalContextResolverFlagsCollapsedTimestampSets() {
        TemporalContextResolver resolver = new TemporalRuntimeBinder().bind(
                TemporalRuntimeConfig.calendarUtc(),
                TemporalTraitCatalog.defaultCatalog(),
                TemporalStrategyRegistry.defaultRegistry(),
                TemporalTimezonePolicyRegistry.defaultRegistry(),
                TemporalPolicy.defaults()
        ).getResolvedTemporalContext().getResolver();

        long[] fineGrainedTrainingTicks = {
                Instant.parse("2026-03-08T07:15:00Z").getEpochSecond(),
                Instant.parse("2026-03-08T11:45:00Z").getEpochSecond(),
                Instant.parse("2026-03-08T17:15:00Z").getEpochSecond(),
        };
        long[] flattenedTrainingTicks = {
                Instant.parse("2026-03-08T07:15:00Z").getEpochSecond(),
                Instant.parse("2026-03-08T07:15:00Z").getEpochSecond(),
                Instant.parse("2026-03-08T07:15:00Z").getEpochSecond(),
        };

        long fineDistinctBuckets = distinctBucketCount(
                resolver,
                fineGrainedTrainingTicks,
                1_800
        );
        long coarseDistinctBuckets = distinctBucketCount(
                resolver,
                fineGrainedTrainingTicks,
                86_400
        );
        long flattenedDistinctBuckets = distinctBucketCount(
                resolver,
                flattenedTrainingTicks,
                1_800
        );

        assertTrue(fineDistinctBuckets > 1L, "sub-hour timestamps should preserve distinct temporal buckets");
        assertEquals(1L, coarseDistinctBuckets, "daily coarsening should flag collapse into a single resolved bucket");
        assertEquals(1L, flattenedDistinctBuckets, "flattened constant timestamps should collapse to one resolved bucket");
        assertEquals(1L, Arrays.stream(flattenedTrainingTicks).distinct().count());
        assertTrue(Arrays.stream(fineGrainedTrainingTicks).distinct().count() > 1L);
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

    private TopologyModelSource granularitySensitiveFutureRouteSource() {
        return TopologyModelSource.builder()
                .modelVersion("granularity-sensitive-future-route")
                .profileTimezone("UTC")
                .profile(hourlyProfile(1, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 6.0f, 6.0f))
                .profile(hourlyProfile(2, 1.0f))
                .node(node("N0", 0.0d, 0.0d))
                .node(node("N1", 1.0d, 0.0d))
                .node(node("N2", 1.0d, 1.0d))
                .node(node("N3", 2.0d, 0.0d))
                .edge(edge("E01", "N0", "N1", 100.0f, 1))
                .edge(edge("E13", "N1", "N3", 100.0f, 1))
                .edge(edge("E02", "N0", "N2", 250.0f, 2))
                .edge(edge("E23", "N2", "N3", 250.0f, 2))
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

    private long bucketMidpointSeconds(int bucketIndex, int bucketSizeSeconds) {
        long midpoint = (long) bucketIndex * bucketSizeSeconds + (bucketSizeSeconds / 2L);
        return midpoint % SECONDS_PER_DAY;
    }

    private long cyclicDifferenceSeconds(long left, long right) {
        long difference = Math.abs(left - right);
        return Math.min(difference, SECONDS_PER_DAY - difference);
    }

    private long distinctBucketCount(
            TemporalContextResolver resolver,
            long[] entryTicks,
            int bucketSizeSeconds
    ) {
        return Arrays.stream(entryTicks)
                .map(ticks -> resolver.resolveBucketIndex(ticks, bucketSizeSeconds, TimeUtils.EngineTimeUnit.SECONDS))
                .distinct()
                .count();
    }

    private FutureRouteRequest futureRequest(String departureIsoInstant) {
        return FutureRouteRequest.builder()
                .routeRequest(RouteRequest.builder()
                        .sourceExternalId("N0")
                        .targetExternalId("N3")
                        .departureTicks(Instant.parse(departureIsoInstant).getEpochSecond())
                        .build())
                .horizonTicks(Duration.ofHours(2).toSeconds())
                .resultTtl(Duration.ofMinutes(10))
                .topKAlternatives(1)
                .build();
    }

    private ScenarioBundleResolver baselineScenarioResolver() {
        return (request, baseCostEngine, temporalContext, topologyVersion, quarantineSnapshot, clock) ->
                ScenarioBundle.builder()
                        .scenarioBundleId("granularity-baseline-" + request.getDepartureTicks())
                        .generatedAt(clock.instant())
                        .validUntil(clock.instant().plus(Duration.ofMinutes(10)))
                        .horizonTicks(request.getHorizonTicks())
                        .topologyVersion(topologyVersion)
                        .quarantineSnapshotId(quarantineSnapshot.snapshotId())
                        .scenario(ScenarioDefinition.builder()
                                .scenarioId("baseline")
                                .label("baseline")
                                .probability(1.0d)
                                .build())
                        .build();
    }

    private TopologyModelSource.ProfileDefinition hourlyProfile(int profileId, float defaultBucketValue, float... overrides) {
        float[] buckets = new float[24];
        Arrays.fill(buckets, defaultBucketValue);
        for (int hour = 0; hour < overrides.length; hour++) {
            buckets[hour] = overrides[hour];
        }
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
