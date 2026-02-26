package org.Aayush.routing.core;

import com.google.flatbuffers.FlatBufferBuilder;
import org.Aayush.core.id.IDMapper;
import org.Aayush.routing.spatial.SpatialRuntime;
import org.Aayush.routing.testutil.RoutingFixtureFactory;
import org.Aayush.routing.traits.addressing.AddressInput;
import org.Aayush.routing.traits.addressing.AddressType;
import org.Aayush.routing.traits.addressing.AddressingRuntimeConfig;
import org.Aayush.routing.traits.addressing.AddressingTelemetry;
import org.Aayush.routing.traits.addressing.AddressingTraitCatalog;
import org.Aayush.routing.traits.addressing.CoordinateDistanceStrategy;
import org.Aayush.routing.traits.addressing.CoordinateStrategyRegistry;
import org.Aayush.routing.traits.temporal.TemporalRuntimeConfig;
import org.Aayush.serialization.flatbuffers.taro.model.GraphTopology;
import org.Aayush.serialization.flatbuffers.taro.model.KDNode;
import org.Aayush.serialization.flatbuffers.taro.model.Metadata;
import org.Aayush.serialization.flatbuffers.taro.model.Model;
import org.Aayush.serialization.flatbuffers.taro.model.SpatialIndex;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Stage 15 Addressing Trait Tests")
class Stage15AddressingTraitTest {

    @Test
    @DisplayName("Typed external-id route resolves and returns endpoint metadata")
    void testTypedExternalIdRouteSuccess() {
        RoutingFixtureFactory.Fixture fixture = createLinearFixtureXY();
        RouteCore core = createCore(fixture, buildSpatialRuntimeFromCoordinates(fixture.edgeGraph(), linearXyCoordinates()));

        RouteResponse response = core.route(RouteRequest.builder()
                .sourceAddress(AddressInput.ofExternalId("N0"))
                .targetAddress(AddressInput.ofExternalId("N4"))
                .addressingTraitId(AddressingTraitCatalog.TRAIT_DEFAULT)
                .departureTicks(0L)
                .algorithm(RoutingAlgorithm.A_STAR)
                .heuristicType(org.Aayush.routing.heuristic.HeuristicType.NONE)
                .build());

        assertTrue(response.isReachable());
        assertEquals(4.0f, response.getTotalCost(), 1e-6f);
        assertNotNull(response.getSourceResolvedAddress());
        assertNotNull(response.getTargetResolvedAddress());
        assertEquals(AddressType.EXTERNAL_ID, response.getSourceResolvedAddress().getInputType());
        assertEquals("N0", response.getSourceResolvedAddress().getResolvedExternalId());
        assertEquals("N4", response.getTargetResolvedAddress().getResolvedExternalId());
    }

    @Test
    @DisplayName("Typed route succeeds without request addressingTraitId when runtime trait is pre-bound")
    void testTypedRouteUsesStartupTraitWithoutRequestOverride() {
        RoutingFixtureFactory.Fixture fixture = createLinearFixtureXY();
        RouteCore core = createCore(fixture, buildSpatialRuntimeFromCoordinates(fixture.edgeGraph(), linearXyCoordinates()));

        RouteResponse response = core.route(RouteRequest.builder()
                .sourceAddress(AddressInput.ofExternalId("N0"))
                .targetAddress(AddressInput.ofExternalId("N4"))
                .departureTicks(0L)
                .algorithm(RoutingAlgorithm.A_STAR)
                .heuristicType(org.Aayush.routing.heuristic.HeuristicType.NONE)
                .build());

        assertTrue(response.isReachable());
        assertEquals(4.0f, response.getTotalCost(), 1e-6f);
    }

    @Test
    @DisplayName("Typed external-id matrix resolves successfully with stable row/column ids")
    void testTypedExternalIdMatrixSuccess() {
        RoutingFixtureFactory.Fixture fixture = createLinearFixtureXY();
        RouteCore core = createCore(fixture, buildSpatialRuntimeFromCoordinates(fixture.edgeGraph(), linearXyCoordinates()));

        MatrixResponse response = core.matrix(MatrixRequest.builder()
                .sourceAddress(AddressInput.ofExternalId("N0"))
                .sourceAddress(AddressInput.ofExternalId("N1"))
                .targetAddress(AddressInput.ofExternalId("N3"))
                .targetAddress(AddressInput.ofExternalId("N4"))
                .addressingTraitId(AddressingTraitCatalog.TRAIT_DEFAULT)
                .departureTicks(0L)
                .algorithm(RoutingAlgorithm.DIJKSTRA)
                .heuristicType(org.Aayush.routing.heuristic.HeuristicType.NONE)
                .build());

        assertEquals(List.of("N0", "N1"), response.getSourceExternalIds());
        assertEquals(List.of("N3", "N4"), response.getTargetExternalIds());
        assertEquals(3.0f, response.getTotalCosts()[0][0], 1e-6f);
        assertEquals(4.0f, response.getTotalCosts()[0][1], 1e-6f);
        assertEquals(2.0f, response.getTotalCosts()[1][0], 1e-6f);
        assertEquals(3.0f, response.getTotalCosts()[1][1], 1e-6f);
    }

    @Test
    @DisplayName("Typed unknown external-id uses Stage 15 reason-code family")
    void testTypedUnknownExternalIdUsesStage15ReasonCode() {
        RoutingFixtureFactory.Fixture fixture = createLinearFixtureXY();
        RouteCore core = createCore(fixture, buildSpatialRuntimeFromCoordinates(fixture.edgeGraph(), linearXyCoordinates()));

        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> core.route(RouteRequest.builder()
                        .sourceAddress(AddressInput.ofExternalId("UNKNOWN"))
                        .targetAddress(AddressInput.ofExternalId("N4"))
                        .addressingTraitId(AddressingTraitCatalog.TRAIT_DEFAULT)
                        .departureTicks(0L)
                        .algorithm(RoutingAlgorithm.A_STAR)
                        .heuristicType(org.Aayush.routing.heuristic.HeuristicType.NONE)
                        .build())
        );
        assertEquals(RouteCore.REASON_UNKNOWN_TYPED_EXTERNAL_NODE, ex.getReasonCode());
    }

    @Test
    @DisplayName("Typed XY route enforces deterministic snap threshold")
    void testTypedXyRouteThresholdPassAndFail() {
        RoutingFixtureFactory.Fixture fixture = createLinearFixtureXY();
        RouteCore core = createCore(fixture, buildSpatialRuntimeFromCoordinates(fixture.edgeGraph(), linearXyCoordinates()));

        RouteResponse pass = core.route(RouteRequest.builder()
                .sourceAddress(AddressInput.ofXY(0.05d, 0.0d))
                .targetAddress(AddressInput.ofXY(3.95d, 0.0d))
                .addressingTraitId(AddressingTraitCatalog.TRAIT_DEFAULT)
                .coordinateDistanceStrategyId(CoordinateStrategyRegistry.STRATEGY_XY)
                .maxSnapDistance(0.10d)
                .departureTicks(0L)
                .algorithm(RoutingAlgorithm.A_STAR)
                .heuristicType(org.Aayush.routing.heuristic.HeuristicType.NONE)
                .build());
        assertTrue(pass.isReachable());
        assertEquals(List.of("N0", "N1", "N2", "N3", "N4"), pass.getPathExternalNodeIds());

        RouteCoreException fail = assertThrows(
                RouteCoreException.class,
                () -> core.route(RouteRequest.builder()
                        .sourceAddress(AddressInput.ofXY(0.05d, 0.0d))
                        .targetAddress(AddressInput.ofXY(3.95d, 0.0d))
                        .addressingTraitId(AddressingTraitCatalog.TRAIT_DEFAULT)
                        .coordinateDistanceStrategyId(CoordinateStrategyRegistry.STRATEGY_XY)
                        .maxSnapDistance(0.001d)
                        .departureTicks(0L)
                        .algorithm(RoutingAlgorithm.A_STAR)
                        .heuristicType(org.Aayush.routing.heuristic.HeuristicType.NONE)
                        .build())
        );
        assertEquals(RouteCore.REASON_SNAP_THRESHOLD_EXCEEDED, fail.getReasonCode());
    }

    @Test
    @DisplayName("Typed LAT_LON route validates range and threshold semantics")
    void testTypedLatLonRouteThresholdPassAndFail() {
        RoutingFixtureFactory.Fixture fixture = createLinearFixtureLatLon();
        RouteCore core = createCore(fixture, buildSpatialRuntimeFromCoordinates(fixture.edgeGraph(), linearLatLonCoordinates()));

        RouteResponse pass = core.route(RouteRequest.builder()
                .sourceAddress(AddressInput.ofLatLon(12.0001d, 77.0d))
                .targetAddress(AddressInput.ofLatLon(12.0039d, 77.0d))
                .addressingTraitId(AddressingTraitCatalog.TRAIT_DEFAULT)
                .coordinateDistanceStrategyId(CoordinateStrategyRegistry.STRATEGY_LAT_LON)
                .maxSnapDistance(50.0d)
                .departureTicks(0L)
                .algorithm(RoutingAlgorithm.A_STAR)
                .heuristicType(org.Aayush.routing.heuristic.HeuristicType.NONE)
                .build());
        assertTrue(pass.isReachable());
        assertEquals(List.of("N0", "N1", "N2", "N3", "N4"), pass.getPathExternalNodeIds());

        RouteCoreException fail = assertThrows(
                RouteCoreException.class,
                () -> core.route(RouteRequest.builder()
                        .sourceAddress(AddressInput.ofLatLon(12.0001d, 77.0d))
                        .targetAddress(AddressInput.ofLatLon(12.0039d, 77.0d))
                        .addressingTraitId(AddressingTraitCatalog.TRAIT_DEFAULT)
                        .coordinateDistanceStrategyId(CoordinateStrategyRegistry.STRATEGY_LAT_LON)
                        .maxSnapDistance(5.0d)
                        .departureTicks(0L)
                        .algorithm(RoutingAlgorithm.A_STAR)
                        .heuristicType(org.Aayush.routing.heuristic.HeuristicType.NONE)
                        .build())
        );
        assertEquals(RouteCore.REASON_SNAP_THRESHOLD_EXCEEDED, fail.getReasonCode());
    }

    @Test
    @DisplayName("Typed coordinate request fails deterministically when spatial runtime is unavailable")
    void testSpatialRuntimeUnavailableForCoordinates() {
        RoutingFixtureFactory.Fixture fixture = createLinearFixtureXY();
        RouteCore core = createCore(fixture, null);

        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> core.route(RouteRequest.builder()
                        .sourceAddress(AddressInput.ofXY(0.0d, 0.0d))
                        .targetAddress(AddressInput.ofXY(4.0d, 0.0d))
                        .addressingTraitId(AddressingTraitCatalog.TRAIT_DEFAULT)
                        .coordinateDistanceStrategyId(CoordinateStrategyRegistry.STRATEGY_XY)
                        .maxSnapDistance(1.0d)
                        .departureTicks(0L)
                        .algorithm(RoutingAlgorithm.A_STAR)
                        .heuristicType(org.Aayush.routing.heuristic.HeuristicType.NONE)
                        .build())
        );
        assertEquals(RouteCore.REASON_SPATIAL_RUNTIME_UNAVAILABLE, ex.getReasonCode());
    }

    @Test
    @DisplayName("Unknown addressing trait and unknown coordinate strategy are rejected")
    void testUnknownTraitAndStrategyValidation() {
        RoutingFixtureFactory.Fixture fixture = createLinearFixtureXY();
        RouteCore core = createCore(fixture, buildSpatialRuntimeFromCoordinates(fixture.edgeGraph(), linearXyCoordinates()));

        RouteCoreException traitEx = assertThrows(
                RouteCoreException.class,
                () -> core.route(RouteRequest.builder()
                        .sourceAddress(AddressInput.ofExternalId("N0"))
                        .targetAddress(AddressInput.ofExternalId("N4"))
                        .addressingTraitId("UNKNOWN_TRAIT")
                        .departureTicks(0L)
                        .algorithm(RoutingAlgorithm.A_STAR)
                        .heuristicType(org.Aayush.routing.heuristic.HeuristicType.NONE)
                        .build())
        );
        assertEquals(RouteCore.REASON_UNKNOWN_ADDRESSING_TRAIT, traitEx.getReasonCode());

        RouteCoreException strategyEx = assertThrows(
                RouteCoreException.class,
                () -> core.route(RouteRequest.builder()
                        .sourceAddress(AddressInput.ofXY(0.0d, 0.0d))
                        .targetAddress(AddressInput.ofXY(4.0d, 0.0d))
                        .addressingTraitId(AddressingTraitCatalog.TRAIT_DEFAULT)
                        .coordinateDistanceStrategyId("UNKNOWN_STRATEGY")
                        .maxSnapDistance(1.0d)
                        .departureTicks(0L)
                        .algorithm(RoutingAlgorithm.A_STAR)
                        .heuristicType(org.Aayush.routing.heuristic.HeuristicType.NONE)
                        .build())
        );
        assertEquals(RouteCore.REASON_UNKNOWN_COORDINATE_STRATEGY, strategyEx.getReasonCode());
    }

    @Test
    @DisplayName("Unsupported address type for selected trait is rejected")
    void testUnsupportedAddressTypeForTrait() {
        RoutingFixtureFactory.Fixture fixture = createLinearFixtureXY();
        RouteCore core = createCore(
                fixture,
                buildSpatialRuntimeFromCoordinates(fixture.edgeGraph(), linearXyCoordinates()),
                fixture.nodeIdMapper(),
                null,
                AddressingRuntimeConfig.externalIdOnlyRuntime()
        );

        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> core.route(RouteRequest.builder()
                        .sourceAddress(AddressInput.ofXY(0.0d, 0.0d))
                        .targetAddress(AddressInput.ofXY(4.0d, 0.0d))
                        .coordinateDistanceStrategyId(CoordinateStrategyRegistry.STRATEGY_XY)
                        .maxSnapDistance(1.0d)
                        .departureTicks(0L)
                        .algorithm(RoutingAlgorithm.A_STAR)
                        .heuristicType(org.Aayush.routing.heuristic.HeuristicType.NONE)
                        .build())
        );
        assertEquals(RouteCore.REASON_UNSUPPORTED_ADDRESS_TYPE, ex.getReasonCode());
    }

    @Test
    @DisplayName("Mixed-mode and typed+legacy ambiguity are enforced deterministically")
    void testMixedModeAndTypedLegacyAmbiguityContracts() {
        RoutingFixtureFactory.Fixture fixture = createLinearFixtureXY();
        RouteCore core = createCore(fixture, buildSpatialRuntimeFromCoordinates(fixture.edgeGraph(), linearXyCoordinates()));

        RouteCoreException mixedDisabled = assertThrows(
                RouteCoreException.class,
                () -> core.route(RouteRequest.builder()
                        .sourceAddress(AddressInput.ofXY(0.0d, 0.0d))
                        .targetExternalId("N4")
                        .addressingTraitId(AddressingTraitCatalog.TRAIT_DEFAULT)
                        .coordinateDistanceStrategyId(CoordinateStrategyRegistry.STRATEGY_XY)
                        .maxSnapDistance(1.0d)
                        .departureTicks(0L)
                        .algorithm(RoutingAlgorithm.A_STAR)
                        .heuristicType(org.Aayush.routing.heuristic.HeuristicType.NONE)
                        .build())
        );
        assertEquals(RouteCore.REASON_MIXED_MODE_DISABLED, mixedDisabled.getReasonCode());

        RouteResponse mixedEnabled = core.route(RouteRequest.builder()
                .sourceAddress(AddressInput.ofXY(0.0d, 0.0d))
                .targetExternalId("N4")
                .allowMixedAddressing(true)
                .addressingTraitId(AddressingTraitCatalog.TRAIT_DEFAULT)
                .coordinateDistanceStrategyId(CoordinateStrategyRegistry.STRATEGY_XY)
                .maxSnapDistance(1.0d)
                .departureTicks(0L)
                .algorithm(RoutingAlgorithm.A_STAR)
                .heuristicType(org.Aayush.routing.heuristic.HeuristicType.NONE)
                .build());
        assertTrue(mixedEnabled.isReachable());

        RouteCoreException ambiguity = assertThrows(
                RouteCoreException.class,
                () -> core.route(RouteRequest.builder()
                        .sourceExternalId("N0")
                        .sourceAddress(AddressInput.ofExternalId("N0"))
                        .targetAddress(AddressInput.ofExternalId("N4"))
                        .addressingTraitId(AddressingTraitCatalog.TRAIT_DEFAULT)
                        .departureTicks(0L)
                        .algorithm(RoutingAlgorithm.A_STAR)
                        .heuristicType(org.Aayush.routing.heuristic.HeuristicType.NONE)
                        .build())
        );
        assertEquals(RouteCore.REASON_TYPED_LEGACY_AMBIGUITY, ambiguity.getReasonCode());
    }

    @Test
    @DisplayName("Coordinate payload validation rejects non-finite and invalid LAT_LON ranges")
    void testCoordinateValidationErrors() {
        RoutingFixtureFactory.Fixture fixture = createLinearFixtureLatLon();
        RouteCore core = createCore(fixture, buildSpatialRuntimeFromCoordinates(fixture.edgeGraph(), linearLatLonCoordinates()));

        RouteCoreException nonFinite = assertThrows(
                RouteCoreException.class,
                () -> core.route(RouteRequest.builder()
                        .sourceAddress(AddressInput.ofXY(Double.NaN, 0.0d))
                        .targetAddress(AddressInput.ofXY(4.0d, 0.0d))
                        .addressingTraitId(AddressingTraitCatalog.TRAIT_DEFAULT)
                        .coordinateDistanceStrategyId(CoordinateStrategyRegistry.STRATEGY_XY)
                        .maxSnapDistance(10.0d)
                        .departureTicks(0L)
                        .algorithm(RoutingAlgorithm.A_STAR)
                        .heuristicType(org.Aayush.routing.heuristic.HeuristicType.NONE)
                        .build())
        );
        assertEquals(RouteCore.REASON_NON_FINITE_COORDINATES, nonFinite.getReasonCode());

        RouteCoreException latLonRange = assertThrows(
                RouteCoreException.class,
                () -> core.route(RouteRequest.builder()
                        .sourceAddress(AddressInput.ofLatLon(95.0d, 77.0d))
                        .targetAddress(AddressInput.ofLatLon(12.0d, 77.0d))
                        .addressingTraitId(AddressingTraitCatalog.TRAIT_DEFAULT)
                        .coordinateDistanceStrategyId(CoordinateStrategyRegistry.STRATEGY_LAT_LON)
                        .maxSnapDistance(500.0d)
                        .departureTicks(0L)
                        .algorithm(RoutingAlgorithm.A_STAR)
                        .heuristicType(org.Aayush.routing.heuristic.HeuristicType.NONE)
                        .build())
        );
        assertEquals(RouteCore.REASON_LAT_LON_RANGE, latLonRange.getReasonCode());
    }

    @Test
    @DisplayName("Typed matrix coordinates resolve once per unique endpoint and preserve stable response shape")
    void testMatrixTypedCoordinateDedupAndTelemetry() {
        RoutingFixtureFactory.Fixture fixture = createLinearFixtureXY();
        RouteCore core = createCore(fixture, buildSpatialRuntimeFromCoordinates(fixture.edgeGraph(), linearXyCoordinates()));

        MatrixResponse response = core.matrix(MatrixRequest.builder()
                .sourceAddress(AddressInput.ofXY(0.01d, 0.0d))
                .sourceAddress(AddressInput.ofXY(0.01d, 0.0d))
                .targetAddress(AddressInput.ofXY(3.99d, 0.0d))
                .targetAddress(AddressInput.ofXY(3.99d, 0.0d))
                .addressingTraitId(AddressingTraitCatalog.TRAIT_DEFAULT)
                .coordinateDistanceStrategyId(CoordinateStrategyRegistry.STRATEGY_XY)
                .maxSnapDistance(0.20d)
                .departureTicks(0L)
                .algorithm(RoutingAlgorithm.DIJKSTRA)
                .heuristicType(org.Aayush.routing.heuristic.HeuristicType.NONE)
                .build());

        assertEquals(List.of("N0", "N0"), response.getSourceExternalIds());
        assertEquals(List.of("N4", "N4"), response.getTargetExternalIds());
        assertEquals(4.0f, response.getTotalCosts()[0][0], 1e-6f);
        assertEquals(4.0f, response.getTotalCosts()[1][1], 1e-6f);

        AddressingTelemetry telemetry = core.addressingTelemetryContract();
        assertEquals(4, telemetry.endpointCount());
        assertEquals(2, telemetry.uniqueEndpointCount());
        assertEquals(2, telemetry.resolveCalls());
        assertEquals(2, telemetry.dedupSaved());
        assertEquals(2, telemetry.coordinateResolveCount());
    }

    @Test
    @DisplayName("Coordinate strategy id is required for coordinate typed requests")
    void testCoordinateStrategyIdRequiredForCoordinates() {
        RoutingFixtureFactory.Fixture fixture = createLinearFixtureXY();
        RouteCore core = createCore(fixture, buildSpatialRuntimeFromCoordinates(fixture.edgeGraph(), linearXyCoordinates()));

        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> core.route(RouteRequest.builder()
                        .sourceAddress(AddressInput.ofXY(0.0d, 0.0d))
                        .targetAddress(AddressInput.ofXY(4.0d, 0.0d))
                        .addressingTraitId(AddressingTraitCatalog.TRAIT_DEFAULT)
                        .maxSnapDistance(1.0d)
                        .departureTicks(0L)
                        .algorithm(RoutingAlgorithm.A_STAR)
                        .heuristicType(org.Aayush.routing.heuristic.HeuristicType.NONE)
                        .build())
        );
        assertEquals(RouteCore.REASON_COORDINATE_STRATEGY_REQUIRED, ex.getReasonCode());
    }

    @Test
    @DisplayName("Coordinate dedup uses effective strategy and canonicalized coordinate bits")
    void testMatrixCoordinateDedupIgnoresHintNoiseAndSignedZero() {
        RoutingFixtureFactory.Fixture fixture = createLinearFixtureXY();
        RouteCore core = createCore(fixture, buildSpatialRuntimeFromCoordinates(fixture.edgeGraph(), linearXyCoordinates()));

        MatrixResponse response = core.matrix(MatrixRequest.builder()
                .sourceAddress(AddressInput.ofCoordinates(0.0d, 0.0d, null))
                .sourceAddress(AddressInput.ofCoordinates(-0.0d, 0.0d, CoordinateStrategyRegistry.STRATEGY_XY))
                .targetAddress(AddressInput.ofCoordinates(4.0d, 0.0d, null))
                .targetAddress(AddressInput.ofCoordinates(4.0d, 0.0d, CoordinateStrategyRegistry.STRATEGY_XY))
                .addressingTraitId(AddressingTraitCatalog.TRAIT_DEFAULT)
                .coordinateDistanceStrategyId(CoordinateStrategyRegistry.STRATEGY_XY)
                .maxSnapDistance(0.20d)
                .departureTicks(0L)
                .algorithm(RoutingAlgorithm.DIJKSTRA)
                .heuristicType(org.Aayush.routing.heuristic.HeuristicType.NONE)
                .build());

        assertEquals(List.of("N0", "N0"), response.getSourceExternalIds());
        assertEquals(List.of("N4", "N4"), response.getTargetExternalIds());

        AddressingTelemetry telemetry = core.addressingTelemetryContract();
        assertEquals(4, telemetry.endpointCount());
        assertEquals(2, telemetry.uniqueEndpointCount());
        assertEquals(2, telemetry.resolveCalls());
        assertEquals(2, telemetry.dedupSaved());
        assertEquals(2, telemetry.coordinateResolveCount());
    }

    @Test
    @DisplayName("Zero maxSnapDistance is valid for exact coordinate-to-node matches")
    void testZeroMaxSnapDistanceAllowsExactMatch() {
        RoutingFixtureFactory.Fixture fixture = createLinearFixtureXY();
        RouteCore core = createCore(fixture, buildSpatialRuntimeFromCoordinates(fixture.edgeGraph(), linearXyCoordinates()));

        RouteResponse response = core.route(RouteRequest.builder()
                .sourceAddress(AddressInput.ofXY(0.0d, 0.0d))
                .targetAddress(AddressInput.ofXY(4.0d, 0.0d))
                .addressingTraitId(AddressingTraitCatalog.TRAIT_DEFAULT)
                .coordinateDistanceStrategyId(CoordinateStrategyRegistry.STRATEGY_XY)
                .maxSnapDistance(0.0d)
                .departureTicks(0L)
                .algorithm(RoutingAlgorithm.A_STAR)
                .heuristicType(org.Aayush.routing.heuristic.HeuristicType.NONE)
                .build());

        assertTrue(response.isReachable());
        assertEquals(List.of("N0", "N1", "N2", "N3", "N4"), response.getPathExternalNodeIds());
    }

    @Test
    @DisplayName("Malformed typed payload with cross-field contamination is rejected")
    void testMalformedTypedPayloadCrossFieldContamination() {
        RoutingFixtureFactory.Fixture fixture = createLinearFixtureXY();
        RouteCore core = createCore(fixture, buildSpatialRuntimeFromCoordinates(fixture.edgeGraph(), linearXyCoordinates()));

        AddressInput malformed = reflectAddressInput(
                AddressType.EXTERNAL_ID,
                "N0",
                0.0d,
                0.0d,
                CoordinateStrategyRegistry.STRATEGY_XY
        );

        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> core.route(RouteRequest.builder()
                        .sourceAddress(malformed)
                        .targetAddress(AddressInput.ofExternalId("N4"))
                        .addressingTraitId(AddressingTraitCatalog.TRAIT_DEFAULT)
                        .departureTicks(0L)
                        .algorithm(RoutingAlgorithm.A_STAR)
                        .heuristicType(org.Aayush.routing.heuristic.HeuristicType.NONE)
                        .build())
        );
        assertEquals(RouteCore.REASON_MALFORMED_TYPED_PAYLOAD, ex.getReasonCode());
    }

    @Test
    @DisplayName("Matrix typed and legacy ambiguity and list validation paths are deterministic")
    void testMatrixTypedLegacyAmbiguityAndListValidationContracts() {
        RoutingFixtureFactory.Fixture fixture = createLinearFixtureXY();
        RouteCore core = createCore(fixture, buildSpatialRuntimeFromCoordinates(fixture.edgeGraph(), linearXyCoordinates()));

        RouteCoreException ambiguity = assertThrows(
                RouteCoreException.class,
                () -> core.matrix(MatrixRequest.builder()
                        .sourceAddress(AddressInput.ofExternalId("N0"))
                        .sourceExternalId("N0")
                        .targetExternalId("N4")
                        .departureTicks(0L)
                        .algorithm(RoutingAlgorithm.DIJKSTRA)
                        .heuristicType(org.Aayush.routing.heuristic.HeuristicType.NONE)
                        .build())
        );
        assertEquals(RouteCore.REASON_TYPED_LEGACY_AMBIGUITY, ambiguity.getReasonCode());

        RouteCoreException sourceListRequired = assertThrows(
                RouteCoreException.class,
                () -> core.matrix(MatrixRequest.builder()
                        .targetExternalId("N4")
                        .departureTicks(0L)
                        .algorithm(RoutingAlgorithm.DIJKSTRA)
                        .heuristicType(org.Aayush.routing.heuristic.HeuristicType.NONE)
                        .build())
        );
        assertEquals(RouteCore.REASON_SOURCE_LIST_REQUIRED, sourceListRequired.getReasonCode());

        RouteCoreException blankLegacySource = assertThrows(
                RouteCoreException.class,
                () -> core.matrix(MatrixRequest.builder()
                        .sourceExternalId("N0")
                        .sourceExternalId(" ")
                        .targetExternalId("N4")
                        .departureTicks(0L)
                        .algorithm(RoutingAlgorithm.DIJKSTRA)
                        .heuristicType(org.Aayush.routing.heuristic.HeuristicType.NONE)
                        .build())
        );
        assertEquals(RouteCore.REASON_SOURCE_EXTERNAL_ID_REQUIRED, blankLegacySource.getReasonCode());

        RouteCoreException nullTypedSource = assertThrows(
                RouteCoreException.class,
                () -> core.matrix(MatrixRequest.builder()
                        .sourceAddresses(Arrays.asList(AddressInput.ofExternalId("N0"), null))
                        .targetAddress(AddressInput.ofExternalId("N4"))
                        .addressingTraitId(AddressingTraitCatalog.TRAIT_DEFAULT)
                        .departureTicks(0L)
                        .algorithm(RoutingAlgorithm.DIJKSTRA)
                        .heuristicType(org.Aayush.routing.heuristic.HeuristicType.NONE)
                        .build())
        );
        assertEquals(RouteCore.REASON_MALFORMED_TYPED_PAYLOAD, nullTypedSource.getReasonCode());
    }

    @Test
    @DisplayName("Coordinate strategy hint mismatch and blank request strategy are rejected")
    void testCoordinateStrategyHintMismatchAndBlankStrategyValidation() {
        RoutingFixtureFactory.Fixture fixture = createLinearFixtureXY();
        RouteCore core = createCore(fixture, buildSpatialRuntimeFromCoordinates(fixture.edgeGraph(), linearXyCoordinates()));

        RouteCoreException hintMismatch = assertThrows(
                RouteCoreException.class,
                () -> core.route(RouteRequest.builder()
                        .sourceAddress(AddressInput.ofCoordinates(0.0d, 0.0d, CoordinateStrategyRegistry.STRATEGY_LAT_LON))
                        .targetAddress(AddressInput.ofCoordinates(4.0d, 0.0d, CoordinateStrategyRegistry.STRATEGY_LAT_LON))
                        .addressingTraitId(AddressingTraitCatalog.TRAIT_DEFAULT)
                        .coordinateDistanceStrategyId(CoordinateStrategyRegistry.STRATEGY_XY)
                        .maxSnapDistance(10.0d)
                        .departureTicks(0L)
                        .algorithm(RoutingAlgorithm.A_STAR)
                        .heuristicType(org.Aayush.routing.heuristic.HeuristicType.NONE)
                        .build())
        );
        assertEquals(RouteCore.REASON_MALFORMED_TYPED_PAYLOAD, hintMismatch.getReasonCode());

        RouteCoreException blankStrategy = assertThrows(
                RouteCoreException.class,
                () -> core.route(RouteRequest.builder()
                        .sourceAddress(AddressInput.ofXY(0.0d, 0.0d))
                        .targetAddress(AddressInput.ofXY(4.0d, 0.0d))
                        .addressingTraitId(AddressingTraitCatalog.TRAIT_DEFAULT)
                        .coordinateDistanceStrategyId("   ")
                        .maxSnapDistance(10.0d)
                        .departureTicks(0L)
                        .algorithm(RoutingAlgorithm.A_STAR)
                        .heuristicType(org.Aayush.routing.heuristic.HeuristicType.NONE)
                        .build())
        );
        assertEquals(RouteCore.REASON_COORDINATE_STRATEGY_REQUIRED, blankStrategy.getReasonCode());
    }

    @Test
    @DisplayName("Invalid maxSnapDistance override maps to deterministic Stage 15 reason code")
    void testInvalidMaxSnapDistanceOverrideReasonCode() {
        RoutingFixtureFactory.Fixture fixture = createLinearFixtureXY();
        RouteCore core = createCore(fixture, buildSpatialRuntimeFromCoordinates(fixture.edgeGraph(), linearXyCoordinates()));

        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> core.route(RouteRequest.builder()
                        .sourceAddress(AddressInput.ofXY(0.0d, 0.0d))
                        .targetAddress(AddressInput.ofXY(4.0d, 0.0d))
                        .addressingTraitId(AddressingTraitCatalog.TRAIT_DEFAULT)
                        .coordinateDistanceStrategyId(CoordinateStrategyRegistry.STRATEGY_XY)
                        .maxSnapDistance(-1.0d)
                        .departureTicks(0L)
                        .algorithm(RoutingAlgorithm.A_STAR)
                        .heuristicType(org.Aayush.routing.heuristic.HeuristicType.NONE)
                        .build())
        );
        assertEquals(RouteCore.REASON_INVALID_MAX_SNAP_DISTANCE, ex.getReasonCode());
    }

    @Test
    @DisplayName("Unexpected custom coordinate-strategy failures are wrapped deterministically")
    void testCustomCoordinateStrategyFailureWrapped() {
        RoutingFixtureFactory.Fixture fixture = createLinearFixtureXY();
        SpatialRuntime spatialRuntime = buildSpatialRuntimeFromCoordinates(fixture.edgeGraph(), linearXyCoordinates());
        CoordinateStrategyRegistry customRegistry =
                new CoordinateStrategyRegistry(List.of(new ThrowingDistanceStrategy("BROKEN_STRATEGY")));
        RouteCore core = createCore(fixture, spatialRuntime, fixture.nodeIdMapper(), customRegistry);

        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> core.route(RouteRequest.builder()
                        .sourceAddress(AddressInput.ofCoordinates(0.0d, 0.0d, null))
                        .targetAddress(AddressInput.ofCoordinates(4.0d, 0.0d, null))
                        .addressingTraitId(AddressingTraitCatalog.TRAIT_DEFAULT)
                        .coordinateDistanceStrategyId("BROKEN_STRATEGY")
                        .maxSnapDistance(10.0d)
                        .departureTicks(0L)
                        .algorithm(RoutingAlgorithm.A_STAR)
                        .heuristicType(org.Aayush.routing.heuristic.HeuristicType.NONE)
                        .build())
        );
        assertEquals(RouteCore.REASON_COORDINATE_STRATEGY_FAILURE, ex.getReasonCode());
    }

    @Test
    @DisplayName("Negative snap distances from custom strategies are rejected deterministically")
    void testNegativeComputedSnapDistanceRejected() {
        RoutingFixtureFactory.Fixture fixture = createLinearFixtureXY();
        SpatialRuntime spatialRuntime = buildSpatialRuntimeFromCoordinates(fixture.edgeGraph(), linearXyCoordinates());
        CoordinateStrategyRegistry customRegistry =
                new CoordinateStrategyRegistry(List.of(new NegativeDistanceStrategy("NEGATIVE_DISTANCE")));
        RouteCore core = createCore(fixture, spatialRuntime, fixture.nodeIdMapper(), customRegistry);

        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> core.route(RouteRequest.builder()
                        .sourceAddress(AddressInput.ofCoordinates(0.0d, 0.0d, null))
                        .targetAddress(AddressInput.ofCoordinates(4.0d, 0.0d, null))
                        .addressingTraitId(AddressingTraitCatalog.TRAIT_DEFAULT)
                        .coordinateDistanceStrategyId("NEGATIVE_DISTANCE")
                        .maxSnapDistance(10.0d)
                        .departureTicks(0L)
                        .algorithm(RoutingAlgorithm.A_STAR)
                        .heuristicType(org.Aayush.routing.heuristic.HeuristicType.NONE)
                        .build())
        );
        assertEquals(RouteCore.REASON_COORDINATE_STRATEGY_FAILURE, ex.getReasonCode());
    }

    @Test
    @DisplayName("Request addressing-trait hint must match startup trait")
    void testRequestTraitHintMustMatchStartupTrait() {
        RoutingFixtureFactory.Fixture fixture = createLinearFixtureXY();
        RouteCore core = createCore(fixture, buildSpatialRuntimeFromCoordinates(fixture.edgeGraph(), linearXyCoordinates()));

        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> core.route(RouteRequest.builder()
                        .sourceAddress(AddressInput.ofExternalId("N0"))
                        .targetAddress(AddressInput.ofExternalId("N4"))
                        .addressingTraitId(AddressingTraitCatalog.TRAIT_EXTERNAL_ID_ONLY)
                        .departureTicks(0L)
                        .algorithm(RoutingAlgorithm.A_STAR)
                        .heuristicType(org.Aayush.routing.heuristic.HeuristicType.NONE)
                        .build())
        );
        assertEquals(RouteCore.REASON_ADDRESSING_RUNTIME_MISMATCH, ex.getReasonCode());
    }

    @Test
    @DisplayName("Coordinate resolution wraps reverse mapping failures deterministically")
    void testCoordinateResolutionExternalMappingFailureWrapped() {
        RoutingFixtureFactory.Fixture fixture = createLinearFixtureXY();
        SpatialRuntime spatialRuntime = buildSpatialRuntimeFromCoordinates(fixture.edgeGraph(), linearXyCoordinates());
        IDMapper failingMapper = new FailingToExternalMapper(fixture.nodeIdMapper(), 0);
        RouteCore core = createCore(fixture, spatialRuntime, failingMapper);

        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> core.route(RouteRequest.builder()
                        .sourceAddress(AddressInput.ofXY(0.0d, 0.0d))
                        .targetAddress(AddressInput.ofXY(4.0d, 0.0d))
                        .addressingTraitId(AddressingTraitCatalog.TRAIT_DEFAULT)
                        .coordinateDistanceStrategyId(CoordinateStrategyRegistry.STRATEGY_XY)
                        .maxSnapDistance(1.0d)
                        .departureTicks(0L)
                        .algorithm(RoutingAlgorithm.A_STAR)
                        .heuristicType(org.Aayush.routing.heuristic.HeuristicType.NONE)
                        .build())
        );
        assertEquals(RouteCore.REASON_EXTERNAL_MAPPING_FAILED, ex.getReasonCode());
    }

    private RouteCore createCore(RoutingFixtureFactory.Fixture fixture, SpatialRuntime spatialRuntime) {
        return createCore(fixture, spatialRuntime, fixture.nodeIdMapper());
    }

    private RouteCore createCore(
            RoutingFixtureFactory.Fixture fixture,
            SpatialRuntime spatialRuntime,
            IDMapper mapper
    ) {
        return createCore(fixture, spatialRuntime, mapper, null, AddressingRuntimeConfig.defaultRuntime());
    }

    private RouteCore createCore(
            RoutingFixtureFactory.Fixture fixture,
            SpatialRuntime spatialRuntime,
            IDMapper mapper,
            CoordinateStrategyRegistry coordinateStrategyRegistry
    ) {
        return createCore(
                fixture,
                spatialRuntime,
                mapper,
                coordinateStrategyRegistry,
                AddressingRuntimeConfig.defaultRuntime()
        );
    }

    private RouteCore createCore(
            RoutingFixtureFactory.Fixture fixture,
            SpatialRuntime spatialRuntime,
            IDMapper mapper,
            CoordinateStrategyRegistry coordinateStrategyRegistry,
            AddressingRuntimeConfig addressingRuntimeConfig
    ) {
        return RouteCore.builder()
                .edgeGraph(fixture.edgeGraph())
                .profileStore(fixture.profileStore())
                .costEngine(fixture.costEngine())
                .nodeIdMapper(mapper)
                .spatialRuntime(spatialRuntime)
                .coordinateStrategyRegistry(coordinateStrategyRegistry)
                .temporalRuntimeConfig(TemporalRuntimeConfig.calendarUtc())
                .transitionRuntimeConfig(org.Aayush.routing.traits.transition.TransitionRuntimeConfig.defaultRuntime())
                .addressingRuntimeConfig(addressingRuntimeConfig)
                .build();
    }

    private RoutingFixtureFactory.Fixture createLinearFixtureXY() {
        return RoutingFixtureFactory.createFixture(
                5,
                new int[]{0, 1, 2, 3, 4, 4},
                new int[]{1, 2, 3, 4},
                new int[]{0, 1, 2, 3},
                new float[]{1.0f, 1.0f, 1.0f, 1.0f},
                new int[]{1, 1, 1, 1},
                linearXyCoordinates(),
                new RoutingFixtureFactory.ProfileSpec(
                        1,
                        RoutingFixtureFactory.ALL_DAYS_MASK,
                        new float[]{1.0f},
                        1.0f
                )
        );
    }

    private RoutingFixtureFactory.Fixture createLinearFixtureLatLon() {
        return RoutingFixtureFactory.createFixture(
                5,
                new int[]{0, 1, 2, 3, 4, 4},
                new int[]{1, 2, 3, 4},
                new int[]{0, 1, 2, 3},
                new float[]{1.0f, 1.0f, 1.0f, 1.0f},
                new int[]{1, 1, 1, 1},
                linearLatLonCoordinates(),
                new RoutingFixtureFactory.ProfileSpec(
                        1,
                        RoutingFixtureFactory.ALL_DAYS_MASK,
                        new float[]{1.0f},
                        1.0f
                )
        );
    }

    private double[] linearXyCoordinates() {
        return new double[]{
                0.0d, 0.0d,
                1.0d, 0.0d,
                2.0d, 0.0d,
                3.0d, 0.0d,
                4.0d, 0.0d
        };
    }

    private double[] linearLatLonCoordinates() {
        return new double[]{
                12.0000d, 77.0000d,
                12.0010d, 77.0000d,
                12.0020d, 77.0000d,
                12.0030d, 77.0000d,
                12.0040d, 77.0000d
        };
    }

    private SpatialRuntime buildSpatialRuntimeFromCoordinates(org.Aayush.routing.graph.EdgeGraph edgeGraph, double[] coordinates) {
        int nodeCount = edgeGraph.nodeCount();
        ByteBuffer model = buildSpatialModelBuffer(nodeCount, coordinates);
        return SpatialRuntime.fromFlatBuffer(model.duplicate().order(ByteOrder.LITTLE_ENDIAN), edgeGraph, true);
    }

    private ByteBuffer buildSpatialModelBuffer(int nodeCount, double[] coordinates) {
        FlatBufferBuilder builder = new FlatBufferBuilder(2048);

        int[] firstEdge = new int[nodeCount + 1];
        int[] edgeTargets = new int[0];

        int firstEdgeVec = GraphTopology.createFirstEdgeVector(builder, firstEdge);
        int edgeTargetVec = GraphTopology.createEdgeTargetVector(builder, edgeTargets);

        GraphTopology.startCoordinatesVector(builder, nodeCount);
        for (int i = nodeCount - 1; i >= 0; i--) {
            org.Aayush.serialization.flatbuffers.taro.model.Coordinate.createCoordinate(
                    builder,
                    coordinates[i * 2],
                    coordinates[i * 2 + 1]
            );
        }
        int coordinatesVec = builder.endVector();

        GraphTopology.startGraphTopology(builder);
        GraphTopology.addNodeCount(builder, nodeCount);
        GraphTopology.addEdgeCount(builder, 0);
        GraphTopology.addFirstEdge(builder, firstEdgeVec);
        GraphTopology.addEdgeTarget(builder, edgeTargetVec);
        GraphTopology.addCoordinates(builder, coordinatesVec);
        int topologyRef = GraphTopology.endGraphTopology(builder);

        int[] leafItems = new int[nodeCount];
        for (int i = 0; i < nodeCount; i++) {
            leafItems[i] = i;
        }

        SpatialIndex.startTreeNodesVector(builder, 1);
        KDNode.createKDNode(builder, 0.0f, -1, -1, 0, nodeCount, 0, 1);
        int treeNodesVec = builder.endVector();
        int leafItemsVec = SpatialIndex.createLeafItemsVector(builder, leafItems);

        SpatialIndex.startSpatialIndex(builder);
        SpatialIndex.addTreeNodes(builder, treeNodesVec);
        SpatialIndex.addLeafItems(builder, leafItemsVec);
        SpatialIndex.addRootIndex(builder, 0);
        int spatialRef = SpatialIndex.endSpatialIndex(builder);

        int metadataRef = createMetadata(builder);

        Model.startModel(builder);
        Model.addMetadata(builder, metadataRef);
        Model.addTopology(builder, topologyRef);
        Model.addSpatialIndex(builder, spatialRef);
        int root = Model.endModel(builder);
        Model.finishModelBuffer(builder, root);

        return ByteBuffer.wrap(builder.sizedByteArray()).order(ByteOrder.LITTLE_ENDIAN);
    }

    private int createMetadata(FlatBufferBuilder builder) {
        int modelVersion = builder.createString("stage15-test");
        int timezone = builder.createString("UTC");
        Metadata.startMetadata(builder);
        Metadata.addSchemaVersion(builder, 1);
        Metadata.addModelVersion(builder, modelVersion);
        Metadata.addTimeUnit(builder, org.Aayush.serialization.flatbuffers.taro.model.TimeUnit.SECONDS);
        Metadata.addTickDurationNs(builder, 1_000_000_000L);
        Metadata.addProfileTimezone(builder, timezone);
        return Metadata.endMetadata(builder);
    }

    private AddressInput reflectAddressInput(
            AddressType type,
            String externalId,
            Double coordinateFirst,
            Double coordinateSecond,
            String coordinateStrategyHintId
    ) {
        try {
            Constructor<AddressInput> ctor = AddressInput.class.getDeclaredConstructor(
                    AddressType.class,
                    String.class,
                    Double.class,
                    Double.class,
                    String.class
            );
            ctor.setAccessible(true);
            return ctor.newInstance(type, externalId, coordinateFirst, coordinateSecond, coordinateStrategyHintId);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException("failed to construct AddressInput via reflection", ex);
        }
    }

    private record ThrowingDistanceStrategy(String id) implements CoordinateDistanceStrategy {
        @Override
        public void validate(double first, double second) {
            if (!Double.isFinite(first) || !Double.isFinite(second)) {
                throw new CoordinateValidationException("BROKEN_NON_FINITE", "coordinates must be finite");
            }
        }

        @Override
        public double distance(double requestFirst, double requestSecond, double nodeFirst, double nodeSecond) {
            throw new IllegalStateException("forced distance failure");
        }
    }

    private record NegativeDistanceStrategy(String id) implements CoordinateDistanceStrategy {
        @Override
        public void validate(double first, double second) {
            if (!Double.isFinite(first) || !Double.isFinite(second)) {
                throw new CoordinateValidationException("NEGATIVE_NON_FINITE", "coordinates must be finite");
            }
        }

        @Override
        public double distance(double requestFirst, double requestSecond, double nodeFirst, double nodeSecond) {
            return -1.0d;
        }
    }

    private static final class FailingToExternalMapper implements IDMapper {
        private final IDMapper delegate;
        private final int failInternalId;

        private FailingToExternalMapper(IDMapper delegate, int failInternalId) {
            this.delegate = delegate;
            this.failInternalId = failInternalId;
        }

        @Override
        public int toInternal(String externalId) throws UnknownIDException {
            return delegate.toInternal(externalId);
        }

        @Override
        public String toExternal(int internalId) {
            if (internalId == failInternalId) {
                throw new IllegalStateException("forced mapping failure for " + internalId);
            }
            return delegate.toExternal(internalId);
        }

        @Override
        public boolean containsExternal(String externalId) {
            return delegate.containsExternal(externalId);
        }

        @Override
        public boolean containsInternal(int internalId) {
            return delegate.containsInternal(internalId);
        }

        @Override
        public int size() {
            return delegate.size();
        }
    }
}
