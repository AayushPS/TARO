package org.Aayush.routing.future;

import org.Aayush.routing.core.FutureRouteEvaluator;
import org.Aayush.routing.core.RouteRequest;
import org.Aayush.routing.execution.ExecutionRuntimeConfig;
import org.Aayush.routing.overlay.LiveUpdate;
import org.Aayush.routing.profile.ProfileRecurrenceCalibrationStore;
import org.Aayush.routing.topology.TopologyModelSource;
import org.Aayush.routing.topology.TopologyRuntimeFactory;
import org.Aayush.routing.topology.TopologyRuntimeSnapshot;
import org.Aayush.routing.topology.TopologyRuntimeTemplate;
import org.Aayush.routing.topology.TopologyVersion;
import org.Aayush.routing.traits.addressing.AddressingRuntimeConfig;
import org.Aayush.routing.traits.temporal.TemporalRuntimeConfig;
import org.Aayush.routing.traits.transition.TransitionRuntimeConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Temporal Fidelity Contract Tests")
@Tag("integration")
class TemporalFidelityContractTest {
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-03-21T00:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("Periodic artifact behavior survives scenario evaluation and aggregate winner selection")
    void testPeriodicArtifactSurvivesFutureServing() {
        TopologyRuntimeSnapshot snapshot = snapshot(periodicContractSource(), "b3-temporal-fidelity");
        FutureRouteService service = new FutureRouteService(
                new FutureRouteEvaluator(periodicContractResolver(), FIXED_CLOCK),
                new InMemoryEphemeralRouteResultStore(FIXED_CLOCK)
        );

        FutureRouteResultSet offDay = service.evaluate(snapshot, requestAt("2026-03-22T08:00:00Z"));
        FutureRouteResultSet rushHour = service.evaluate(snapshot, requestAt("2026-03-23T08:00:00Z"));

        assertEquals(List.of("N0", "N1", "N3"), offDay.getExpectedRoute().getRoute().getPathExternalNodeIds());
        assertEquals(List.of("N0", "N1", "N3"), offDay.getRobustRoute().getRoute().getPathExternalNodeIds());
        assertEquals(List.of("N0", "N2", "N3"), rushHour.getExpectedRoute().getRoute().getPathExternalNodeIds());
        assertEquals(List.of("N0", "N2", "N3"), rushHour.getRobustRoute().getRoute().getPathExternalNodeIds());
        assertEquals(2, offDay.getScenarioBundle().getScenarios().size());
        assertEquals(2, rushHour.getScenarioBundle().getScenarios().size());
    }

    @Test
    @DisplayName("Default resolver carries periodic artifact signal into shipped future-aware serving without activating it before the peak window")
    void testDefaultResolverCarriesPeriodicSignalIntoServing() {
        TopologyRuntimeSnapshot snapshot = snapshot(periodicContractSource(), "b3-temporal-fidelity-default");
        FutureRouteService service = new FutureRouteService(
                new FutureRouteEvaluator(new DefaultScenarioBundleResolver(), FIXED_CLOCK),
                new InMemoryEphemeralRouteResultStore(FIXED_CLOCK)
        );

        FutureRouteResultSet offDay = service.evaluate(snapshot, requestAt("2026-03-22T07:00:00Z"));
        FutureRouteResultSet preRushWeekday = service.evaluate(snapshot, requestAt("2026-03-23T07:00:00Z"));
        FutureRouteResultSet rushWeekday = service.evaluate(snapshot, requestAt("2026-03-23T08:00:00Z"));

        assertEquals(List.of("N0", "N1", "N3"), offDay.getExpectedRoute().getRoute().getPathExternalNodeIds());
        assertEquals(List.of("N0", "N1", "N3"), offDay.getRobustRoute().getRoute().getPathExternalNodeIds());
        assertEquals(List.of("N0", "N1", "N3"), preRushWeekday.getExpectedRoute().getRoute().getPathExternalNodeIds());
        assertEquals(List.of("N0", "N1", "N3"), preRushWeekday.getRobustRoute().getRoute().getPathExternalNodeIds());
        assertEquals(List.of("N0", "N2", "N3"), rushWeekday.getExpectedRoute().getRoute().getPathExternalNodeIds());
        assertEquals(List.of("N0", "N2", "N3"), rushWeekday.getRobustRoute().getRoute().getPathExternalNodeIds());
        assertEquals(1, offDay.getScenarioBundle().getScenarios().size());
        assertEquals(2, preRushWeekday.getScenarioBundle().getScenarios().size());
        assertEquals("periodic_peak", preRushWeekday.getScenarioBundle().getScenarios().get(1).getScenarioId());
        assertEquals(1, rushWeekday.getScenarioBundle().getScenarios().size());
        assertEquals("baseline", rushWeekday.getRobustRoute().getDominantScenarioId());
    }

    @Test
    @DisplayName("Fresh recurring evidence reroutes near-horizon serving while stale equivalent stays on the baseline corridor")
    void testFreshRecurringEvidenceShiftsNearHorizonRouteWhileStaleEquivalentStaysBaseline() {
        FutureRouteService service = new FutureRouteService(
                new FutureRouteEvaluator(new DefaultScenarioBundleResolver(), FIXED_CLOCK),
                new InMemoryEphemeralRouteResultStore(FIXED_CLOCK)
        );

        FutureRouteResultSet fresh = service.evaluate(
                snapshot(recencySensitivePeriodicSource(Instant.parse("2026-03-23T06:55:00Z").getEpochSecond()), "b4-fresh-fidelity"),
                requestAt("2026-03-23T07:00:00Z")
        );
        FutureRouteResultSet stale = service.evaluate(
                snapshot(recencySensitivePeriodicSource(Instant.parse("2026-03-22T00:00:00Z").getEpochSecond()), "b4-stale-fidelity"),
                requestAt("2026-03-23T07:00:00Z")
        );

        assertEquals(List.of("N0", "N2", "N3"), fresh.getExpectedRoute().getRoute().getPathExternalNodeIds());
        assertEquals(List.of("N0", "N1", "N3"), stale.getExpectedRoute().getRoute().getPathExternalNodeIds());
        assertEquals(2, fresh.getScenarioBundle().getScenarios().size());
        assertEquals(2, stale.getScenarioBundle().getScenarios().size());

        ScenarioDefinition freshScenario = fresh.getScenarioBundle().getScenarios().get(1);
        ScenarioDefinition staleScenario = stale.getScenarioBundle().getScenarios().get(1);
        assertNotNull(freshScenario.getProbabilityAudit());
        assertNotNull(staleScenario.getProbabilityAudit());
        assertTrue(freshScenario.getProbability() > staleScenario.getProbability());
        assertTrue(freshScenario.getProbabilityAudit().getFreshnessWeight() > staleScenario.getProbabilityAudit().getFreshnessWeight());
        assertEquals(
                freshScenario.getProbability(),
                freshScenario.getProbabilityAudit().getAdjustedProbability(),
                1.0e-9d
        );
    }

    @Test
    @DisplayName("Directional asymmetry survives scenario evaluation and served route selection")
    void testDirectionalAsymmetrySurvivesFutureServing() {
        FutureRouteService service = new FutureRouteService(
                new FutureRouteEvaluator(baselineResolver(), FIXED_CLOCK),
                new InMemoryEphemeralRouteResultStore(FIXED_CLOCK)
        );
        TopologyRuntimeSnapshot snapshot = snapshot(directionalDivergenceContractSource(), "b5-directional-fidelity");

        FutureRouteResultSet forward = service.evaluate(snapshot, requestAt("N0", "N1", "2026-03-23T08:00:00Z"));
        FutureRouteResultSet reverse = service.evaluate(snapshot, requestAt("N1", "N0", "2026-03-23T08:00:00Z"));

        assertEquals(List.of("N0", "N2", "N1"), forward.getExpectedRoute().getRoute().getPathExternalNodeIds());
        assertEquals(List.of("N1", "N0"), reverse.getExpectedRoute().getRoute().getPathExternalNodeIds());
        assertEquals(RouteSelectionProvenance.SCENARIO_OPTIMAL, forward.getExpectedRoute().getRouteSelectionProvenance());
        assertEquals(RouteSelectionProvenance.SCENARIO_OPTIMAL, reverse.getExpectedRoute().getRouteSelectionProvenance());
    }

    @Test
    @DisplayName("Density calibration keeps aggregate compromise winners visible in served output")
    void testDensityCalibrationPreservesAggregateCompromiseWinner() {
        FutureRouteService service = new FutureRouteService(
                new FutureRouteEvaluator(compromiseContractResolver(), FIXED_CLOCK),
                new InMemoryEphemeralRouteResultStore(FIXED_CLOCK)
        );
        TopologyRuntimeSnapshot snapshot = snapshot(aggregateDensityContractSource(), "b5-density-fidelity");

        FutureRouteResultSet resultSet = service.evaluate(snapshot, requestAt("N0", "N4", "2026-03-23T07:00:00Z"));

        assertEquals(List.of("N0", "N3", "N4"), resultSet.getExpectedRoute().getRoute().getPathExternalNodeIds());
        assertEquals(List.of("N0", "N3", "N4"), resultSet.getRobustRoute().getRoute().getPathExternalNodeIds());
        assertEquals(RouteSelectionProvenance.AGGREGATE_OBJECTIVE, resultSet.getExpectedRoute().getRouteSelectionProvenance());
        assertEquals(RouteSelectionProvenance.AGGREGATE_OBJECTIVE, resultSet.getRobustRoute().getRouteSelectionProvenance());
        assertEquals(CandidateDensityClass.HIGH_DENSITY, resultSet.getCandidateDensityCalibrationReport().getDensityClass());
        assertTrue(resultSet.getCandidateDensityCalibrationReport().isExpectedRouteAggregateOnly());
        assertTrue(resultSet.getCandidateDensityCalibrationReport().isRobustRouteAggregateOnly());
    }

    private ScenarioBundleResolver periodicContractResolver() {
        return (request, baseCostEngine, temporalContext, topologyVersion, quarantineSnapshot, clock) -> {
            Instant now = clock.instant();
            long validUntilTicks = request.getDepartureTicks() + request.getHorizonTicks();
            return ScenarioBundle.builder()
                    .scenarioBundleId("bundle-" + request.getDepartureTicks())
                    .generatedAt(now)
                    .validUntil(now.plus(Duration.ofMinutes(15)))
                    .horizonTicks(request.getHorizonTicks())
                    .topologyVersion(topologyVersion)
                    .quarantineSnapshotId(quarantineSnapshot.snapshotId())
                    .scenario(ScenarioDefinition.builder()
                            .scenarioId("baseline")
                            .label("baseline")
                            .probability(0.7d)
                            .build())
                    .scenario(ScenarioDefinition.builder()
                            .scenarioId("alt_delay")
                            .label("alt_delay")
                            .probability(0.3d)
                            .explanationTag("alt_delay")
                            .liveUpdate(LiveUpdate.of(1, 0.75f, validUntilTicks))
                            .liveUpdate(LiveUpdate.of(3, 0.75f, validUntilTicks))
                            .build())
                    .build();
        };
    }

    private ScenarioBundleResolver baselineResolver() {
        return (request, baseCostEngine, temporalContext, topologyVersion, quarantineSnapshot, clock) ->
                ScenarioBundle.builder()
                        .scenarioBundleId("baseline-" + request.getDepartureTicks())
                        .generatedAt(clock.instant())
                        .validUntil(clock.instant().plus(Duration.ofMinutes(15)))
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

    private ScenarioBundleResolver compromiseContractResolver() {
        return (request, baseCostEngine, temporalContext, topologyVersion, quarantineSnapshot, clock) -> {
            long validUntilTicks = request.getDepartureTicks() + request.getHorizonTicks();
            return ScenarioBundle.builder()
                    .scenarioBundleId("density-" + request.getDepartureTicks())
                    .generatedAt(clock.instant())
                    .validUntil(clock.instant().plus(Duration.ofMinutes(15)))
                    .horizonTicks(request.getHorizonTicks())
                    .topologyVersion(topologyVersion)
                    .quarantineSnapshotId(quarantineSnapshot.snapshotId())
                    .scenario(ScenarioDefinition.builder()
                            .scenarioId("b_slow")
                            .label("b_slow")
                            .probability(0.5d)
                            .liveUpdate(LiveUpdate.of(1, 0.1f, validUntilTicks))
                            .liveUpdate(LiveUpdate.of(4, 0.1f, validUntilTicks))
                            .build())
                    .scenario(ScenarioDefinition.builder()
                            .scenarioId("a_slow")
                            .label("a_slow")
                            .probability(0.5d)
                            .liveUpdate(LiveUpdate.of(0, 0.1f, validUntilTicks))
                            .liveUpdate(LiveUpdate.of(3, 0.1f, validUntilTicks))
                            .build())
                    .build();
        };
    }

    private FutureRouteRequest requestAt(String departureIsoInstant) {
        return requestAt("N0", "N3", departureIsoInstant);
    }

    private FutureRouteRequest requestAt(String sourceExternalId, String targetExternalId, String departureIsoInstant) {
        return FutureRouteRequest.builder()
                .routeRequest(RouteRequest.builder()
                        .sourceExternalId(sourceExternalId)
                        .targetExternalId(targetExternalId)
                        .departureTicks(Instant.parse(departureIsoInstant).getEpochSecond())
                        .build())
                .horizonTicks(Duration.ofHours(2).toSeconds())
                .resultTtl(Duration.ofMinutes(15))
                .topKAlternatives(2)
                .build();
    }

    private TopologyRuntimeSnapshot snapshot(TopologyModelSource source, String topologyId) {
        TopologyRuntimeFactory runtimeFactory = new TopologyRuntimeFactory(runtimeTemplate());
        TopologyVersion topologyVersion = TopologyVersion.builder()
                .modelVersion(source.getModelVersion())
                .topologyVersion(topologyId)
                .generatedAt(FIXED_CLOCK.instant())
                .sourceDataLineageHash("lineage-" + topologyId)
                .changeSetHash("changes-" + topologyId)
                .build();
        return runtimeFactory.buildSnapshot(source, topologyVersion, 0L, null);
    }

    private TopologyRuntimeTemplate runtimeTemplate() {
        return TopologyRuntimeTemplate.builder()
                .executionRuntimeConfig(ExecutionRuntimeConfig.dijkstra())
                .addressingRuntimeConfig(AddressingRuntimeConfig.defaultRuntime())
                .temporalRuntimeConfig(TemporalRuntimeConfig.calendarUtc())
                .transitionRuntimeConfig(TransitionRuntimeConfig.edgeBased())
                .bucketSizeSeconds(3_600)
                .liveOverlayCapacity(64)
                .build();
    }

    private TopologyModelSource periodicContractSource() {
        return TopologyModelSource.builder()
                .modelVersion("b3-temporal-fidelity-source")
                .profileTimezone("UTC")
                .profile(weekdayRushProfile(1))
                .profile(flatProfile(2, 1.0f))
                .node(node("N0", 0.0d, 0.0d))
                .node(node("N1", 1.0d, 0.0d))
                .node(node("N2", 1.0d, 1.0d))
                .node(node("N3", 2.0d, 0.0d))
                .edge(edge("E01", "N0", "N1", 1.0f, 1))
                .edge(edge("E02", "N0", "N2", 1.5f, 2))
                .edge(edge("E13", "N1", "N3", 1.0f, 1))
                .edge(edge("E23", "N2", "N3", 1.5f, 2))
                .build();
    }

    private TopologyModelSource directionalDivergenceContractSource() {
        return TopologyModelSource.builder()
                .modelVersion("b5-directional-fidelity-source")
                .profileTimezone("UTC")
                .profile(peakProfile(1, 4.0f))
                .profile(flatProfile(2, 1.0f))
                .profile(flatProfile(3, 1.0f))
                .node(node("N0", 0.0d, 0.0d))
                .node(node("N1", 2.0d, 0.0d))
                .node(node("N2", 1.0d, 1.0d))
                .edge(edge("E01", "N0", "N1", 10.0f, 1))
                .edge(edge("E10", "N1", "N0", 10.0f, 2))
                .edge(edge("E02", "N0", "N2", 8.0f, 3))
                .edge(edge("E21", "N2", "N1", 8.0f, 3))
                .edge(edge("E12", "N1", "N2", 8.0f, 3))
                .edge(edge("E20", "N2", "N0", 8.0f, 3))
                .build();
    }

    private TopologyModelSource aggregateDensityContractSource() {
        return TopologyModelSource.builder()
                .modelVersion("b5-density-fidelity-source")
                .profileTimezone("UTC")
                .profile(flatProfile(1, 1.0f))
                .node(node("N0", 0.0d, 0.0d))
                .node(node("N1", 1.0d, 1.0d))
                .node(node("N2", 1.0d, 0.0d))
                .node(node("N3", 1.0d, -1.0d))
                .node(node("N4", 2.0d, 0.0d))
                .edge(edge("E01", "N0", "N1", 5.0f, 1))
                .edge(edge("E02", "N0", "N2", 5.0f, 1))
                .edge(edge("E03", "N0", "N3", 20.0f, 1))
                .edge(edge("E14", "N1", "N4", 5.0f, 1))
                .edge(edge("E24", "N2", "N4", 5.0f, 1))
                .edge(edge("E34", "N3", "N4", 20.0f, 1))
                .build();
    }

    private TopologyModelSource recencySensitivePeriodicSource(long lastObservedAtTicks) {
        return TopologyModelSource.builder()
                .modelVersion("b4-recency-fidelity-" + lastObservedAtTicks)
                .profileTimezone("UTC")
                .profile(recencySensitivePeriodicProfile(1, lastObservedAtTicks))
                .profile(flatProfile(2, 1.0f))
                .node(node("N0", 0.0d, 0.0d))
                .node(node("N1", 1.0d, 0.0d))
                .node(node("N2", 1.0d, 1.0d))
                .node(node("N3", 2.0d, 0.0d))
                .edge(edge("E01", "N0", "N1", 3_600.0f, 1))
                .edge(edge("E02", "N0", "N2", 6_500.0f, 2))
                .edge(edge("E13", "N1", "N3", 3_600.0f, 1))
                .edge(edge("E23", "N2", "N3", 6_500.0f, 2))
                .build();
    }

    private TopologyModelSource.ProfileDefinition weekdayRushProfile(int profileId) {
        TopologyModelSource.ProfileDefinition.ProfileDefinitionBuilder builder = TopologyModelSource.ProfileDefinition.builder()
                .profileId(profileId)
                .dayMask(0x1F)
                .multiplier(1.0f)
                .recurringSignalFlavor(ProfileRecurrenceCalibrationStore.SignalFlavor.ROUTINE_PERIODIC)
                .recurringConfidence(0.35f)
                .recurringObservationCount(9)
                .lastObservedAtTicks(Instant.parse("2026-03-23T06:30:00Z").getEpochSecond());
        for (int hour = 0; hour < 24; hour++) {
            builder.bucket(hour == 8 ? 5.0f : 1.0f);
        }
        return builder.build();
    }

    private TopologyModelSource.ProfileDefinition recencySensitivePeriodicProfile(int profileId, long lastObservedAtTicks) {
        TopologyModelSource.ProfileDefinition.ProfileDefinitionBuilder builder = TopologyModelSource.ProfileDefinition.builder()
                .profileId(profileId)
                .dayMask(0x1F)
                .multiplier(1.0f)
                .recurringSignalFlavor(ProfileRecurrenceCalibrationStore.SignalFlavor.ROUTINE_PERIODIC)
                .recurringConfidence(0.35f)
                .recurringObservationCount(9)
                .lastObservedAtTicks(lastObservedAtTicks);
        for (int hour = 0; hour < 24; hour++) {
            builder.bucket(hour == 8 ? 2.0f : 1.0f);
        }
        return builder.build();
    }

    private TopologyModelSource.ProfileDefinition peakProfile(int profileId, float peakMultiplier) {
        TopologyModelSource.ProfileDefinition.ProfileDefinitionBuilder builder = TopologyModelSource.ProfileDefinition.builder()
                .profileId(profileId)
                .dayMask(0x1F)
                .multiplier(1.0f);
        for (int hour = 0; hour < 24; hour++) {
            builder.bucket(hour == 8 ? peakMultiplier : 1.0f);
        }
        return builder.build();
    }

    private TopologyModelSource.ProfileDefinition flatProfile(int profileId, float bucketValue) {
        TopologyModelSource.ProfileDefinition.ProfileDefinitionBuilder builder = TopologyModelSource.ProfileDefinition.builder()
                .profileId(profileId)
                .dayMask(0x7F)
                .multiplier(1.0f);
        for (int hour = 0; hour < 24; hour++) {
            builder.bucket(bucketValue);
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
