package org.Aayush.routing.future;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.Aayush.api.ApiErrorCode;
import org.Aayush.api.FutureApiTestConfiguration;
import org.Aayush.api.FutureRoutingApiFacade;
import org.Aayush.app.Main;
import org.Aayush.routing.core.FutureMatrixEvaluator;
import org.Aayush.routing.core.FutureRouteEvaluator;
import org.Aayush.routing.core.MatrixRequest;
import org.Aayush.routing.core.MatrixResponse;
import org.Aayush.routing.core.RouteCore;
import org.Aayush.routing.core.RouteRequest;
import org.Aayush.routing.core.RouteResponse;
import org.Aayush.routing.core.RoutingAlgorithm;
import org.Aayush.routing.execution.ExecutionRuntimeConfig;
import org.Aayush.routing.heuristic.HeuristicType;
import org.Aayush.routing.testutil.RoutingFixtureFactory;
import org.Aayush.routing.topology.FailureQuarantine;
import org.Aayush.routing.topology.ReloadCompatibilityPolicy;
import org.Aayush.routing.topology.TopologyBoundResultStore;
import org.Aayush.routing.topology.TopologyReloadCoordinator;
import org.Aayush.routing.topology.TopologyRuntimeSnapshot;
import org.Aayush.routing.topology.TopologyVersion;
import org.Aayush.routing.traits.addressing.AddressingRuntimeConfig;
import org.Aayush.routing.traits.temporal.TemporalRuntimeConfig;
import org.Aayush.routing.traits.transition.TransitionRuntimeConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = {Main.class, FutureApiTestConfiguration.class}, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Tag("integration")
@DisplayName("Retained Result API Tests")
class RetainedResultApiTest {
    private static final Instant BASE_INSTANT = Instant.parse("2026-03-21T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FutureApiTestConfiguration.ApiMutableClock apiMutableClock;

    @Autowired
    private TopologyReloadCoordinator apiReloadCoordinator;

    @BeforeEach
    void resetApiClock() {
        if (apiMutableClock != null) {
            apiMutableClock.set(BASE_INSTANT);
        }
        if (apiReloadCoordinator != null) {
            apiReloadCoordinator.applyReload(FutureApiTestConfiguration.initialSnapshot());
        }
    }

    @Test
    @DisplayName("Route retained summaries and details stay stable by resultSetId")
    void testRouteSummaryAndDetailLookup() {
        MutableClock clock = new MutableClock(BASE_INSTANT);
        InMemoryEphemeralRouteResultStore store = new InMemoryEphemeralRouteResultStore(clock);
        FutureRouteService service = routeService(store, clock);
        TopologyVersion topologyVersion = topologyVersion("topo-route");

        FutureRouteResultSet resultSet = routeResultSet(
                "route-summary",
                topologyVersion,
                BASE_INSTANT.plus(Duration.ofMinutes(10))
        );
        store.put(resultSet);

        RetainedRouteResultView.Summary summary = service.getResultSummary(resultSet.getResultSetId()).orElseThrow();
        RetainedRouteResultView.Detail detail = service.getResultDetail(resultSet.getResultSetId()).orElseThrow();

        assertEquals(resultSet.getResultSetId(), summary.getResultSetId());
        assertEquals(topologyVersion, summary.getTopologyVersion());
        assertEquals("bundle-route-summary", summary.getScenarioBundleId());
        assertEquals(2, summary.getScenarioCount());
        assertEquals(List.of("N0", "N1", "N2"), summary.getExpectedRoute().getRoute().getPathExternalNodeIds());
        assertEquals(List.of("N0", "N3", "N2"), summary.getRobustRoute().getRoute().getPathExternalNodeIds());
        assertEquals(2, summary.getAlternatives().size());

        assertEquals(summary, detail.getSummary());
        assertEquals(BASE_INSTANT, detail.getScenarioBundleGeneratedAt());
        assertEquals(BASE_INSTANT.plus(Duration.ofMinutes(10)), detail.getScenarioBundleValidUntil());
        assertEquals(3_600L, detail.getScenarioBundleHorizonTicks());
        assertEquals("b5-density-v2", detail.getCandidateDensityCalibrationReport().getPolicyId());
        assertEquals(2, detail.getScenarioResults().size());
        assertEquals("incident", detail.getScenarioResults().get(1).getScenarioId());
        assertEquals(List.of("N0", "N3", "N2"), detail.getScenarioResults().get(1).getRoute().getPathExternalNodeIds());
    }

    @Test
    @DisplayName("Matrix retained summaries and details stay stable by resultSetId")
    void testMatrixSummaryAndDetailLookup() {
        MutableClock clock = new MutableClock(BASE_INSTANT);
        InMemoryEphemeralMatrixResultStore store = new InMemoryEphemeralMatrixResultStore(clock);
        FutureMatrixService service = matrixService(store, clock);
        TopologyVersion topologyVersion = topologyVersion("topo-matrix");

        FutureMatrixResultSet resultSet = matrixResultSet(
                "matrix-summary",
                topologyVersion,
                BASE_INSTANT.plus(Duration.ofMinutes(10)),
                1,
                2
        );
        store.put(resultSet);

        RetainedMatrixResultView.Summary summary = service.getResultSummary(resultSet.getResultSetId()).orElseThrow();
        RetainedMatrixResultView.Detail detail = service.getResultDetail(resultSet.getResultSetId()).orElseThrow();

        assertEquals(resultSet.getResultSetId(), summary.getResultSetId());
        assertEquals(topologyVersion, summary.getTopologyVersion());
        assertEquals("bundle-matrix-summary", summary.getScenarioBundleId());
        assertEquals(2, summary.getScenarioCount());
        assertEquals(List.of("N0"), summary.getAggregate().getSourceExternalIds());
        assertEquals(List.of("N1", "N2"), summary.getAggregate().getTargetExternalIds());
        assertEquals(1.2f, summary.getAggregate().getExpectedCosts()[0][0], 0.0001f);
        assertEquals("dummy aggregate", summary.getAggregate().getAggregationNote());

        assertEquals(summary, detail.getSummary());
        assertEquals(BASE_INSTANT, detail.getScenarioBundleGeneratedAt());
        assertEquals(BASE_INSTANT.plus(Duration.ofMinutes(10)), detail.getScenarioBundleValidUntil());
        assertEquals(3_600L, detail.getScenarioBundleHorizonTicks());
        assertEquals(2, detail.getScenarioResults().size());
        assertEquals("incident", detail.getScenarioResults().get(1).getScenarioId());
        assertEquals(2.5f, detail.getScenarioResults().get(1).getMatrix().getTotalCosts()[0][1], 0.0001f);
    }

    @Test
    @DisplayName("Expired and oversize retained results stay absent from typed lookup")
    void testExpiredAndOversizeResultsAreAbsentFromTypedLookup() {
        MutableClock clock = new MutableClock(BASE_INSTANT);
        InMemoryEphemeralRouteResultStore routeStore = new InMemoryEphemeralRouteResultStore(clock);
        FutureRouteService routeService = routeService(routeStore, clock);
        FutureRouteResultSet routeResult = routeResultSet(
                "route-expiring",
                topologyVersion("topo-expiring-route"),
                BASE_INSTANT.plus(Duration.ofMinutes(1))
        );

        routeStore.put(routeResult);
        assertTrue(routeService.getResultSummary(routeResult.getResultSetId()).isPresent());
        assertTrue(routeService.getResultDetail(routeResult.getResultSetId()).isPresent());

        clock.set(BASE_INSTANT.plus(Duration.ofMinutes(1)));
        assertFalse(routeService.getResultSummary(routeResult.getResultSetId()).isPresent());
        assertFalse(routeService.getResultDetail(routeResult.getResultSetId()).isPresent());

        InMemoryEphemeralMatrixResultStore.Config tinyConfig =
                new InMemoryEphemeralMatrixResultStore.Config(10L, 1024L * 1024L, 256L, 0);
        InMemoryEphemeralMatrixResultStore matrixStore = new InMemoryEphemeralMatrixResultStore(clock, tinyConfig);
        FutureMatrixService matrixService = matrixService(matrixStore, clock);
        FutureMatrixResultSet matrixResult = matrixResultSet(
                "matrix-oversize",
                topologyVersion("topo-oversize-matrix"),
                BASE_INSTANT.plus(Duration.ofMinutes(10)),
                80,
                80
        );

        matrixStore.put(matrixResult);
        assertFalse(matrixService.getResultSummary(matrixResult.getResultSetId()).isPresent());
        assertFalse(matrixService.getResultDetail(matrixResult.getResultSetId()).isPresent());
    }

    @Test
    @DisplayName("Topology-aware typed lookup honors reload compatibility policy")
    void testTopologyAwareLookupHonorsReloadCompatibilityPolicy() {
        RouteCore routeCore = createRouteCore();
        TopologyRuntimeSnapshot snapshotV1 = snapshot(routeCore, "topo-1");
        TopologyRuntimeSnapshot snapshotV2 = snapshot(routeCore, "topo-2");

        InMemoryEphemeralRouteResultStore strictRouteStore = new InMemoryEphemeralRouteResultStore(fixedClock());
        InMemoryEphemeralMatrixResultStore strictMatrixStore = new InMemoryEphemeralMatrixResultStore(fixedClock());
        TopologyReloadCoordinator strictCoordinator = new TopologyReloadCoordinator(
                snapshotV1,
                ReloadCompatibilityPolicy.invalidateStaleTopologyResults(),
                List.<TopologyBoundResultStore>of(strictRouteStore, strictMatrixStore)
        );
        TopologyAwareFutureRouteService strictRouteService =
                new TopologyAwareFutureRouteService(strictCoordinator, routeService(strictRouteStore, fixedClock()));
        TopologyAwareFutureMatrixService strictMatrixService =
                new TopologyAwareFutureMatrixService(strictCoordinator, matrixService(strictMatrixStore, fixedClock()));

        FutureRouteResultSet strictRouteResult = routeResultSet(
                "route-strict",
                snapshotV1.getTopologyVersion(),
                BASE_INSTANT.plus(Duration.ofMinutes(10))
        );
        FutureMatrixResultSet strictMatrixResult = matrixResultSet(
                "matrix-strict",
                snapshotV1.getTopologyVersion(),
                BASE_INSTANT.plus(Duration.ofMinutes(10)),
                1,
                1
        );
        strictRouteStore.put(strictRouteResult);
        strictMatrixStore.put(strictMatrixResult);

        assertTrue(strictRouteService.getResultSummary(strictRouteResult.getResultSetId()).isPresent());
        assertTrue(strictMatrixService.getResultDetail(strictMatrixResult.getResultSetId()).isPresent());

        strictCoordinator.applyReload(snapshotV2);

        assertFalse(strictRouteService.getResultSummary(strictRouteResult.getResultSetId()).isPresent());
        assertFalse(strictRouteService.getResultDetail(strictRouteResult.getResultSetId()).isPresent());
        assertFalse(strictMatrixService.getResultSummary(strictMatrixResult.getResultSetId()).isPresent());
        assertFalse(strictMatrixService.getResultDetail(strictMatrixResult.getResultSetId()).isPresent());

        InMemoryEphemeralRouteResultStore retainRouteStore = new InMemoryEphemeralRouteResultStore(fixedClock());
        InMemoryEphemeralMatrixResultStore retainMatrixStore = new InMemoryEphemeralMatrixResultStore(fixedClock());
        TopologyReloadCoordinator retainCoordinator = new TopologyReloadCoordinator(
                snapshotV1,
                ReloadCompatibilityPolicy.retainUntilExpiry(),
                List.<TopologyBoundResultStore>of(retainRouteStore, retainMatrixStore)
        );
        TopologyAwareFutureRouteService retainRouteService =
                new TopologyAwareFutureRouteService(retainCoordinator, routeService(retainRouteStore, fixedClock()));
        TopologyAwareFutureMatrixService retainMatrixService =
                new TopologyAwareFutureMatrixService(retainCoordinator, matrixService(retainMatrixStore, fixedClock()));

        FutureRouteResultSet retainRouteResult = routeResultSet(
                "route-retain",
                snapshotV1.getTopologyVersion(),
                BASE_INSTANT.plus(Duration.ofMinutes(10))
        );
        FutureMatrixResultSet retainMatrixResult = matrixResultSet(
                "matrix-retain",
                snapshotV1.getTopologyVersion(),
                BASE_INSTANT.plus(Duration.ofMinutes(10)),
                1,
                1
        );
        retainRouteStore.put(retainRouteResult);
        retainMatrixStore.put(retainMatrixResult);

        retainCoordinator.applyReload(snapshotV2);

        RetainedRouteResultView.Summary retainedRouteSummary =
                retainRouteService.getResultSummary(retainRouteResult.getResultSetId()).orElseThrow();
        RetainedMatrixResultView.Summary retainedMatrixSummary =
                retainMatrixService.getResultSummary(retainMatrixResult.getResultSetId()).orElseThrow();
        RetainedRouteResultView.Detail retainedRouteDetail =
                retainRouteService.getResultDetail(retainRouteResult.getResultSetId()).orElseThrow();
        RetainedMatrixResultView.Detail retainedMatrixDetail =
                retainMatrixService.getResultDetail(retainMatrixResult.getResultSetId()).orElseThrow();
        assertEquals("topo-1", retainedRouteSummary.getTopologyVersion().getTopologyVersion());
        assertEquals("topo-1", retainedMatrixSummary.getTopologyVersion().getTopologyVersion());
        assertEquals(retainedRouteSummary, retainedRouteDetail.getSummary());
        assertEquals(retainedMatrixSummary, retainedMatrixDetail.getSummary());
    }

    @Test
    @DisplayName("HTTP retained route summary and detail lookup stay stable by resultSetId")
    void testHttpRouteSummaryAndDetailLookup() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/route")
                        .header(FutureRoutingApiFacade.CALLER_HEADER, "caller-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "source": {"externalId": "N0"},
                                  "target": {"externalId": "N3"},
                                  "departureTicks": 0,
                                  "horizonTicks": 3600,
                                  "topKAlternatives": 2,
                                  "resultTtlSeconds": 600
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode createJson = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String resultSetId = createJson.path("resultSetId").asText();

        MvcResult summaryResult = mockMvc.perform(get("/api/v1/route/results/{resultSetId}/summary", resultSetId)
                        .header(FutureRoutingApiFacade.CALLER_HEADER, "caller-a"))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult detailResult = mockMvc.perform(get("/api/v1/route/results/{resultSetId}/detail", resultSetId)
                        .header(FutureRoutingApiFacade.CALLER_HEADER, "caller-a"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode summaryJson = objectMapper.readTree(summaryResult.getResponse().getContentAsString());
        JsonNode detailJson = objectMapper.readTree(detailResult.getResponse().getContentAsString());
        assertEquals(resultSetId, summaryJson.path("resultSetId").asText());
        assertEquals(resultSetId, detailJson.path("summary").path("resultSetId").asText());
        assertEquals("bundle-api", summaryJson.path("scenarioBundleId").asText());
        assertEquals(3600L, detailJson.path("scenarioBundleHorizonTicks").asLong());
    }

    @Test
    @DisplayName("Caller mismatch returns forbidden")
    void testCallerMismatchReturnsForbidden() throws Exception {
        String resultSetId = createHttpRouteResultSetId("caller-a", 600L);

        MvcResult forbiddenResult = mockMvc.perform(get("/api/v1/route/results/{resultSetId}/summary", resultSetId)
                        .header(FutureRoutingApiFacade.CALLER_HEADER, "caller-b"))
                .andExpect(status().isForbidden())
                .andReturn();

        JsonNode json = objectMapper.readTree(forbiddenResult.getResponse().getContentAsString());
        assertEquals(ApiErrorCode.UNAUTHORIZED_RESULT_ACCESS.name(), json.path("code").asText());
    }

    @Test
    @DisplayName("Unknown resultSetId returns not found")
    void testUnknownResultSetIdReturnsNotFound() throws Exception {
        MvcResult notFoundResult = mockMvc.perform(get("/api/v1/route/results/{resultSetId}/summary", "missing-result")
                        .header(FutureRoutingApiFacade.CALLER_HEADER, "caller-a"))
                .andExpect(status().isNotFound())
                .andReturn();

        JsonNode json = objectMapper.readTree(notFoundResult.getResponse().getContentAsString());
        assertEquals(ApiErrorCode.INVALID_RESULT_SET_ID.name(), json.path("code").asText());
    }

    @Test
    @DisplayName("Expired result returns gone")
    void testExpiredResultReturnsGone() throws Exception {
        String resultSetId = createHttpRouteResultSetId("caller-a", 600L);
        apiMutableClock.set(BASE_INSTANT.plus(Duration.ofSeconds(601)));

        MvcResult goneResult = mockMvc.perform(get("/api/v1/route/results/{resultSetId}/summary", resultSetId)
                        .header(FutureRoutingApiFacade.CALLER_HEADER, "caller-a"))
                .andExpect(status().isGone())
                .andReturn();

        JsonNode json = objectMapper.readTree(goneResult.getResponse().getContentAsString());
        assertEquals(ApiErrorCode.RESULT_EXPIRED.name(), json.path("code").asText());
    }

    @Test
    @DisplayName("Reload invalidated result returns conflict")
    void testReloadInvalidatedResultReturnsConflict() throws Exception {
        String resultSetId = createHttpRouteResultSetId("caller-a", 600L);
        apiReloadCoordinator.applyReload(snapshot(createRouteCore(), "topo-api-reloaded"));

        MvcResult conflictResult = mockMvc.perform(get("/api/v1/route/results/{resultSetId}/summary", resultSetId)
                        .header(FutureRoutingApiFacade.CALLER_HEADER, "caller-a"))
                .andExpect(status().isConflict())
                .andReturn();

        JsonNode json = objectMapper.readTree(conflictResult.getResponse().getContentAsString());
        assertEquals(ApiErrorCode.RESULT_INCOMPATIBLE.name(), json.path("code").asText());
    }

    @Test
    @DisplayName("Admin purge endpoint completes against configured stores")
    void testAdminPurgeEndpointCompletesAgainstConfiguredStores() throws Exception {
        createHttpRouteResultSetId("caller-a", 600L);
        apiMutableClock.set(BASE_INSTANT.plus(Duration.ofSeconds(601)));

        MvcResult purgeResult = mockMvc.perform(post("/api/v1/admin/retained-results/purge"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(purgeResult.getResponse().getContentAsString());
        assertTrue(json.path("routeServiceConfigured").asBoolean());
        assertTrue(json.path("matrixServiceConfigured").asBoolean());
        assertEquals("topo-api", json.path("activeTopologyVersion").asText());
    }

    private FutureRouteService routeService(EphemeralRouteResultStore store, Clock clock) {
        return new FutureRouteService(new FutureRouteEvaluator(noopResolver(), clock), store);
    }

    private FutureMatrixService matrixService(EphemeralMatrixResultStore store, Clock clock) {
        return new FutureMatrixService(new FutureMatrixEvaluator(noopResolver(), clock), store);
    }

    private ScenarioBundleResolver noopResolver() {
        return (request, baseCostEngine, temporalContext, topologyVersion, quarantineSnapshot, clock) ->
                ScenarioBundle.builder()
                        .scenarioBundleId("unused-bundle")
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

    private RouteCore createRouteCore() {
        RoutingFixtureFactory.Fixture fixture = RoutingFixtureFactory.createFixture(
                3,
                new int[]{0, 1, 2, 2},
                new int[]{1, 2},
                new int[]{0, 1},
                new float[]{1.0f, 1.0f},
                new int[]{1, 1},
                null,
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

    private TopologyRuntimeSnapshot snapshot(RouteCore routeCore, String topologyId) {
        return TopologyRuntimeSnapshot.builder()
                .routeCore(routeCore)
                .topologyVersion(topologyVersion(topologyId))
                .failureQuarantine(new FailureQuarantine("q-" + topologyId))
                .build();
    }

    private TopologyVersion topologyVersion(String topologyId) {
        return TopologyVersion.builder()
                .modelVersion("model-v13")
                .topologyVersion(topologyId)
                .generatedAt(BASE_INSTANT)
                .sourceDataLineageHash("lineage-" + topologyId)
                .changeSetHash("change-" + topologyId)
                .build();
    }

    private FutureRouteResultSet routeResultSet(String resultSetId, TopologyVersion topologyVersion, Instant expiresAt) {
        ScenarioRouteSelection expected = routeSelection(
                "baseline",
                0.6d,
                List.of("N0", "N1", "N2"),
                5.0f,
                6L,
                RouteSelectionProvenance.SCENARIO_OPTIMAL,
                List.of("baseline")
        );
        ScenarioRouteSelection robust = routeSelection(
                "incident",
                0.4d,
                List.of("N0", "N3", "N2"),
                6.0f,
                7L,
                RouteSelectionProvenance.SCENARIO_OPTIMAL,
                List.of("incident")
        );
        ScenarioRouteSelection alternative = routeSelection(
                "clear",
                0.2d,
                List.of("N0", "N4", "N2"),
                6.5f,
                8L,
                RouteSelectionProvenance.AGGREGATE_OBJECTIVE,
                List.of("clear")
        );
        ScenarioBundle bundle = ScenarioBundle.builder()
                .scenarioBundleId("bundle-" + resultSetId)
                .generatedAt(BASE_INSTANT)
                .validUntil(expiresAt)
                .horizonTicks(3_600L)
                .topologyVersion(topologyVersion)
                .quarantineSnapshotId("q-" + topologyVersion.getTopologyVersion() + ":0")
                .scenario(ScenarioDefinition.builder()
                        .scenarioId("baseline")
                        .label("baseline")
                        .probability(0.6d)
                        .build())
                .scenario(ScenarioDefinition.builder()
                        .scenarioId("incident")
                        .label("incident")
                        .probability(0.4d)
                        .explanationTag("incident")
                        .build())
                .build();
        return FutureRouteResultSet.builder()
                .resultSetId(resultSetId)
                .createdAt(BASE_INSTANT)
                .expiresAt(expiresAt)
                .request(FutureRouteRequest.builder()
                        .routeRequest(RouteRequest.builder()
                                .sourceExternalId("N0")
                                .targetExternalId("N2")
                                .departureTicks(0L)
                                .build())
                        .horizonTicks(3_600L)
                        .topKAlternatives(2)
                        .resultTtl(Duration.between(BASE_INSTANT, expiresAt))
                        .build())
                .topologyVersion(topologyVersion)
                .quarantineSnapshotId("q-" + topologyVersion.getTopologyVersion() + ":0")
                .scenarioBundle(bundle)
                .candidateDensityCalibrationReport(CandidateDensityCalibrationReport.builder()
                        .policyId("b5-density-v2")
                        .scenarioCount(2)
                        .scenarioOptimalRouteCount(2)
                        .uniqueScenarioOptimalRouteCount(2)
                        .uniqueCandidateRouteCount(3)
                        .aggregateAddedCandidateCount(1)
                        .expectedRouteAggregateOnly(false)
                        .robustRouteAggregateOnly(false)
                        .selectedAlternativeCount(2)
                        .scenarioCoverageRatio(1.0d)
                        .candidateCoverageRatio(1.5d)
                        .aggregateExpansionRatio(0.5d)
                        .densityClass(CandidateDensityClass.HIGH_DENSITY)
                        .build())
                .expectedRoute(expected)
                .robustRoute(robust)
                .alternative(expected)
                .alternative(alternative)
                .scenarioResult(FutureRouteScenarioResult.builder()
                        .scenarioId("baseline")
                        .label("baseline")
                        .probability(0.6d)
                        .route(routeResponse(List.of("N0", "N1", "N2"), 5.0f, 6L))
                        .build())
                .scenarioResult(FutureRouteScenarioResult.builder()
                        .scenarioId("incident")
                        .label("incident")
                        .probability(0.4d)
                        .route(routeResponse(List.of("N0", "N3", "N2"), 6.0f, 7L))
                        .explanationTag("incident")
                        .build())
                .build();
    }

    private ScenarioRouteSelection routeSelection(
            String scenarioId,
            double probability,
            List<String> path,
            float cost,
            long arrivalTicks,
            RouteSelectionProvenance provenance,
            List<String> explanationTags
    ) {
        return ScenarioRouteSelection.builder()
                .route(RouteShape.builder()
                        .reachable(true)
                        .departureTicks(0L)
                        .algorithm(RoutingAlgorithm.DIJKSTRA)
                        .heuristicType(HeuristicType.NONE)
                        .pathExternalNodeIds(path)
                        .build())
                .expectedCost(cost)
                .p50Cost(cost)
                .p90Cost(cost + 1.0f)
                .minCost(cost)
                .maxCost(cost + 1.0f)
                .minArrivalTicks(arrivalTicks)
                .maxArrivalTicks(arrivalTicks + 1L)
                .optimalityProbability(probability)
                .expectedRegret(provenance == RouteSelectionProvenance.AGGREGATE_OBJECTIVE ? 0.5f : 0.0f)
                .etaBandLowerArrivalTicks(arrivalTicks)
                .etaBandUpperArrivalTicks(arrivalTicks + 1L)
                .dominantScenarioId(scenarioId)
                .dominantScenarioProbability(probability)
                .dominantScenarioLabel(scenarioId)
                .routeSelectionProvenance(provenance)
                .explanationTags(explanationTags)
                .build();
    }

    private RouteResponse routeResponse(List<String> path, float totalCost, long arrivalTicks) {
        return RouteResponse.builder()
                .reachable(true)
                .departureTicks(0L)
                .arrivalTicks(arrivalTicks)
                .totalCost(totalCost)
                .settledStates(path.size())
                .algorithm(RoutingAlgorithm.DIJKSTRA)
                .heuristicType(HeuristicType.NONE)
                .pathExternalNodeIds(path)
                .build();
    }

    private FutureMatrixResultSet matrixResultSet(
            String resultSetId,
            TopologyVersion topologyVersion,
            Instant expiresAt,
            int sourceCount,
            int targetCount
    ) {
        List<String> sourceExternalIds = new ArrayList<>(sourceCount);
        for (int row = 0; row < sourceCount; row++) {
            sourceExternalIds.add("N" + row);
        }
        List<String> targetExternalIds = new ArrayList<>(targetCount);
        for (int col = 0; col < targetCount; col++) {
            targetExternalIds.add("N" + (col + 1));
        }

        ScenarioBundle bundle = ScenarioBundle.builder()
                .scenarioBundleId("bundle-" + resultSetId)
                .generatedAt(BASE_INSTANT)
                .validUntil(expiresAt)
                .horizonTicks(3_600L)
                .topologyVersion(topologyVersion)
                .quarantineSnapshotId("q-" + topologyVersion.getTopologyVersion() + ":0")
                .scenario(ScenarioDefinition.builder()
                        .scenarioId("baseline")
                        .label("baseline")
                        .probability(0.6d)
                        .build())
                .scenario(ScenarioDefinition.builder()
                        .scenarioId("incident")
                        .label("incident")
                        .probability(0.4d)
                        .explanationTag("incident")
                        .build())
                .build();

        return FutureMatrixResultSet.builder()
                .resultSetId(resultSetId)
                .createdAt(BASE_INSTANT)
                .expiresAt(expiresAt)
                .request(FutureMatrixRequest.builder()
                        .matrixRequest(matrixRequest(sourceExternalIds, targetExternalIds))
                        .horizonTicks(3_600L)
                        .resultTtl(Duration.between(BASE_INSTANT, expiresAt))
                        .build())
                .topologyVersion(topologyVersion)
                .quarantineSnapshotId("q-" + topologyVersion.getTopologyVersion() + ":0")
                .scenarioBundle(bundle)
                .aggregate(FutureMatrixAggregate.builder()
                        .sourceExternalIds(sourceExternalIds)
                        .targetExternalIds(targetExternalIds)
                        .reachabilityProbabilities(doubleMatrix(sourceCount, targetCount, 1.0d))
                        .expectedCosts(floatMatrix(sourceCount, targetCount, 1.2f))
                        .p50Costs(floatMatrix(sourceCount, targetCount, 1.1f))
                        .p90Costs(floatMatrix(sourceCount, targetCount, 1.8f))
                        .minCosts(floatMatrix(sourceCount, targetCount, 1.0f))
                        .maxCosts(floatMatrix(sourceCount, targetCount, 2.0f))
                        .minArrivalTicks(longMatrix(sourceCount, targetCount, 5L))
                        .maxArrivalTicks(longMatrix(sourceCount, targetCount, 7L))
                        .aggregationNote("dummy aggregate")
                        .build())
                .scenarioResult(FutureMatrixScenarioResult.builder()
                        .scenarioId("baseline")
                        .label("baseline")
                        .probability(0.6d)
                        .matrix(matrixResponse(sourceExternalIds, targetExternalIds, 1.0f))
                        .build())
                .scenarioResult(FutureMatrixScenarioResult.builder()
                        .scenarioId("incident")
                        .label("incident")
                        .probability(0.4d)
                        .matrix(matrixResponse(sourceExternalIds, targetExternalIds, 1.5f))
                        .explanationTag("incident")
                        .build())
                .build();
    }

    private MatrixRequest matrixRequest(List<String> sourceExternalIds, List<String> targetExternalIds) {
        MatrixRequest.MatrixRequestBuilder builder = MatrixRequest.builder().departureTicks(0L);
        for (String sourceExternalId : sourceExternalIds) {
            builder.sourceExternalId(sourceExternalId);
        }
        for (String targetExternalId : targetExternalIds) {
            builder.targetExternalId(targetExternalId);
        }
        return builder.build();
    }

    private MatrixResponse matrixResponse(List<String> sourceExternalIds, List<String> targetExternalIds, float baseCost) {
        int sourceCount = sourceExternalIds.size();
        int targetCount = targetExternalIds.size();
        boolean[][] reachable = new boolean[sourceCount][targetCount];
        float[][] totalCosts = new float[sourceCount][targetCount];
        long[][] arrivalTicks = new long[sourceCount][targetCount];
        for (int row = 0; row < sourceCount; row++) {
            for (int col = 0; col < targetCount; col++) {
                reachable[row][col] = true;
                totalCosts[row][col] = baseCost + row + col;
                arrivalTicks[row][col] = 5L + row + col;
            }
        }
        return MatrixResponse.builder()
                .sourceExternalIds(sourceExternalIds)
                .targetExternalIds(targetExternalIds)
                .reachable(reachable)
                .totalCosts(totalCosts)
                .arrivalTicks(arrivalTicks)
                .algorithm(RoutingAlgorithm.DIJKSTRA)
                .heuristicType(HeuristicType.NONE)
                .implementationNote("retained-test")
                .build();
    }

    private double[][] doubleMatrix(int rows, int cols, double value) {
        double[][] matrix = new double[rows][cols];
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                matrix[row][col] = value;
            }
        }
        return matrix;
    }

    private float[][] floatMatrix(int rows, int cols, float baseValue) {
        float[][] matrix = new float[rows][cols];
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                matrix[row][col] = baseValue + row + col;
            }
        }
        return matrix;
    }

    private long[][] longMatrix(int rows, int cols, long baseValue) {
        long[][] matrix = new long[rows][cols];
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                matrix[row][col] = baseValue + row + col;
            }
        }
        return matrix;
    }

    private Clock fixedClock() {
        return Clock.fixed(BASE_INSTANT, ZoneOffset.UTC);
    }

    private String createHttpRouteResultSetId(String callerId, long resultTtlSeconds) throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/route")
                        .header(FutureRoutingApiFacade.CALLER_HEADER, callerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "source": {"externalId": "N0"},
                                  "target": {"externalId": "N3"},
                                  "departureTicks": 0,
                                  "horizonTicks": 3600,
                                  "topKAlternatives": 2,
                                  "resultTtlSeconds": %d
                                }
                                """.formatted(resultTtlSeconds)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode createJson = objectMapper.readTree(createResult.getResponse().getContentAsString());
        return createJson.path("resultSetId").asText();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void set(Instant instant) {
            this.instant = instant;
        }
    }
}
