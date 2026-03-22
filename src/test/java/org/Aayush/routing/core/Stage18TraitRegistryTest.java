package org.Aayush.routing.core;

import com.google.flatbuffers.FlatBufferBuilder;
import org.Aayush.core.id.IDMapper;
import org.Aayush.routing.execution.ExecutionRuntimeConfig;
import org.Aayush.routing.spatial.SpatialRuntime;
import org.Aayush.routing.testutil.RoutingFixtureFactory;
import org.Aayush.routing.traits.addressing.AddressInput;
import org.Aayush.routing.traits.registry.TraitBundleRegistry;
import org.Aayush.routing.traits.registry.TraitBundleRuntimeBinder;
import org.Aayush.routing.traits.registry.TraitBundleRuntimeConfig;
import org.Aayush.routing.traits.registry.TraitBundleSpec;
import org.Aayush.routing.traits.temporal.TemporalTimezonePolicyRegistry;
import org.Aayush.routing.traits.transition.TransitionTraitCatalog;
import org.Aayush.serialization.flatbuffers.taro.model.GraphTopology;
import org.Aayush.serialization.flatbuffers.taro.model.KDNode;
import org.Aayush.serialization.flatbuffers.taro.model.Metadata;
import org.Aayush.serialization.flatbuffers.taro.model.Model;
import org.Aayush.serialization.flatbuffers.taro.model.SpatialIndex;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Stage 18 Trait Registry Tests")
class Stage18TraitRegistryTest {

    @Test
    @DisplayName("Named bundle route and matrix use startup-bound trait selection and expose bundle context")
    void testNamedBundleRouteAndMatrixSuccess() {
        RoutingFixtureFactory.Fixture fixture = createLinearFixtureXY();
        RouteCore core = createCore(
                fixture,
                buildSpatialRuntimeFromCoordinates(fixture.edgeGraph(), linearXyCoordinates()),
                TraitBundleRuntimeConfig.ofBundleId("CITY_XY"),
                new TraitBundleRegistry(List.of(namedBundle()))
        );

        RouteResponse routeResponse = core.route(RouteRequest.builder()
                .sourceAddress(AddressInput.ofXY(0.01d, 0.0d))
                .targetAddress(AddressInput.ofXY(3.99d, 0.0d))
                .maxSnapDistance(0.20d)
                .departureTicks(0L)
                .build());
        assertTrue(routeResponse.isReachable());
        assertEquals(List.of("N0", "N1", "N2", "N3", "N4"), routeResponse.getPathExternalNodeIds());

        MatrixResponse matrixResponse = core.matrix(MatrixRequest.builder()
                .sourceAddress(AddressInput.ofXY(0.01d, 0.0d))
                .targetAddress(AddressInput.ofXY(3.99d, 0.0d))
                .maxSnapDistance(0.20d)
                .departureTicks(0L)
                .build());
        assertEquals(4.0f, matrixResponse.getTotalCosts()[0][0], 1e-6f);

        assertEquals("CITY_XY", core.traitBundleContextContract().getBundleId());
        assertEquals(TraitBundleRuntimeBinder.CONFIG_SOURCE_NAMED_BUNDLE, core.traitBundleContextContract().getConfigSource());
        assertNotNull(core.traitBundleTelemetryContract().getTraitHash());
        assertEquals(core.traitBundleContextContract().getTraitHash(), core.traitBundleTelemetryContract().getTraitHash());
    }

    @Test
    @DisplayName("Named bundle rejects coordinate payloads that conflict with startup trait lock")
    void testStartupTraitLockStillAppliesWithNamedBundle() {
        RoutingFixtureFactory.Fixture fixture = createLinearFixtureXY();
        RouteCore core = createCore(
                fixture,
                buildSpatialRuntimeFromCoordinates(fixture.edgeGraph(), linearXyCoordinates()),
                TraitBundleRuntimeConfig.ofBundleId("EXTERNAL_ONLY"),
                new TraitBundleRegistry(List.of(externalOnlyBundle()))
        );

        RouteCoreException routeMismatch = assertThrows(
                RouteCoreException.class,
                () -> core.route(RouteRequest.builder()
                        .sourceAddress(AddressInput.ofXY(0.01d, 0.0d))
                        .targetAddress(AddressInput.ofXY(3.99d, 0.0d))
                        .maxSnapDistance(0.20d)
                        .departureTicks(0L)
                        .build())
        );
        assertEquals(RouteCore.REASON_UNSUPPORTED_ADDRESS_TYPE, routeMismatch.getReasonCode());

        RouteCoreException matrixMismatch = assertThrows(
                RouteCoreException.class,
                () -> core.matrix(MatrixRequest.builder()
                        .sourceAddress(AddressInput.ofXY(0.01d, 0.0d))
                        .targetAddress(AddressInput.ofXY(3.99d, 0.0d))
                        .maxSnapDistance(0.20d)
                        .departureTicks(0L)
                        .build())
        );
        assertEquals(RouteCore.REASON_UNSUPPORTED_ADDRESS_TYPE, matrixMismatch.getReasonCode());
    }

    @Test
    @DisplayName("Constructor rejects conflicting Stage 18 bundle and legacy axis configs")
    void testConstructorRejectsConflictingBundleAndLegacyConfigs() {
        RoutingFixtureFactory.Fixture fixture = createLinearFixtureXY();

        RouteCoreException ex = assertThrows(
                RouteCoreException.class,
                () -> RouteCore.builder()
                        .edgeGraph(fixture.edgeGraph())
                        .profileStore(fixture.profileStore())
                        .costEngine(fixture.costEngine())
                        .nodeIdMapper(fixture.nodeIdMapper())
                        .spatialRuntime(buildSpatialRuntimeFromCoordinates(fixture.edgeGraph(), linearXyCoordinates()))
                        .traitBundleRuntimeConfig(TraitBundleRuntimeConfig.ofBundleId("CITY_XY"))
                        .traitBundleRegistry(new TraitBundleRegistry(List.of(namedBundle())))
                        .addressingRuntimeConfig(org.Aayush.routing.traits.addressing.AddressingRuntimeConfig.latLonRuntime())
                        .temporalRuntimeConfig(org.Aayush.routing.traits.temporal.TemporalRuntimeConfig.calendarUtc())
                        .transitionRuntimeConfig(org.Aayush.routing.traits.transition.TransitionRuntimeConfig.edgeBased())
                        .build()
        );
        assertEquals(RouteCore.REASON_TRAIT_BUNDLE_CONFIG_CONFLICT, ex.getReasonCode());
    }

    @Test
    @DisplayName("Legacy axis configs route successfully and expose synthesized bundle context")
    void testLegacyAxisConfigsExposeSynthesizedBundleContext() {
        RoutingFixtureFactory.Fixture fixture = createLinearFixtureXY();
        RouteCore core = RouteCore.builder()
                .edgeGraph(fixture.edgeGraph())
                .profileStore(fixture.profileStore())
                .costEngine(fixture.costEngine())
                .nodeIdMapper(fixture.nodeIdMapper())
                .spatialRuntime(buildSpatialRuntimeFromCoordinates(fixture.edgeGraph(), linearXyCoordinates()))
                .executionRuntimeConfig(ExecutionRuntimeConfig.aStarNone())
                .addressingRuntimeConfig(org.Aayush.routing.traits.addressing.AddressingRuntimeConfig.xyRuntime())
                .temporalRuntimeConfig(org.Aayush.routing.traits.temporal.TemporalRuntimeConfig.calendarUtc())
                .transitionRuntimeConfig(org.Aayush.routing.traits.transition.TransitionRuntimeConfig.edgeBased())
                .build();

        RouteResponse response = core.route(RouteRequest.builder()
                .sourceAddress(AddressInput.ofXY(0.01d, 0.0d))
                .targetAddress(AddressInput.ofXY(3.99d, 0.0d))
                .maxSnapDistance(0.20d)
                .departureTicks(0L)
                .build());

        assertTrue(response.isReachable());
        assertEquals(TraitBundleRuntimeBinder.CONFIG_SOURCE_LEGACY_AXIS_CONFIGS, core.traitBundleContextContract().getConfigSource());
        assertNull(core.traitBundleContextContract().getBundleId());
        assertEquals(core.traitBundleContextContract().getTraitHash(), core.traitBundleTelemetryContract().getTraitHash());
    }

    private RouteCore createCore(
            RoutingFixtureFactory.Fixture fixture,
            SpatialRuntime spatialRuntime,
            TraitBundleRuntimeConfig traitBundleRuntimeConfig,
            TraitBundleRegistry traitBundleRegistry
    ) {
        return RouteCore.builder()
                .edgeGraph(fixture.edgeGraph())
                .profileStore(fixture.profileStore())
                .costEngine(fixture.costEngine())
                .nodeIdMapper(fixture.nodeIdMapper())
                .spatialRuntime(spatialRuntime)
                .executionRuntimeConfig(ExecutionRuntimeConfig.aStarNone())
                .traitBundleRuntimeConfig(traitBundleRuntimeConfig)
                .traitBundleRegistry(traitBundleRegistry)
                .build();
    }

    private TraitBundleSpec namedBundle() {
        return TraitBundleSpec.builder()
                .bundleId("CITY_XY")
                .addressingTraitId(org.Aayush.routing.traits.addressing.AddressingTraitCatalog.TRAIT_DEFAULT)
                .coordinateDistanceStrategyId(org.Aayush.routing.traits.addressing.CoordinateStrategyRegistry.STRATEGY_XY)
                .temporalTraitId(org.Aayush.routing.traits.temporal.TemporalTraitCatalog.TRAIT_CALENDAR)
                .timezonePolicyId(TemporalTimezonePolicyRegistry.POLICY_UTC)
                .transitionTraitId(TransitionTraitCatalog.TRAIT_EDGE_BASED)
                .build();
    }

    private TraitBundleSpec externalOnlyBundle() {
        return TraitBundleSpec.builder()
                .bundleId("EXTERNAL_ONLY")
                .addressingTraitId(org.Aayush.routing.traits.addressing.AddressingTraitCatalog.TRAIT_EXTERNAL_ID_ONLY)
                .temporalTraitId(org.Aayush.routing.traits.temporal.TemporalTraitCatalog.TRAIT_CALENDAR)
                .timezonePolicyId(TemporalTimezonePolicyRegistry.POLICY_UTC)
                .transitionTraitId(TransitionTraitCatalog.TRAIT_EDGE_BASED)
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

    private double[] linearXyCoordinates() {
        return new double[]{
                0.0d, 0.0d,
                1.0d, 0.0d,
                2.0d, 0.0d,
                3.0d, 0.0d,
                4.0d, 0.0d
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
        int modelVersion = builder.createString("stage18-trait-registry-test");
        int timezone = builder.createString("UTC");
        Metadata.startMetadata(builder);
        Metadata.addSchemaVersion(builder, 1);
        Metadata.addModelVersion(builder, modelVersion);
        Metadata.addTimeUnit(builder, org.Aayush.serialization.flatbuffers.taro.model.TimeUnit.SECONDS);
        Metadata.addTickDurationNs(builder, 1_000_000_000L);
        Metadata.addProfileTimezone(builder, timezone);
        return Metadata.endMetadata(builder);
    }
}
