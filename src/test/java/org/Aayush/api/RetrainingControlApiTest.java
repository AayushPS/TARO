package org.Aayush.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.Aayush.app.Main;
import org.Aayush.routing.topology.TopologyReloadCoordinator;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = {Main.class, FutureApiTestConfiguration.class}, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Tag("integration")
@DisplayName("Retraining Control API Tests")
class RetrainingControlApiTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FutureApiTestConfiguration.ApiMutableClock apiMutableClock;

    @Autowired
    private TopologyReloadCoordinator topologyReloadCoordinator;

    @Autowired
    private PredictionTelemetryStore predictionTelemetryStore;

    @Autowired
    private RetrainingControlService retrainingControlService;

    @BeforeEach
    void resetApiState() {
        apiMutableClock.set(FutureApiTestConfiguration.BASE_INSTANT);
        topologyReloadCoordinator.applyReload(FutureApiTestConfiguration.initialSnapshot());
        predictionTelemetryStore.clear();
        retrainingControlService.clear();
    }

    @Test
    @DisplayName("Caller-scoped telemetry export and retraining job lifecycle lead to one published active model")
    void testRetrainingLifecyclePublishesActiveModel() throws Exception {
        String resultSetId = createRouteResultSetId("caller-a");
        recordCompleteRouteFeedback("caller-a", resultSetId);

        MvcResult exportResult = mockMvc.perform(get("/api/v1/training/retraining/export")
                        .header(FutureRoutingApiFacade.CALLER_HEADER, "caller-a")
                        .param("resultKind", "ROUTE")
                        .param("completeOnly", "true"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode exportJson = objectMapper.readTree(exportResult.getResponse().getContentAsString());
        assertEquals(1, exportJson.path("rowCount").asInt());
        assertEquals(1, exportJson.path("completeRowCount").asInt());
        assertEquals(resultSetId, exportJson.path("rows").get(0).path("resultSetId").asText());

        MvcResult createJobResult = mockMvc.perform(post("/api/v1/training/retraining/jobs")
                        .header(FutureRoutingApiFacade.CALLER_HEADER, "caller-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "trainingWindowLabel": "rolling_30d",
                                  "selectedTraits": ["recency", "periodicity", "persistence"],
                                  "resultKind": "ROUTE",
                                  "completeOnly": true
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode createdJobJson = objectMapper.readTree(createJobResult.getResponse().getContentAsString());
        String jobId = createdJobJson.path("jobId").asText();
        assertEquals("QUEUED", createdJobJson.path("status").asText());
        assertEquals(1, createdJobJson.path("exportRowCount").asInt());
        assertEquals(1, createdJobJson.path("completeExportRowCount").asInt());
        assertTrue(createdJobJson.path("basePublishedModelId").isNull());

        MvcResult startJobResult = mockMvc.perform(post("/api/v1/training/retraining/jobs/{jobId}/start", jobId)
                        .header(FutureRoutingApiFacade.CALLER_HEADER, "caller-a"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode startedJobJson = objectMapper.readTree(startJobResult.getResponse().getContentAsString());
        assertEquals("RUNNING", startedJobJson.path("status").asText());
        assertFalse(startedJobJson.path("startedAt").isNull());

        MvcResult completeJobResult = mockMvc.perform(post("/api/v1/training/retraining/jobs/{jobId}/complete", jobId)
                        .header(FutureRoutingApiFacade.CALLER_HEADER, "caller-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "succeeded": true,
                                  "releaseArtifactId": "release-caller-a-v2",
                                  "validationSummary": "temporal probes and calibration gates passed"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode completedJobJson = objectMapper.readTree(completeJobResult.getResponse().getContentAsString());
        assertEquals("SUCCEEDED", completedJobJson.path("status").asText());
        assertEquals("release-caller-a-v2", completedJobJson.path("releaseArtifactId").asText());
        assertFalse(completedJobJson.path("completedAt").isNull());

        MvcResult publishResult = mockMvc.perform(post("/api/v1/training/retraining/jobs/{jobId}/publish", jobId)
                        .header(FutureRoutingApiFacade.CALLER_HEADER, "caller-a"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode publishedModelJson = objectMapper.readTree(publishResult.getResponse().getContentAsString());
        String activeModelId = publishedModelJson.path("activeModelId").asText();
        assertNotNull(activeModelId);
        assertEquals(jobId, publishedModelJson.path("sourceTrainingJobId").asText());
        assertEquals("release-caller-a-v2", publishedModelJson.path("releaseArtifactId").asText());

        MvcResult activeModelResult = mockMvc.perform(get("/api/v1/training/retraining/models/active")
                        .header(FutureRoutingApiFacade.CALLER_HEADER, "caller-a"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode activeModelJson = objectMapper.readTree(activeModelResult.getResponse().getContentAsString());
        assertEquals(activeModelId, activeModelJson.path("activeModelId").asText());
        assertEquals("release-caller-a-v2", activeModelJson.path("releaseArtifactId").asText());

        MvcResult jobStatusResult = mockMvc.perform(get("/api/v1/training/retraining/jobs/{jobId}", jobId)
                        .header(FutureRoutingApiFacade.CALLER_HEADER, "caller-a"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode finalJobJson = objectMapper.readTree(jobStatusResult.getResponse().getContentAsString());
        assertEquals("PUBLISHED", finalJobJson.path("status").asText());
        assertEquals(activeModelId, finalJobJson.path("publishedModelId").asText());
        assertFalse(finalJobJson.path("publishedAt").isNull());
    }

    @Test
    @DisplayName("Retraining job creation rejects callers with no matching telemetry rows")
    void testRetrainingJobCreationRejectsMissingTelemetryRows() throws Exception {
        MvcResult mvcResult = mockMvc.perform(post("/api/v1/training/retraining/jobs")
                        .header(FutureRoutingApiFacade.CALLER_HEADER, "caller-empty")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "trainingWindowLabel": "rolling_7d",
                                  "selectedTraits": ["recency"],
                                  "resultKind": "ROUTE",
                                  "completeOnly": true
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andReturn();

        JsonNode json = objectMapper.readTree(mvcResult.getResponse().getContentAsString());
        assertEquals(ApiErrorCode.INVALID_REQUEST.name(), json.path("code").asText());
    }

    @Test
    @DisplayName("Retraining jobs are caller-scoped and hidden from other callers")
    void testRetrainingJobsAreCallerScoped() throws Exception {
        String resultSetId = createRouteResultSetId("caller-a");
        recordCompleteRouteFeedback("caller-a", resultSetId);
        MvcResult createJobResult = mockMvc.perform(post("/api/v1/training/retraining/jobs")
                        .header(FutureRoutingApiFacade.CALLER_HEADER, "caller-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "trainingWindowLabel": "rolling_14d",
                                  "selectedTraits": ["recency", "homophily"],
                                  "resultKind": "ROUTE",
                                  "completeOnly": true
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String jobId = objectMapper.readTree(createJobResult.getResponse().getContentAsString()).path("jobId").asText();

        MvcResult mvcResult = mockMvc.perform(get("/api/v1/training/retraining/jobs/{jobId}", jobId)
                        .header(FutureRoutingApiFacade.CALLER_HEADER, "caller-b"))
                .andExpect(status().isNotFound())
                .andReturn();

        JsonNode json = objectMapper.readTree(mvcResult.getResponse().getContentAsString());
        assertEquals(ApiErrorCode.TRAINING_JOB_NOT_FOUND.name(), json.path("code").asText());
    }

    @Test
    @DisplayName("Active model requires publication and publish rejects non-succeeded jobs")
    void testActiveModelRequiresPublicationAndPublishRejectsWrongState() throws Exception {
        String resultSetId = createRouteResultSetId("caller-a");
        recordCompleteRouteFeedback("caller-a", resultSetId);
        MvcResult createJobResult = mockMvc.perform(post("/api/v1/training/retraining/jobs")
                        .header(FutureRoutingApiFacade.CALLER_HEADER, "caller-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "trainingWindowLabel": "rolling_14d",
                                  "selectedTraits": ["recency", "density"],
                                  "resultKind": "ROUTE",
                                  "completeOnly": true
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String jobId = objectMapper.readTree(createJobResult.getResponse().getContentAsString()).path("jobId").asText();

        MvcResult activeModelResult = mockMvc.perform(get("/api/v1/training/retraining/models/active")
                        .header(FutureRoutingApiFacade.CALLER_HEADER, "caller-a"))
                .andExpect(status().isNotFound())
                .andReturn();
        JsonNode activeModelJson = objectMapper.readTree(activeModelResult.getResponse().getContentAsString());
        assertEquals(ApiErrorCode.ACTIVE_MODEL_NOT_FOUND.name(), activeModelJson.path("code").asText());

        MvcResult publishResult = mockMvc.perform(post("/api/v1/training/retraining/jobs/{jobId}/publish", jobId)
                        .header(FutureRoutingApiFacade.CALLER_HEADER, "caller-a"))
                .andExpect(status().isConflict())
                .andReturn();
        JsonNode publishJson = objectMapper.readTree(publishResult.getResponse().getContentAsString());
        assertEquals(ApiErrorCode.TRAINING_JOB_CONFLICT.name(), publishJson.path("code").asText());
    }

    private String createRouteResultSetId(String callerId) throws Exception {
        MvcResult mvcResult = mockMvc.perform(post("/api/v1/route")
                        .header(FutureRoutingApiFacade.CALLER_HEADER, callerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "source": {"externalId": "N0"},
                                  "target": {"externalId": "N3"},
                                  "departureTicks": 0,
                                  "horizonTicks": 3600,
                                  "preferredObjective": "EXPECTED_ETA",
                                  "topKAlternatives": 2,
                                  "resultTtlSeconds": 600
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(mvcResult.getResponse().getContentAsString()).path("resultSetId").asText();
    }

    private void recordCompleteRouteFeedback(String callerId, String resultSetId) throws Exception {
        mockMvc.perform(post("/api/v1/feedback/route/results/{resultSetId}/outcome", resultSetId)
                        .header(FutureRoutingApiFacade.CALLER_HEADER, callerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "outcomeStatus": "COMPLETE",
                                  "observedAtTicks": 480,
                                  "observedArrivalTicks": 540,
                                  "observedCostSeconds": 120.5,
                                  "observationCount": 1
                                }
                                """))
                .andExpect(status().isOk());
    }
}
