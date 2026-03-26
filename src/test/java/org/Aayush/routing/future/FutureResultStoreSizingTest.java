package org.Aayush.routing.future;

import org.Aayush.routing.core.MatrixRequest;
import org.Aayush.routing.core.MatrixResponse;
import org.Aayush.routing.core.RouteRequest;
import org.Aayush.routing.core.RouteResponse;
import org.Aayush.routing.core.RoutingAlgorithm;
import org.Aayush.routing.heuristic.HeuristicType;
import org.Aayush.routing.overlay.LiveUpdate;
import org.Aayush.routing.topology.TopologyVersion;
import org.Aayush.routing.traits.addressing.AddressInput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Future Result Store Sizing Tests")
class FutureResultStoreSizingTest {

    @Test
    @DisplayName("Route selection sizing counts C4 confidence semantics explicitly")
    void testEstimateRouteResultSetCountsC4SelectionSemantics() throws Exception {
        ScenarioRouteSelection selection = ScenarioRouteSelection.builder()
                .route(RouteShape.builder()
                        .reachable(true)
                        .departureTicks(5L)
                        .pathExternalNodeIds(List.of("N0", "N1", "N2"))
                        .build())
                .expectedCost(12.0f)
                .p50Cost(12.0f)
                .p90Cost(15.0f)
                .minCost(11.0f)
                .maxCost(18.0f)
                .minArrivalTicks(17L)
                .maxArrivalTicks(23L)
                .optimalityProbability(0.75d)
                .expectedRegret(1.5f)
                .etaBandLowerArrivalTicks(18L)
                .etaBandUpperArrivalTicks(22L)
                .dominantScenarioId("incident_persists")
                .dominantScenarioProbability(0.75d)
                .dominantScenarioLabel("incident_persists")
                .routeSelectionProvenance(RouteSelectionProvenance.SCENARIO_OPTIMAL)
                .explanationTag("recurrent_confidence_high")
                .build();

        long actual = invokePrivateLong("scenarioRouteSelection", ScenarioRouteSelection.class, selection);
        long expected = privateStaticLongField("OBJECT_HEADER_BYTES")
                + (6L * Float.BYTES)
                + (4L * Long.BYTES)
                + (2L * Double.BYTES)
                + invokePrivateLong("routeShape", RouteShape.class, selection.getRoute())
                + invokePrivateLong("string", String.class, selection.getDominantScenarioId())
                + invokePrivateLong("string", String.class, selection.getDominantScenarioLabel())
                + invokePrivateLong("enumBytes", Enum.class, selection.getRouteSelectionProvenance())
                + invokePrivateLong("stringList", List.class, selection.getExplanationTags());

        assertEquals(expected, actual);
    }

    @Test
    @DisplayName("Sparse route result sets still size cleanly with nullable nested metadata")
    void testEstimateRouteResultSetHandlesSparseMetadata() {
        FutureRouteResultSet resultSet = FutureRouteResultSet.builder()
                .resultSetId("route-sparse")
                .quarantineSnapshotId("quarantine-sparse")
                .scenarioResult(FutureRouteScenarioResult.builder()
                        .scenarioId("incident")
                        .label("Incident Persists")
                        .probability(0.60d)
                        .build())
                .build();

        assertTrue(FutureResultStoreSizing.estimateRouteResultSet(resultSet) > 0L);
    }

    @Test
    @DisplayName("Matrix metadata sizing counts address inputs even when optional request fields are absent")
    void testEstimateMatrixMetadataCountsAddressInputs() {
        MatrixRequest matrixRequest = MatrixRequest.builder()
                .sourceExternalId("S0")
                .targetExternalId("T0")
                .sourceAddress(AddressInput.ofXY(12.0d, 34.0d))
                .targetAddress(AddressInput.ofExternalId("NODE-9"))
                .departureTicks(42L)
                .build();
        MatrixResponse matrixResponse = MatrixResponse.builder()
                .sourceExternalIds(List.of("S0"))
                .targetExternalIds(List.of("T0"))
                .reachable(new boolean[][]{{true}})
                .totalCosts(new float[][]{{12.5f}})
                .arrivalTicks(new long[][]{{84L}})
                .algorithm(RoutingAlgorithm.DIJKSTRA)
                .heuristicType(HeuristicType.NONE)
                .implementationNote("sizing-test")
                .build();
        FutureMatrixResultSet resultSet = FutureMatrixResultSet.builder()
                .resultSetId("matrix-sized")
                .request(FutureMatrixRequest.builder()
                        .matrixRequest(matrixRequest)
                        .resultTtl(null)
                        .build())
                .scenarioResult(FutureMatrixScenarioResult.builder()
                        .scenarioId("baseline")
                        .label("Baseline")
                        .probability(1.0d)
                        .matrix(matrixResponse)
                        .build())
                .build();

        assertTrue(FutureResultStoreSizing.estimateMatrixMetadata(resultSet) > 0L);
    }

    @Test
    @DisplayName("Null sizing helpers return zero for defensive accounting")
    void testNullSizingHelpersReturnZero() throws Exception {
        assertEquals(0L, FutureResultStoreSizing.matrixAggregateMetadata(null));
        assertEquals(0L, FutureResultStoreSizing.futureMatrixScenarioMetadata(null));
        assertEquals(0L, FutureResultStoreSizing.matrixResponseMetadata(null));
        assertEquals(0L, invokePrivateLong("futureRouteRequest", FutureRouteRequest.class, null));
        assertEquals(0L, invokePrivateLong("futureMatrixRequest", FutureMatrixRequest.class, null));
        assertEquals(0L, invokePrivateLong("routeRequest", RouteRequest.class, null));
        assertEquals(0L, invokePrivateLong("matrixRequest", MatrixRequest.class, null));
        assertEquals(0L, invokePrivateLong("topologyVersion", TopologyVersion.class, null));
        assertEquals(0L, invokePrivateLong("scenarioBundle", ScenarioBundle.class, null));
        assertEquals(0L, invokePrivateLong("scenarioDefinition", ScenarioDefinition.class, null));
        assertEquals(0L, invokePrivateLong("scenarioRouteSelection", ScenarioRouteSelection.class, null));
        assertEquals(0L, invokePrivateLong("candidateDensityCalibrationReport", CandidateDensityCalibrationReport.class, null));
        assertEquals(0L, invokePrivateLong("routeShape", RouteShape.class, null));
        assertEquals(0L, invokePrivateLong("futureRouteScenarioResult", FutureRouteScenarioResult.class, null));
        assertEquals(0L, invokePrivateLong("routeResponse", RouteResponse.class, null));
        assertEquals(0L, invokePrivateLong("liveUpdate", LiveUpdate.class, null));
        assertEquals(0L, invokePrivateLong("listOverhead", List.class, null));
        assertEquals(0L, invokePrivateLong("instant", Instant.class, null));
        assertEquals(0L, invokePrivateLong("duration", Duration.class, null));
        assertEquals(0L, invokePrivateLong("boolBox", Boolean.class, null));
        assertEquals(0L, invokePrivateLong("number", Number.class, null));
    }

    private long invokePrivateLong(String methodName, Class<?> parameterType, Object argument) throws Exception {
        Method method = FutureResultStoreSizing.class.getDeclaredMethod(methodName, parameterType);
        method.setAccessible(true);
        return (long) method.invoke(null, argument);
    }

    private long privateStaticLongField(String fieldName) throws Exception {
        Field field = FutureResultStoreSizing.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getLong(null);
    }
}
