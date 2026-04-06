package org.Aayush.routing.future;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.Aayush.routing.core.FutureRouteEvaluator;
import org.Aayush.routing.core.RouteCore;
import org.Aayush.routing.core.RouteRequest;
import org.Aayush.routing.core.RouteResponse;
import org.Aayush.routing.execution.ExecutionRuntimeConfig;
import org.Aayush.routing.overlay.LiveUpdate;
import org.Aayush.routing.testutil.RoutingFixtureFactory;
import org.Aayush.routing.topology.FailureQuarantine;
import org.Aayush.routing.topology.TopologyRuntimeSnapshot;
import org.Aayush.routing.topology.TopologyVersion;
import org.Aayush.routing.traits.addressing.AddressingRuntimeConfig;
import org.Aayush.routing.traits.temporal.TemporalRuntimeConfig;
import org.Aayush.routing.traits.transition.TransitionRuntimeConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@DisplayName("Complex Scenario Dataset Export Tests")
class ComplexScenarioDatasetExportTest {
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-03-21T00:00:00Z"), ZoneOffset.UTC);
    private static final String GENERATED_BY =
            "src/test/java/org/Aayush/routing/future/ComplexScenarioDatasetExportTest.java";
    private static final String REGEN_COMMAND =
            "scripts/export_complex_scenario_datasets.sh";
    private static final Path INPUT_PATH = Paths.get(
            "docs",
            "scenarios",
            "complex_route_scenarios.json"
    );
    private static final Path RESULT_PATH = Paths.get(
            "docs",
            "scenarios",
            "complex_route_results.json"
    );

    @Test
    @DisplayName("Checked-in complex scenario artifacts stay aligned with the routing engine")
    void testCheckedInComplexScenarioArtifactsStayInSync() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        ExportArtifacts artifacts = buildArtifacts();

        if (Boolean.getBoolean("taro.writeComplexScenarioArtifacts")) {
            writeJson(objectMapper, INPUT_PATH, artifacts.inputs());
            writeJson(objectMapper, RESULT_PATH, artifacts.results());
        }

        assertJsonEquals(objectMapper, INPUT_PATH, artifacts.inputs());
        assertJsonEquals(objectMapper, RESULT_PATH, artifacts.results());
        assertKeySemantics(artifacts.runtimes());
    }

    private ExportArtifacts buildArtifacts() {
        List<ScenarioCaseRuntime> runtimes = List.of(
                evaluate(incidentSplitTemplate()),
                evaluate(aggregateCompromiseTemplate()),
                evaluate(twoHourBranchReversalTemplate()),
                evaluate(multiStageBranchTemplate())
        );

        List<ScenarioInputCase> inputCases = new ArrayList<>(runtimes.size());
        List<ScenarioResultCase> resultCases = new ArrayList<>(runtimes.size());
        for (ScenarioCaseRuntime runtime : runtimes) {
            inputCases.add(toInputCase(runtime));
            resultCases.add(toResultCase(runtime));
        }

        ScenarioInputDataset inputs = new ScenarioInputDataset(
                "complex-route-scenarios-v1",
                "Complex future-routing scenario inputs generated from real engine fixtures.",
                FIXED_CLOCK.instant().toString(),
                GENERATED_BY,
                REGEN_COMMAND,
                inputCases
        );
        ScenarioResultDataset results = new ScenarioResultDataset(
                "complex-route-results-v1",
                "Evaluated route results for the complex future-routing scenarios.",
                FIXED_CLOCK.instant().toString(),
                GENERATED_BY,
                REGEN_COMMAND,
                resultCases
        );
        return new ExportArtifacts(inputs, results, runtimes);
    }

    private ScenarioCaseRuntime evaluate(ScenarioCaseTemplate template) {
        RouteCore routeCore = createRouteCore(template.fixture());
        TopologyRuntimeSnapshot snapshot = snapshot(routeCore, template.topologyId());
        FutureRouteService service = new FutureRouteService(
                new FutureRouteEvaluator(template.resolver(), FIXED_CLOCK),
                new InMemoryEphemeralRouteResultStore(FIXED_CLOCK)
        );
        FutureRouteResultSet resultSet = service.evaluate(snapshot, template.request());
        return new ScenarioCaseRuntime(template, resultSet);
    }

    private ScenarioInputCase toInputCase(ScenarioCaseRuntime runtime) {
        FutureRouteResultSet resultSet = runtime.resultSet();
        ScenarioBundle bundle = resultSet.getScenarioBundle();
        ArrayList<ScenarioOptionExport> scenarios = new ArrayList<>(bundle.getScenarios().size());
        for (ScenarioDefinition scenario : bundle.getScenarios()) {
            scenarios.add(new ScenarioOptionExport(
                    scenario.getScenarioId(),
                    scenario.getLabel(),
                    scenario.getProbability(),
                    scenario.getExplanationTags(),
                    liveUpdates(scenario.getLiveUpdates())
            ));
        }

        return new ScenarioInputCase(
                runtime.template().caseId(),
                runtime.template().title(),
                runtime.template().focus(),
                runtime.template().topologyId(),
                requestExport(runtime.template().request()),
                new ScenarioBundleExport(
                        bundle.getScenarioBundleId(),
                        bundle.getGeneratedAt().toString(),
                        bundle.getValidUntil().toString(),
                        bundle.getHorizonTicks(),
                        bundle.getTopologyVersion().getTopologyVersion(),
                        bundle.getQuarantineSnapshotId(),
                        scenarios
                ),
                decisionStageInputs(runtime.template().decisionStages())
        );
    }

    private ScenarioResultCase toResultCase(ScenarioCaseRuntime runtime) {
        FutureRouteResultSet resultSet = runtime.resultSet();
        RetainedRouteResultView.Summary summary = RetainedRouteResultView.summaryOf(resultSet);
        RetainedRouteResultView.Detail detail = RetainedRouteResultView.detailOf(resultSet);
        List<RouteChoiceExport> routeChoices = routeChoices(summary);

        return new ScenarioResultCase(
                runtime.template().caseId(),
                runtime.template().title(),
                runtime.template().focus(),
                summaryExport(summary),
                detailExport(detail),
                routeChoices,
                decisionStageResults(runtime.template().decisionStages(), routeChoices, detail),
                uniquePathCatalog(routeChoices, detail)
        );
    }

    private List<DecisionStageInput> decisionStageInputs(
            List<DecisionStageTemplate> stageTemplates
    ) {
        ArrayList<DecisionStageInput> stages = new ArrayList<>(stageTemplates.size());
        for (DecisionStageTemplate stage : stageTemplates) {
            ArrayList<StageOptionInput> options = new ArrayList<>(stage.options().size());
            for (StageOptionTemplate option : stage.options()) {
                options.add(new StageOptionInput(
                        option.optionId(),
                        option.label(),
                        option.notes(),
                        option.matchingPaths()
                ));
            }
            stages.add(new DecisionStageInput(
                    stage.stageId(),
                    stage.label(),
                    stage.description(),
                    options
            ));
        }
        return stages;
    }

    private List<DecisionStageResult> decisionStageResults(
            List<DecisionStageTemplate> stageTemplates,
            List<RouteChoiceExport> routeChoices,
            RetainedRouteResultView.Detail detail
    ) {
        ArrayList<DecisionStageResult> stages = new ArrayList<>(stageTemplates.size());
        for (DecisionStageTemplate stage : stageTemplates) {
            ArrayList<StageOptionResult> options = new ArrayList<>(stage.options().size());
            for (StageOptionTemplate option : stage.options()) {
                ArrayList<String> selectedBy = new ArrayList<>();
                for (RouteChoiceExport routeChoice : routeChoices) {
                    if (matchesAnyPath(routeChoice.pathNodes(), option.matchingPaths())) {
                        selectedBy.add(routeChoice.role());
                    }
                }

                ArrayList<String> winningScenarios = new ArrayList<>();
                for (FutureRouteScenarioResult scenarioResult : detail.getScenarioResults()) {
                    if (matchesAnyPath(
                            scenarioResult.getRoute().getPathExternalNodeIds(),
                            option.matchingPaths()
                    )) {
                        winningScenarios.add(scenarioResult.getScenarioId());
                    }
                }

                options.add(new StageOptionResult(
                        option.optionId(),
                        option.label(),
                        option.notes(),
                        option.matchingPaths(),
                        selectedBy,
                        winningScenarios
                ));
            }
            stages.add(new DecisionStageResult(
                    stage.stageId(),
                    stage.label(),
                    stage.description(),
                    options
            ));
        }
        return stages;
    }

    private List<PathOptionExport> uniquePathCatalog(
            List<RouteChoiceExport> routeChoices,
            RetainedRouteResultView.Detail detail
    ) {
        ArrayList<PathOptionExport> catalog = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();

        for (RouteChoiceExport routeChoice : routeChoices) {
            String key = pathKey(routeChoice.pathNodes());
            if (seen.add(key)) {
                catalog.add(new PathOptionExport(
                        "path-" + catalog.size(),
                        routeChoice.pathNodes(),
                        rolesForPath(key, routeChoices),
                        scenarioIdsForPath(key, detail)
                ));
            }
        }
        return catalog;
    }

    private SummaryExport summaryExport(RetainedRouteResultView.Summary summary) {
        return new SummaryExport(
                summary.getCreatedAt().toString(),
                summary.getExpiresAt().toString(),
                requestExport(summary.getRequest()),
                summary.getTopologyVersion().getModelVersion(),
                summary.getTopologyVersion().getTopologyVersion(),
                summary.getQuarantineSnapshotId(),
                summary.getScenarioBundleId(),
                summary.getScenarioCount(),
                routeChoice("expectedRoute", summary.getExpectedRoute()),
                routeChoice("robustRoute", summary.getRobustRoute()),
                alternativeChoices(summary.getAlternatives())
        );
    }

    private DetailExport detailExport(RetainedRouteResultView.Detail detail) {
        ArrayList<ScenarioOutcomeExport> scenarioResults =
                new ArrayList<>(detail.getScenarioResults().size());
        for (FutureRouteScenarioResult scenarioResult : detail.getScenarioResults()) {
            RouteResponse route = scenarioResult.getRoute();
            scenarioResults.add(new ScenarioOutcomeExport(
                    scenarioResult.getScenarioId(),
                    scenarioResult.getLabel(),
                    scenarioResult.getProbability(),
                    route.isReachable(),
                    route.getTotalCost(),
                    route.getArrivalTicks(),
                    route.getPathExternalNodeIds(),
                    scenarioResult.getExplanationTags()
            ));
        }

        CandidateDensityCalibrationReport report =
                detail.getCandidateDensityCalibrationReport();
        CandidateDensityExport densityExport = new CandidateDensityExport(
                report.getPolicyId(),
                report.getScenarioCount(),
                report.getScenarioOptimalRouteCount(),
                report.getUniqueScenarioOptimalRouteCount(),
                report.getUniqueCandidateRouteCount(),
                report.getAggregateAddedCandidateCount(),
                report.isExpectedRouteAggregateOnly(),
                report.isRobustRouteAggregateOnly(),
                report.getSelectedAlternativeCount(),
                report.getScenarioCoverageRatio(),
                report.getCandidateCoverageRatio(),
                report.getAggregateExpansionRatio(),
                report.getDensityClass().name()
        );

        return new DetailExport(
                detail.getScenarioBundleGeneratedAt().toString(),
                detail.getScenarioBundleValidUntil().toString(),
                detail.getScenarioBundleHorizonTicks(),
                densityExport,
                scenarioResults
        );
    }

    private List<RouteChoiceExport> routeChoices(RetainedRouteResultView.Summary summary) {
        ArrayList<RouteChoiceExport> choices = new ArrayList<>();
        choices.add(routeChoice("expectedRoute", summary.getExpectedRoute()));
        choices.add(routeChoice("robustRoute", summary.getRobustRoute()));
        choices.addAll(alternativeChoices(summary.getAlternatives()));
        return choices;
    }

    private List<RouteChoiceExport> alternativeChoices(
            List<ScenarioRouteSelection> alternatives
    ) {
        ArrayList<RouteChoiceExport> choices = new ArrayList<>(alternatives.size());
        for (int index = 0; index < alternatives.size(); index++) {
            choices.add(routeChoice(
                    "alternative" + (index + 1),
                    alternatives.get(index)
            ));
        }
        return choices;
    }

    private RouteChoiceExport routeChoice(
            String role,
            ScenarioRouteSelection selection
    ) {
        return new RouteChoiceExport(
                role,
                selection.getRoute().isReachable(),
                selection.getRoute().getPathExternalNodeIds(),
                selection.getExpectedCost(),
                selection.getP50Cost(),
                selection.getP90Cost(),
                selection.getMinCost(),
                selection.getMaxCost(),
                selection.getOptimalityProbability(),
                selection.getExpectedRegret(),
                selection.getDominantScenarioId(),
                selection.getDominantScenarioProbability(),
                selection.getDominantScenarioLabel(),
                selection.getRouteSelectionProvenance().name(),
                selection.getExplanationTags()
        );
    }

    private RequestExport requestExport(FutureRouteRequest request) {
        return new RequestExport(
                request.getRouteRequest().getSourceExternalId(),
                request.getRouteRequest().getTargetExternalId(),
                request.getRouteRequest().getDepartureTicks(),
                request.getHorizonTicks(),
                request.getPreferredObjective().name(),
                request.getTopKAlternatives(),
                request.getResultTtl().getSeconds()
        );
    }

    private List<LiveUpdateExport> liveUpdates(List<LiveUpdate> updates) {
        ArrayList<LiveUpdateExport> exported = new ArrayList<>(updates.size());
        for (LiveUpdate update : updates) {
            exported.add(new LiveUpdateExport(
                    update.edgeId(),
                    update.speedFactor(),
                    update.validFromTicks(),
                    update.validUntilTicks()
            ));
        }
        return exported;
    }

    private List<String> rolesForPath(
            String pathKey,
            List<RouteChoiceExport> routeChoices
    ) {
        ArrayList<String> roles = new ArrayList<>();
        for (RouteChoiceExport routeChoice : routeChoices) {
            if (Objects.equals(pathKey(routeChoice.pathNodes()), pathKey)) {
                roles.add(routeChoice.role());
            }
        }
        return roles;
    }

    private List<String> scenarioIdsForPath(
            String pathKey,
            RetainedRouteResultView.Detail detail
    ) {
        ArrayList<String> scenarioIds = new ArrayList<>();
        for (FutureRouteScenarioResult scenarioResult : detail.getScenarioResults()) {
            if (Objects.equals(
                    pathKey(scenarioResult.getRoute().getPathExternalNodeIds()),
                    pathKey
            )) {
                scenarioIds.add(scenarioResult.getScenarioId());
            }
        }
        return scenarioIds;
    }

    private boolean matchesAnyPath(
            List<String> candidatePath,
            List<List<String>> matchingPaths
    ) {
        String candidateKey = pathKey(candidatePath);
        for (List<String> matchingPath : matchingPaths) {
            if (Objects.equals(candidateKey, pathKey(matchingPath))) {
                return true;
            }
        }
        return false;
    }

    private String pathKey(List<String> pathNodes) {
        return String.join(">", pathNodes);
    }

    private void assertJsonEquals(
            ObjectMapper objectMapper,
            Path path,
            Object expectedArtifact
    ) throws Exception {
        assertTrue(Files.exists(path), "Missing checked-in artifact: " + path);
        JsonNode expected = objectMapper.readTree(
                objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(expectedArtifact)
        );
        JsonNode actual = objectMapper.readTree(Files.readString(path));
        assertEquals(expected, actual, "Artifact drift at " + path);
    }

    private void writeJson(
            ObjectMapper objectMapper,
            Path path,
            Object artifact
    ) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(
                path,
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(artifact)
                        + System.lineSeparator()
        );
    }

    private void assertKeySemantics(List<ScenarioCaseRuntime> runtimes) {
        FutureRouteResultSet incident = runtimes.get(0).resultSet();
        assertEquals(
                List.of("N0", "N1", "N3"),
                incident.getExpectedRoute().getRoute().getPathExternalNodeIds()
        );
        assertEquals(
                List.of("N0", "N2", "N3"),
                incident.getRobustRoute().getRoute().getPathExternalNodeIds()
        );

        FutureRouteResultSet compromise = runtimes.get(1).resultSet();
        assertEquals(
                List.of("N0", "N3", "N4"),
                compromise.getExpectedRoute().getRoute().getPathExternalNodeIds()
        );
        assertEquals(
                RouteSelectionProvenance.AGGREGATE_OBJECTIVE,
                compromise.getExpectedRoute().getRouteSelectionProvenance()
        );

        FutureRouteResultSet branchReversal = runtimes.get(2).resultSet();
        assertEquals(
                List.of("N0", "N2", "N3"),
                branchReversal.getExpectedRoute().getRoute().getPathExternalNodeIds()
        );
        assertTrue(branchReversal.getAlternatives().stream().anyMatch(selection ->
                selection.getRoute().getPathExternalNodeIds().equals(
                        List.of("N0", "N1", "N3")
                )));

        FutureRouteResultSet multiStage = runtimes.get(3).resultSet();
        assertEquals(
                List.of("N0", "N1", "N4", "N6", "N7"),
                multiStage.getExpectedRoute().getRoute().getPathExternalNodeIds()
        );
        assertEquals(
                List.of("N0", "N3", "N4", "N6", "N7"),
                multiStage.getRobustRoute().getRoute().getPathExternalNodeIds()
        );
        assertTrue(multiStage.getAlternatives().stream().anyMatch(selection ->
                selection.getRoute().getPathExternalNodeIds().equals(
                        List.of("N0", "N1", "N4", "N5", "N7")
                )));
        assertTrue(multiStage.getAlternatives().stream().anyMatch(selection ->
                selection.getRoute().getPathExternalNodeIds().equals(
                        List.of("N0", "N2", "N4", "N5", "N7")
                )));
    }

    private ScenarioCaseTemplate incidentSplitTemplate() {
        return new ScenarioCaseTemplate(
                "incident_split_corridor",
                "Incident Split Corridor",
                "Expected ETA prefers the fast corridor while robust routing"
                        + " pivots to the incident-resilient branch.",
                "topo-incident-split",
                createAlternativeRouteFixture(),
                (
                        request,
                        baseCostEngine,
                        temporalContext,
                        resolvedTopologyVersion,
                        quarantineSnapshot,
                        clock
                ) -> ScenarioBundle.builder()
                        .scenarioBundleId("bundle-incident-split")
                        .generatedAt(FIXED_CLOCK.instant())
                        .validUntil(FIXED_CLOCK.instant().plus(Duration.ofMinutes(10)))
                        .horizonTicks(request.getHorizonTicks())
                        .topologyVersion(resolvedTopologyVersion)
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
                        .build(),
                routeRequest("N0", "N3", 0L, 2),
                List.of(new DecisionStageTemplate(
                        "corridor_selection",
                        "Primary corridor selection",
                        "Choose between the optimistic north branch and the"
                                + " incident-resilient south branch.",
                        List.of(
                                new StageOptionTemplate(
                                        "north_branch",
                                        "North branch",
                                        "Fastest when the baseline holds.",
                                        List.of(List.of("N0", "N1", "N3"))
                                ),
                                new StageOptionTemplate(
                                        "south_branch",
                                        "South branch",
                                        "Safer when the downstream incident lingers.",
                                        List.of(List.of("N0", "N2", "N3"))
                                )
                        )
                ))
        );
    }

    private ScenarioCaseTemplate aggregateCompromiseTemplate() {
        return new ScenarioCaseTemplate(
                "aggregate_compromise_triangle",
                "Aggregate Compromise Triangle",
                "Neither single-scenario optimum is globally best, so the"
                        + " aggregate planner rescues a compromise path.",
                "topo-aggregate-compromise",
                createCompromiseRouteFixture(),
                (
                        request,
                        baseCostEngine,
                        temporalContext,
                        resolvedTopologyVersion,
                        quarantineSnapshot,
                        clock
                ) -> ScenarioBundle.builder()
                        .scenarioBundleId("bundle-aggregate-compromise")
                        .generatedAt(FIXED_CLOCK.instant())
                        .validUntil(FIXED_CLOCK.instant().plus(Duration.ofMinutes(10)))
                        .horizonTicks(request.getHorizonTicks())
                        .topologyVersion(resolvedTopologyVersion)
                        .quarantineSnapshotId(quarantineSnapshot.snapshotId())
                        .scenario(ScenarioDefinition.builder()
                                .scenarioId("b_slow")
                                .label("b_slow")
                                .probability(0.5d)
                                .explanationTag("b_slow")
                                .liveUpdate(LiveUpdate.of(1, 0.1f, 10_000L))
                                .liveUpdate(LiveUpdate.of(4, 0.1f, 10_000L))
                                .build())
                        .scenario(ScenarioDefinition.builder()
                                .scenarioId("a_slow")
                                .label("a_slow")
                                .probability(0.5d)
                                .explanationTag("a_slow")
                                .liveUpdate(LiveUpdate.of(0, 0.1f, 10_000L))
                                .liveUpdate(LiveUpdate.of(3, 0.1f, 10_000L))
                                .build())
                        .build(),
                routeRequest("N0", "N4", 0L, 3),
                List.of(new DecisionStageTemplate(
                        "arterial_family",
                        "Arterial family selection",
                        "The planner can pick either scenario-specific arc or"
                                + " a compromise spine that survives both.",
                        List.of(
                                new StageOptionTemplate(
                                        "north_arc",
                                        "North arc",
                                        "Scenario-optimal when the central arc degrades.",
                                        List.of(List.of("N0", "N1", "N4"))
                                ),
                                new StageOptionTemplate(
                                        "central_arc",
                                        "Central arc",
                                        "Scenario-optimal when the north arc degrades.",
                                        List.of(List.of("N0", "N2", "N4"))
                                ),
                                new StageOptionTemplate(
                                        "compromise_spine",
                                        "Compromise spine",
                                        "Aggregate-only path that limits regret across both futures.",
                                        List.of(List.of("N0", "N3", "N4"))
                                )
                        )
                ))
        );
    }

    private ScenarioCaseTemplate twoHourBranchReversalTemplate() {
        long departureTicks = Instant.parse("2026-03-23T00:00:00Z").getEpochSecond();
        long shiftTicks = departureTicks + Duration.ofHours(2).toSeconds();
        long validUntilTicks = departureTicks + Duration.ofHours(6).toSeconds();
        return new ScenarioCaseTemplate(
                "two_hour_branch_reversal",
                "Two-Hour Branch Reversal",
                "The departure snapshot can make route 1 look like a 2.5-hour"
                        + " winner versus route 2 at 4.0 hours, but the future"
                        + " shift flips that ranking: route 1 grows to 5.0 hours"
                        + " after the two-hour buildup point while route 2 clears"
                        + " down to 3.0 hours.",
                "topo-two-hour-branch-reversal",
                createTwoHourBranchReversalFixture(),
                (
                        request,
                        baseCostEngine,
                        temporalContext,
                        resolvedTopologyVersion,
                        quarantineSnapshot,
                        clock
                ) -> ScenarioBundle.builder()
                        .scenarioBundleId("bundle-two-hour-branch-reversal")
                        .generatedAt(FIXED_CLOCK.instant())
                        .validUntil(FIXED_CLOCK.instant().plus(Duration.ofMinutes(10)))
                        .horizonTicks(request.getHorizonTicks())
                        .topologyVersion(resolvedTopologyVersion)
                        .quarantineSnapshotId(quarantineSnapshot.snapshotId())
                        .scenario(ScenarioDefinition.builder()
                                .scenarioId("departure_snapshot_freeze")
                                .label("departure_snapshot_freeze")
                                .probability(0.25d)
                                .explanationTag("current_traffic_snapshot")
                                .liveUpdate(LiveUpdate.of(3, 0.5f, departureTicks, validUntilTicks))
                                .build())
                        .scenario(ScenarioDefinition.builder()
                                .scenarioId("future_shift")
                                .label("future_shift")
                                .probability(0.75d)
                                .explanationTag("late_buildup")
                                .explanationTag("downstream_relief")
                                .liveUpdate(LiveUpdate.of(2, 1.0f / 6.0f, shiftTicks, validUntilTicks))
                                .build())
                        .build(),
                routeRequest("N0", "N3", departureTicks, 2),
                List.of(new DecisionStageTemplate(
                        "branch_selection",
                        "Branch selection",
                        "Pick the branch that still looks healthy two hours into the trip,"
                                + " not just the branch that looks healthiest at departure.",
                        List.of(
                                new StageOptionTemplate(
                                        "route_1_buildup_branch",
                                        "Route 1 buildup branch",
                                        "Departure snapshot: 2.5h. Future shift: 5.0h after the downstream buildup activates.",
                                        List.of(List.of("N0", "N1", "N3"))
                                ),
                                new StageOptionTemplate(
                                        "route_2_relief_branch",
                                        "Route 2 relief branch",
                                        "Departure snapshot: 4.0h. Future shift: 3.0h once the downstream slowdown clears.",
                                        List.of(List.of("N0", "N2", "N3"))
                                )
                        )
                ))
        );
    }

    private ScenarioCaseTemplate multiStageBranchTemplate() {
        return new ScenarioCaseTemplate(
                "multi_stage_branch_ladder",
                "Multi-Stage Branch Ladder",
                "A richer bundle exposes both an origin-branch decision and a"
                        + " downstream exit decision in the same result set.",
                "topo-multi-stage",
                createMultiStageFixture(),
                (
                        request,
                        baseCostEngine,
                        temporalContext,
                        resolvedTopologyVersion,
                        quarantineSnapshot,
                        clock
                ) -> ScenarioBundle.builder()
                        .scenarioBundleId("bundle-multi-stage")
                        .generatedAt(FIXED_CLOCK.instant())
                        .validUntil(FIXED_CLOCK.instant().plus(Duration.ofMinutes(10)))
                        .horizonTicks(request.getHorizonTicks())
                        .topologyVersion(resolvedTopologyVersion)
                        .quarantineSnapshotId(quarantineSnapshot.snapshotId())
                        .scenario(ScenarioDefinition.builder()
                                .scenarioId("baseline")
                                .label("baseline")
                                .probability(0.35d)
                                .build())
                        .scenario(ScenarioDefinition.builder()
                                .scenarioId("express_exit_slow")
                                .label("express_exit_slow")
                                .probability(0.25d)
                                .explanationTag("express_exit_slow")
                                .liveUpdate(LiveUpdate.of(6, 0.25f, 10_000L))
                                .build())
                        .scenario(ScenarioDefinition.builder()
                                .scenarioId("north_gate_blocked")
                                .label("north_gate_blocked")
                                .probability(0.20d)
                                .explanationTag("north_gate_blocked")
                                .liveUpdate(LiveUpdate.of(0, 0.2f, 10_000L))
                                .build())
                        .scenario(ScenarioDefinition.builder()
                                .scenarioId("downtown_flood")
                                .label("downtown_flood")
                                .probability(0.20d)
                                .explanationTag("downtown_flood")
                                .liveUpdate(LiveUpdate.of(0, 0.2f, 10_000L))
                                .liveUpdate(LiveUpdate.of(1, 0.2f, 10_000L))
                                .liveUpdate(LiveUpdate.of(6, 0.25f, 10_000L))
                                .build())
                        .build(),
                routeRequest("N0", "N7", 0L, 4),
                List.of(
                        new DecisionStageTemplate(
                                "origin_branch",
                                "Origin branch",
                                "The first stage decides which corridor reaches the merge node.",
                                List.of(
                                        new StageOptionTemplate(
                                                "north_gate",
                                                "North gate",
                                                "Best baseline entry when the northern gate is open.",
                                                List.of(
                                                        List.of("N0", "N1", "N4", "N5", "N7"),
                                                        List.of("N0", "N1", "N4", "N6", "N7")
                                                )
                                        ),
                                        new StageOptionTemplate(
                                                "central_gate",
                                                "Central gate",
                                                "Fallback entry when the north gate is degraded.",
                                                List.of(List.of("N0", "N2", "N4", "N5", "N7"))
                                        ),
                                        new StageOptionTemplate(
                                                "south_gate",
                                                "South gate",
                                                "Most defensive entry when both the north gate and express exit degrade.",
                                                List.of(List.of("N0", "N3", "N4", "N6", "N7"))
                                        )
                                )
                        ),
                        new DecisionStageTemplate(
                                "final_approach",
                                "Final approach",
                                "The second stage chooses between the express exit"
                                        + " and the steadier slow exit.",
                                List.of(
                                        new StageOptionTemplate(
                                                "express_exit",
                                                "Express exit",
                                                "Lower ETA when the express edge remains healthy.",
                                                List.of(
                                                        List.of("N0", "N1", "N4", "N5", "N7"),
                                                        List.of("N0", "N2", "N4", "N5", "N7")
                                                )
                                        ),
                                        new StageOptionTemplate(
                                                "steady_exit",
                                                "Steady exit",
                                                "Higher baseline cost but lower tail-risk under exit congestion.",
                                                List.of(
                                                        List.of("N0", "N1", "N4", "N6", "N7"),
                                                        List.of("N0", "N3", "N4", "N6", "N7")
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private RouteCore createRouteCore(RoutingFixtureFactory.Fixture fixture) {
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

    private TopologyRuntimeSnapshot snapshot(
            RouteCore routeCore,
            String topologyId
    ) {
        TopologyVersion topologyVersion = TopologyVersion.builder()
                .modelVersion("model-v14")
                .topologyVersion(topologyId)
                .generatedAt(FIXED_CLOCK.instant())
                .sourceDataLineageHash("lineage-" + topologyId)
                .changeSetHash("changes-" + topologyId)
                .build();
        return TopologyRuntimeSnapshot.builder()
                .routeCore(routeCore)
                .topologyVersion(topologyVersion)
                .failureQuarantine(new FailureQuarantine("quarantine-" + topologyId))
                .build();
    }

    private FutureRouteRequest routeRequest(
            String sourceExternalId,
            String targetExternalId,
            long departureTicks,
            int topKAlternatives
    ) {
        return FutureRouteRequest.builder()
                .routeRequest(RouteRequest.builder()
                        .sourceExternalId(sourceExternalId)
                        .targetExternalId(targetExternalId)
                        .departureTicks(departureTicks)
                        .build())
                .horizonTicks(3_600L)
                .topKAlternatives(topKAlternatives)
                .preferredObjective(FutureRouteObjective.EXPECTED_ETA)
                .resultTtl(Duration.ofMinutes(10))
                .build();
    }

    private RoutingFixtureFactory.Fixture createAlternativeRouteFixture() {
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
                        1.0d, 1.0d,
                        2.0d, 0.5d
                },
                new RoutingFixtureFactory.ProfileSpec(
                        1,
                        RoutingFixtureFactory.ALL_DAYS_MASK,
                        new float[]{1.0f},
                        1.0f
                )
        );
    }

    private RoutingFixtureFactory.Fixture createCompromiseRouteFixture() {
        return RoutingFixtureFactory.createFixture(
                5,
                new int[]{0, 3, 4, 5, 6, 6},
                new int[]{1, 2, 3, 4, 4, 4},
                new int[]{0, 0, 0, 1, 2, 3},
                new float[]{5.0f, 5.0f, 20.0f, 5.0f, 5.0f, 20.0f},
                new int[]{1, 1, 1, 1, 1, 1},
                new double[]{
                        0.0d, 0.0d,
                        1.0d, 1.0d,
                        1.0d, 0.0d,
                        1.0d, -1.0d,
                        2.0d, 0.0d
                },
                new RoutingFixtureFactory.ProfileSpec(
                        1,
                        RoutingFixtureFactory.ALL_DAYS_MASK,
                        new float[]{1.0f},
                        1.0f
                )
        );
    }

    private RoutingFixtureFactory.Fixture createTwoHourBranchReversalFixture() {
        return RoutingFixtureFactory.createFixture(
                4,
                new int[]{0, 2, 3, 4, 4},
                new int[]{1, 2, 3, 3},
                new int[]{0, 0, 1, 2},
                new float[]{7_200.0f, 7_200.0f, 1_800.0f, 3_600.0f},
                new int[]{1, 1, 1, 1},
                new double[]{
                        0.0d, 0.0d,
                        1.0d, 1.0d,
                        1.0d, -1.0d,
                        2.0d, 0.0d
                },
                new RoutingFixtureFactory.ProfileSpec(
                        1,
                        RoutingFixtureFactory.ALL_DAYS_MASK,
                        new float[]{1.0f},
                        1.0f
                )
        );
    }

    private RoutingFixtureFactory.Fixture createMultiStageFixture() {
        return RoutingFixtureFactory.createFixture(
                8,
                new int[]{0, 3, 4, 5, 6, 8, 9, 10, 10},
                new int[]{1, 2, 3, 4, 4, 4, 5, 6, 7, 7},
                new int[]{0, 0, 0, 1, 2, 3, 4, 4, 5, 6},
                new float[]{2.0f, 4.0f, 6.0f, 2.0f, 1.0f, 2.0f, 1.0f, 3.0f, 2.0f, 1.0f},
                new int[]{1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
                new double[]{
                        0.0d, 0.0d,
                        1.0d, 1.0d,
                        1.0d, 0.0d,
                        1.0d, -1.0d,
                        2.0d, 0.0d,
                        3.0d, 1.0d,
                        3.0d, -1.0d,
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

    private record ExportArtifacts(
            ScenarioInputDataset inputs,
            ScenarioResultDataset results,
            List<ScenarioCaseRuntime> runtimes
    ) {
    }

    private record ScenarioCaseRuntime(
            ScenarioCaseTemplate template,
            FutureRouteResultSet resultSet
    ) {
    }

    private record ScenarioCaseTemplate(
            String caseId,
            String title,
            String focus,
            String topologyId,
            RoutingFixtureFactory.Fixture fixture,
            ScenarioBundleResolver resolver,
            FutureRouteRequest request,
            List<DecisionStageTemplate> decisionStages
    ) {
    }

    private record ScenarioInputDataset(
            String datasetId,
            String description,
            String generatedAt,
            String generatedBy,
            String regenerationCommand,
            List<ScenarioInputCase> cases
    ) {
    }

    private record ScenarioInputCase(
            String caseId,
            String title,
            String focus,
            String topologyId,
            RequestExport request,
            ScenarioBundleExport scenarioBundle,
            List<DecisionStageInput> decisionStages
    ) {
    }

    private record ScenarioBundleExport(
            String scenarioBundleId,
            String generatedAt,
            String validUntil,
            long horizonTicks,
            String topologyVersion,
            String quarantineSnapshotId,
            List<ScenarioOptionExport> scenarios
    ) {
    }

    private record ScenarioOptionExport(
            String scenarioId,
            String label,
            double probability,
            List<String> explanationTags,
            List<LiveUpdateExport> liveUpdates
    ) {
    }

    private record LiveUpdateExport(
            int edgeId,
            float speedFactor,
            long validFromTicks,
            long validUntilTicks
    ) {
    }

    private record DecisionStageInput(
            String stageId,
            String label,
            String description,
            List<StageOptionInput> options
    ) {
    }

    private record StageOptionInput(
            String optionId,
            String label,
            String notes,
            List<List<String>> matchingPaths
    ) {
    }

    private record ScenarioResultDataset(
            String datasetId,
            String description,
            String generatedAt,
            String generatedBy,
            String regenerationCommand,
            List<ScenarioResultCase> cases
    ) {
    }

    private record ScenarioResultCase(
            String caseId,
            String title,
            String focus,
            SummaryExport summary,
            DetailExport detail,
            List<RouteChoiceExport> routeChoices,
            List<DecisionStageResult> decisionStages,
            List<PathOptionExport> pathCatalog
    ) {
    }

    private record SummaryExport(
            String createdAt,
            String expiresAt,
            RequestExport request,
            String modelVersion,
            String topologyVersion,
            String quarantineSnapshotId,
            String scenarioBundleId,
            int scenarioCount,
            RouteChoiceExport expectedRoute,
            RouteChoiceExport robustRoute,
            List<RouteChoiceExport> alternatives
    ) {
    }

    private record DetailExport(
            String scenarioBundleGeneratedAt,
            String scenarioBundleValidUntil,
            long scenarioBundleHorizonTicks,
            CandidateDensityExport candidateDensityCalibrationReport,
            List<ScenarioOutcomeExport> scenarioResults
    ) {
    }

    private record RequestExport(
            String sourceExternalId,
            String targetExternalId,
            long departureTicks,
            long horizonTicks,
            String preferredObjective,
            int topKAlternatives,
            long resultTtlSeconds
    ) {
    }

    private record RouteChoiceExport(
            String role,
            boolean reachable,
            List<String> pathNodes,
            float expectedCost,
            float p50Cost,
            float p90Cost,
            float minCost,
            float maxCost,
            double optimalityProbability,
            float expectedRegret,
            String dominantScenarioId,
            double dominantScenarioProbability,
            String dominantScenarioLabel,
            String routeSelectionProvenance,
            List<String> explanationTags
    ) {
    }

    private record CandidateDensityExport(
            String policyId,
            int scenarioCount,
            int scenarioOptimalRouteCount,
            int uniqueScenarioOptimalRouteCount,
            int uniqueCandidateRouteCount,
            int aggregateAddedCandidateCount,
            boolean expectedRouteAggregateOnly,
            boolean robustRouteAggregateOnly,
            int selectedAlternativeCount,
            double scenarioCoverageRatio,
            double candidateCoverageRatio,
            double aggregateExpansionRatio,
            String densityClass
    ) {
    }

    private record ScenarioOutcomeExport(
            String scenarioId,
            String label,
            double probability,
            boolean reachable,
            float totalCost,
            long arrivalTicks,
            List<String> pathNodes,
            List<String> explanationTags
    ) {
    }

    private record DecisionStageResult(
            String stageId,
            String label,
            String description,
            List<StageOptionResult> options
    ) {
    }

    private record StageOptionResult(
            String optionId,
            String label,
            String notes,
            List<List<String>> matchingPaths,
            List<String> selectedByRoles,
            List<String> winningScenarioIds
    ) {
    }

    private record PathOptionExport(
            String pathId,
            List<String> pathNodes,
            List<String> selectedByRoles,
            List<String> winningScenarioIds
    ) {
    }

    private record DecisionStageTemplate(
            String stageId,
            String label,
            String description,
            List<StageOptionTemplate> options
    ) {
    }

    private record StageOptionTemplate(
            String optionId,
            String label,
            String notes,
            List<List<String>> matchingPaths
    ) {
    }
}
