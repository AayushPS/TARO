package org.Aayush.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.Aayush.testsupport.AbstractTaroApiSpringTest;
import org.Aayush.testsupport.TaroApiSpringTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TaroApiSpringTest
@Tag("integration")
@DisplayName("Dataset-Driven Training And Serving Load Tests")
class DatasetDrivenTrainingServingLoadTest extends AbstractTaroApiSpringTest {
    private static final List<RouteProbe> ROUTE_PROBES = List.of(
            new RouteProbe("N0", "N1", 2),
            new RouteProbe("N0", "N2", 2),
            new RouteProbe("N1", "N3", 2),
            new RouteProbe("N2", "N3", 2),
            new RouteProbe("N0", "N3", 3)
    );

    @Autowired
    private PredictionTelemetryStore predictionTelemetryStore;

    @Autowired
    private RetrainingControlService retrainingControlService;

    @Autowired
    private TrainingDatasetService trainingDatasetService;

    @Autowired
    private AdminNotificationService adminNotificationService;

    @Override
    protected void resetAdditionalState() {
        predictionTelemetryStore.clear();
        retrainingControlService.clear();
        trainingDatasetService.clear();
        adminNotificationService.clear();
    }

    @Test
    @DisplayName("TomTom dataset supports dataset-backed publication, sustained route traffic, and telemetry-driven retraining")
    void testTomTomDatasetLifecycleAndRetrainingLoop() throws Exception {
        DatasetScenario scenario = DatasetScenario.tomtom();
        PublishedModelContext initialModel = publishDatasetBackedModel(
                "caller-tomtom",
                scenario,
                "release-tomtom-initial-v1",
                "bootstrap_90d"
        );

        LoadRun run = driveServingLoad("caller-tomtom", 30, 30);
        assertEquals(30, run.resultSetIds().size());
        assertEquals(30, run.feedbackCount());

        MvcResult exportResult = mockMvc.perform(get("/api/v1/training/retraining/export")
                        .header(FutureRoutingApiFacade.CALLER_HEADER, "caller-tomtom")
                        .param("resultKind", "ROUTE")
                        .param("completeOnly", "true"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode exportJson = objectMapper.readTree(exportResult.getResponse().getContentAsString());
        assertEquals(30, exportJson.path("rowCount").asInt());
        assertEquals(30, exportJson.path("completeRowCount").asInt());

        MvcResult retrainingJobResult = mockMvc.perform(post("/api/v1/training/retraining/jobs")
                        .header(FutureRoutingApiFacade.CALLER_HEADER, "caller-tomtom")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RetrainingJobCreateRequest(
                                "rolling_feedback_14d",
                                List.of("recency", "density", "periodicity"),
                                CallerScopedRetainedResultRegistry.ResultKind.ROUTE,
                                null,
                                null,
                                List.of(),
                                true,
                                null,
                                null,
                                null,
                                true
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode retrainingJobJson = objectMapper.readTree(retrainingJobResult.getResponse().getContentAsString());
        String retrainingJobId = retrainingJobJson.path("jobId").asText();
        assertEquals(initialModel.activeModelId(), retrainingJobJson.path("basePublishedModelId").asText());
        assertEquals(30, retrainingJobJson.path("exportRowCount").asInt());

        mockMvc.perform(post("/api/v1/training/retraining/jobs/{jobId}/start", retrainingJobId)
                        .header(FutureRoutingApiFacade.CALLER_HEADER, "caller-tomtom"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/training/retraining/jobs/{jobId}/complete", retrainingJobId)
                        .header(FutureRoutingApiFacade.CALLER_HEADER, "caller-tomtom")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "succeeded": true,
                                  "releaseArtifactId": "release-tomtom-retrain-v2",
                                  "validationSummary": "telemetry retraining load test passed"
                                }
                                """))
                .andExpect(status().isOk());

        MvcResult republishResult = mockMvc.perform(post("/api/v1/training/retraining/jobs/{jobId}/publish", retrainingJobId)
                        .header(FutureRoutingApiFacade.CALLER_HEADER, "caller-tomtom"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode republishedModelJson = objectMapper.readTree(republishResult.getResponse().getContentAsString());
        assertNotEquals(initialModel.activeModelId(), republishedModelJson.path("activeModelId").asText());
        assertEquals(retrainingJobId, republishedModelJson.path("sourceTrainingJobId").asText());

        assertTelemetryBackedActiveModel(
                "caller-tomtom",
                republishedModelJson.path("activeModelId").asText(),
                30
        );
    }

    @Test
    @DisplayName("UCI metro dataset supports dataset-backed training metadata and sustained retained-result queries")
    void testUciDatasetLifecycleUnderServingLoad() throws Exception {
        DatasetScenario scenario = DatasetScenario.uciMetro();
        PublishedModelContext model = publishDatasetBackedModel(
                "caller-uci",
                scenario,
                "release-uci-bootstrap-v1",
                "metro_60d"
        );

        LoadRun run = driveServingLoad("caller-uci", 24, 12);
        assertEquals(24, run.resultSetIds().size());
        assertEquals(12, run.feedbackCount());
        assertActiveModel("caller-uci", scenario, model.activeModelId());
    }

    @Test
    @DisplayName("Airline route dataset supports large feature selections and stable serving after publication")
    void testAirlineDatasetLifecycleUnderServingLoad() throws Exception {
        DatasetScenario scenario = DatasetScenario.airline();
        PublishedModelContext model = publishDatasetBackedModel(
                "caller-air",
                scenario,
                "release-airline-bootstrap-v1",
                "airline_180d"
        );

        LoadRun run = driveServingLoad("caller-air", 26, 8);
        assertEquals(26, run.resultSetIds().size());
        assertEquals(8, run.feedbackCount());
        assertActiveModel("caller-air", scenario, model.activeModelId());
    }

    @Test
    @DisplayName("Published models stay caller-scoped under mixed dataset traffic")
    void testMixedDatasetTrafficPreservesCallerScopedModels() throws Exception {
        PublishedModelContext tomtom = publishDatasetBackedModel(
                "caller-mixed-tomtom",
                DatasetScenario.tomtom(),
                "release-mixed-tomtom-v1",
                "mixed_90d"
        );
        PublishedModelContext metro = publishDatasetBackedModel(
                "caller-mixed-metro",
                DatasetScenario.uciMetro(),
                "release-mixed-metro-v1",
                "mixed_60d"
        );
        PublishedModelContext airline = publishDatasetBackedModel(
                "caller-mixed-air",
                DatasetScenario.airline(),
                "release-mixed-air-v1",
                "mixed_180d"
        );

        driveServingLoad("caller-mixed-tomtom", 12, 4);
        driveServingLoad("caller-mixed-metro", 12, 4);
        driveServingLoad("caller-mixed-air", 12, 4);

        assertActiveModel("caller-mixed-tomtom", DatasetScenario.tomtom(), tomtom.activeModelId());
        assertActiveModel("caller-mixed-metro", DatasetScenario.uciMetro(), metro.activeModelId());
        assertActiveModel("caller-mixed-air", DatasetScenario.airline(), airline.activeModelId());
        assertNotEquals(tomtom.activeModelId(), metro.activeModelId());
        assertNotEquals(metro.activeModelId(), airline.activeModelId());
        assertNotEquals(tomtom.activeModelId(), airline.activeModelId());
    }

    private PublishedModelContext publishDatasetBackedModel(
            String callerId,
            DatasetScenario scenario,
            String releaseArtifactId,
            String trainingWindowLabel
    ) throws Exception {
        JsonNode datasetJson = uploadDataset(callerId, scenario);
        String datasetId = datasetJson.path("datasetId").asText();
        assertEquals(scenario.fileName(), datasetJson.path("fileName").asText());
        assertTrue(datasetJson.path("rowCount").asInt() > 0);
        assertTrue(datasetJson.path("headers").isArray());
        assertTrue(datasetJson.path("headers").toString().contains(scenario.targetColumn()));

        RetrainingJobCreateRequest createRequest = new RetrainingJobCreateRequest(
                trainingWindowLabel,
                scenario.selectedTraits(),
                CallerScopedRetainedResultRegistry.ResultKind.ROUTE,
                datasetId,
                scenario.targetColumn(),
                scenario.featureColumns(),
                true,
                null,
                null,
                null,
                true
        );

        MvcResult createJobResult = mockMvc.perform(post("/api/v1/training/retraining/jobs")
                        .header(FutureRoutingApiFacade.CALLER_HEADER, callerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode createdJobJson = objectMapper.readTree(createJobResult.getResponse().getContentAsString());
        String jobId = createdJobJson.path("jobId").asText();
        assertEquals(datasetId, createdJobJson.path("datasetId").asText());
        assertEquals(scenario.fileName(), createdJobJson.path("datasetFileName").asText());
        assertEquals(scenario.targetColumn(), createdJobJson.path("targetColumn").asText());
        assertEquals("QUEUED", createdJobJson.path("status").asText());

        mockMvc.perform(post("/api/v1/training/retraining/jobs/{jobId}/start", jobId)
                        .header(FutureRoutingApiFacade.CALLER_HEADER, callerId))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/training/retraining/jobs/{jobId}/complete", jobId)
                        .header(FutureRoutingApiFacade.CALLER_HEADER, callerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RetrainingJobCompletionRequest(
                                        true,
                                        releaseArtifactId,
                                        "dataset-driven validation passed",
                                        null
                                )
                        )))
                .andExpect(status().isOk());

        MvcResult publishResult = mockMvc.perform(post("/api/v1/training/retraining/jobs/{jobId}/publish", jobId)
                        .header(FutureRoutingApiFacade.CALLER_HEADER, callerId))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode publishedModelJson = objectMapper.readTree(publishResult.getResponse().getContentAsString());
        assertEquals(datasetId, publishedModelJson.path("datasetId").asText());
        assertEquals(scenario.fileName(), publishedModelJson.path("datasetFileName").asText());
        assertEquals(scenario.targetColumn(), publishedModelJson.path("targetColumn").asText());
        assertEquals(releaseArtifactId, publishedModelJson.path("releaseArtifactId").asText());

        return new PublishedModelContext(
                datasetId,
                jobId,
                publishedModelJson.path("activeModelId").asText()
        );
    }

    private LoadRun driveServingLoad(String callerId, int routeCount, int feedbackCount) throws Exception {
        Set<String> resultSetIds = new LinkedHashSet<>();
        int recordedFeedback = 0;
        for (int index = 0; index < routeCount; index++) {
            RouteProbe probe = ROUTE_PROBES.get(index % ROUTE_PROBES.size());
            long departureTicks = 1_711_713_600L + (index * 300L);
            JsonNode routeEnvelopeJson = evaluateRoute(callerId, probe, departureTicks, 3);
            String resultSetId = routeEnvelopeJson.path("resultSetId").asText();
            assertTrue(resultSetIds.add(resultSetId));
            assertRouteEnvelope(routeEnvelopeJson, probe, resultSetId);

            JsonNode summaryJson = retainedRouteSummary(callerId, resultSetId);
            assertEquals(resultSetId, summaryJson.path("resultSetId").asText());
            assertEquals(2, summaryJson.path("scenarioCount").asInt());
            assertEquals(probe.sourceExternalId(), summaryJson.path("request").path("routeRequest").path("sourceExternalId").asText());
            assertEquals(probe.targetExternalId(), summaryJson.path("request").path("routeRequest").path("targetExternalId").asText());

            JsonNode detailJson = retainedRouteDetail(callerId, resultSetId);
            assertEquals(resultSetId, detailJson.path("summary").path("resultSetId").asText());
            assertEquals(2, detailJson.path("scenarioResults").size());

            if (index < feedbackCount) {
                recordCompleteRouteFeedback(callerId, resultSetId, index);
                recordedFeedback++;
            }
        }
        return new LoadRun(resultSetIds, recordedFeedback);
    }

    private void assertActiveModel(String callerId, DatasetScenario scenario, String expectedModelId) throws Exception {
        MvcResult activeModelResult = mockMvc.perform(get("/api/v1/training/retraining/models/active")
                        .header(FutureRoutingApiFacade.CALLER_HEADER, callerId))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode activeModelJson = objectMapper.readTree(activeModelResult.getResponse().getContentAsString());
        assertEquals(expectedModelId, activeModelJson.path("activeModelId").asText());
        assertEquals(scenario.fileName(), activeModelJson.path("datasetFileName").asText());
        assertEquals(scenario.targetColumn(), activeModelJson.path("targetColumn").asText());
        assertEquals(scenario.featureColumns().size(), activeModelJson.path("featureColumns").size());
    }

    private void assertTelemetryBackedActiveModel(
            String callerId,
            String expectedModelId,
            int expectedExportRowCount
    ) throws Exception {
        MvcResult activeModelResult = mockMvc.perform(get("/api/v1/training/retraining/models/active")
                        .header(FutureRoutingApiFacade.CALLER_HEADER, callerId))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode activeModelJson = objectMapper.readTree(activeModelResult.getResponse().getContentAsString());
        assertEquals(expectedModelId, activeModelJson.path("activeModelId").asText());
        assertTrue(activeModelJson.path("datasetId").isNull());
        assertTrue(activeModelJson.path("datasetFileName").isNull());
        assertTrue(activeModelJson.path("targetColumn").isNull());
        assertEquals(expectedExportRowCount, activeModelJson.path("exportRowCount").asInt());
        assertEquals(expectedExportRowCount, activeModelJson.path("completeExportRowCount").asInt());
    }

    private JsonNode uploadDataset(String callerId, DatasetScenario scenario) throws Exception {
        Path csvPath = scenario.csvPath();
        assertTrue(Files.exists(csvPath), "expected sample dataset to exist: " + csvPath.toAbsolutePath());
        MockMultipartFile file = new MockMultipartFile(
                "file",
                scenario.fileName(),
                "text/csv",
                Files.readAllBytes(csvPath)
        );
        MvcResult mvcResult = mockMvc.perform(multipart("/api/v1/training/datasets")
                        .file(file)
                        .header(FutureRoutingApiFacade.CALLER_HEADER, callerId))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(mvcResult.getResponse().getContentAsString());
    }

    private JsonNode evaluateRoute(
            String callerId,
            RouteProbe probe,
            long departureTicks,
            int topKAlternatives
    ) throws Exception {
        MvcResult mvcResult = mockMvc.perform(post("/api/v1/route")
                        .header(FutureRoutingApiFacade.CALLER_HEADER, callerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "source": {"externalId": "%s"},
                                  "target": {"externalId": "%s"},
                                  "departureTicks": %d,
                                  "horizonTicks": 3600,
                                  "preferredObjective": "EXPECTED_ETA",
                                  "topKAlternatives": %d,
                                  "resultTtlSeconds": 600
                                }
                                """.formatted(
                                probe.sourceExternalId(),
                                probe.targetExternalId(),
                                departureTicks,
                                topKAlternatives
                        )))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(mvcResult.getResponse().getContentAsString());
    }

    private JsonNode retainedRouteSummary(String callerId, String resultSetId) throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/api/v1/route/results/{resultSetId}/summary", resultSetId)
                        .header(FutureRoutingApiFacade.CALLER_HEADER, callerId))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(mvcResult.getResponse().getContentAsString());
    }

    private JsonNode retainedRouteDetail(String callerId, String resultSetId) throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/api/v1/route/results/{resultSetId}/detail", resultSetId)
                        .header(FutureRoutingApiFacade.CALLER_HEADER, callerId))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(mvcResult.getResponse().getContentAsString());
    }

    private void assertRouteEnvelope(JsonNode routeEnvelopeJson, RouteProbe probe, String resultSetId) {
        assertEquals(resultSetId, routeEnvelopeJson.path("summary").path("resultSetId").asText());
        assertTrue(routeEnvelopeJson.path("retained").asBoolean());
        assertEquals("bundle-api", routeEnvelopeJson.path("summary").path("scenarioBundleId").asText());
        assertEquals(2, routeEnvelopeJson.path("summary").path("scenarioCount").asInt());
        assertEquals("topo-api", routeEnvelopeJson.path("topologyVersion").path("topologyVersion").asText());

        JsonNode pathNodes = routeEnvelopeJson.path("summary").path("expectedRoute").path("route").path("pathExternalNodeIds");
        assertTrue(routeEnvelopeJson.path("summary").path("expectedRoute").path("route").path("reachable").asBoolean());
        assertEquals(probe.sourceExternalId(), pathNodes.get(0).asText());
        assertEquals(probe.targetExternalId(), pathNodes.get(pathNodes.size() - 1).asText());
        assertTrue(pathNodes.size() >= probe.minimumPathNodes());
    }

    private void recordCompleteRouteFeedback(String callerId, String resultSetId, int iteration) throws Exception {
        long observedAtTicks = 600L + iteration;
        long observedArrivalTicks = observedAtTicks + 120L;
        mockMvc.perform(post("/api/v1/feedback/route/results/{resultSetId}/outcome", resultSetId)
                        .header(FutureRoutingApiFacade.CALLER_HEADER, callerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "outcomeStatus": "COMPLETE",
                                  "observedAtTicks": %d,
                                  "observedArrivalTicks": %d,
                                  "observedCostSeconds": %.1f,
                                  "observationCount": 1
                                }
                                """.formatted(
                                observedAtTicks,
                                observedArrivalTicks,
                                120.0d + iteration
                        )))
                .andExpect(status().isOk());
    }

    private record PublishedModelContext(
            String datasetId,
            String jobId,
            String activeModelId
    ) {
    }

    private record LoadRun(
            Set<String> resultSetIds,
            int feedbackCount
    ) {
    }

    private record RouteProbe(
            String sourceExternalId,
            String targetExternalId,
            int minimumPathNodes
    ) {
    }

    private record DatasetScenario(
            String fileName,
            String targetColumn,
            List<String> featureColumns,
            List<String> selectedTraits
    ) {
        Path csvPath() {
            return Path.of("sample-data", "manual", fileName);
        }

        static DatasetScenario tomtom() {
            return new DatasetScenario(
                    "tomtom_nyc_travel_time_taro_demo.csv",
                    "travel_time_per_10km_min",
                    List.of("speed_kmh", "free_flow_speed_kmh", "congestion_level_pct"),
                    List.of("recency", "periodicity", "persistence")
            );
        }

        static DatasetScenario uciMetro() {
            return new DatasetScenario(
                    "uci_metro_traffic_taro_demo.csv",
                    "traffic_volume",
                    List.of("temp_k", "rain_1h", "snow_1h", "clouds_all"),
                    List.of("recency", "periodicity", "density")
            );
        }

        static DatasetScenario airline() {
            return new DatasetScenario(
                    "hf_airline_routes_taro_demo.csv",
                    "average_fare",
                    List.of(
                            "distance_miles",
                            "passengers",
                            "primary_market_share",
                            "low_fare_market_share",
                            "quarter"
                    ),
                    List.of("recency", "direction", "density")
            );
        }
    }
}
