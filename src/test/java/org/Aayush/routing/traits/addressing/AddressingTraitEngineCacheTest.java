package org.Aayush.routing.traits.addressing;

import com.google.flatbuffers.FlatBufferBuilder;
import org.Aayush.routing.core.RouteRequest;
import org.Aayush.routing.core.RoutingAlgorithm;
import org.Aayush.routing.graph.EdgeGraph;
import org.Aayush.routing.heuristic.HeuristicType;
import org.Aayush.routing.spatial.SpatialRuntime;
import org.Aayush.routing.testutil.RoutingFixtureFactory;
import org.Aayush.serialization.flatbuffers.taro.model.GraphTopology;
import org.Aayush.serialization.flatbuffers.taro.model.KDNode;
import org.Aayush.serialization.flatbuffers.taro.model.Metadata;
import org.Aayush.serialization.flatbuffers.taro.model.Model;
import org.Aayush.serialization.flatbuffers.taro.model.SpatialIndex;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("AddressingTraitEngine Coordinate Cache Tests")
class AddressingTraitEngineCacheTest {

    @Test
    @DisplayName("Segmented LRU coordinate cache retains hot entries under churn")
    void testSegmentedLruRetainsHotEntries() {
        RoutingFixtureFactory.Fixture fixture = createLinearFixture();
        AddressingTraitCatalog traitCatalog = AddressingTraitCatalog.defaultCatalog();
        AddressingRuntimeBinder.Binding runtimeBinding = new AddressingRuntimeBinder()
                .bind(AddressingRuntimeConfig.defaultRuntime(), traitCatalog);
        SpatialRuntime spatialRuntime = buildSpatialRuntimeFromCoordinates(
                fixture.edgeGraph(),
                linearXyCoordinates()
        );
        AddressingTraitEngine.ResolveContext context = new AddressingTraitEngine.ResolveContext(
                fixture.edgeGraph(),
                fixture.nodeIdMapper(),
                spatialRuntime,
                traitCatalog,
                CoordinateStrategyRegistry.defaultRegistry(),
                AddressingPolicy.defaults(),
                runtimeBinding
        );

        AddressingTraitEngine engine = new AddressingTraitEngine(2, 1);

        AddressingTelemetry firstA = resolveRouteTelemetry(engine, context, 0.02d, 0.0d);
        AddressingTelemetry firstB = resolveRouteTelemetry(engine, context, 1.02d, 0.0d);
        AddressingTelemetry secondA = resolveRouteTelemetry(engine, context, 0.02d, 0.0d);
        AddressingTelemetry firstC = resolveRouteTelemetry(engine, context, 2.02d, 0.0d);
        AddressingTelemetry thirdA = resolveRouteTelemetry(engine, context, 0.02d, 0.0d);

        assertEquals(1, firstA.coordinateResolveCount());
        assertEquals(1, firstB.coordinateResolveCount());
        assertEquals(1, secondA.coordinateResolveCount());
        assertEquals(1, firstC.coordinateResolveCount());
        assertEquals(1, thirdA.coordinateResolveCount());

        Set<Long> cachedFirstBits = snapshotCoordinateFirstBits(engine);
        assertTrue(
                cachedFirstBits.contains(canonicalCoordinateBits(0.02d)),
                "LRU cache should retain recently used A entry"
        );
        assertTrue(
                cachedFirstBits.contains(canonicalCoordinateBits(2.02d)),
                "LRU cache should retain newest C entry"
        );
        assertFalse(
                cachedFirstBits.contains(canonicalCoordinateBits(1.02d)),
                "LRU cache should evict least-recently-used B entry"
        );
    }

    private AddressingTelemetry resolveRouteTelemetry(
            AddressingTraitEngine engine,
            AddressingTraitEngine.ResolveContext context,
            double sourceX,
            double sourceY
    ) {
        RouteRequest request = RouteRequest.builder()
                .sourceAddress(AddressInput.ofXY(sourceX, sourceY))
                .targetExternalId("N4")
                .allowMixedAddressing(true)
                .addressingTraitId(AddressingTraitCatalog.TRAIT_DEFAULT)
                .coordinateDistanceStrategyId(CoordinateStrategyRegistry.STRATEGY_XY)
                .maxSnapDistance(0.25d)
                .departureTicks(0L)
                .algorithm(RoutingAlgorithm.A_STAR)
                .heuristicType(HeuristicType.NONE)
                .build();
        return engine.resolveRoute(request, context).telemetry();
    }

    private RoutingFixtureFactory.Fixture createLinearFixture() {
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

    private SpatialRuntime buildSpatialRuntimeFromCoordinates(EdgeGraph edgeGraph, double[] coordinates) {
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
        int modelVersion = builder.createString("addressing-cache-test");
        int timezone = builder.createString("UTC");
        Metadata.startMetadata(builder);
        Metadata.addSchemaVersion(builder, 1);
        Metadata.addModelVersion(builder, modelVersion);
        Metadata.addTimeUnit(builder, org.Aayush.serialization.flatbuffers.taro.model.TimeUnit.SECONDS);
        Metadata.addTickDurationNs(builder, 1_000_000_000L);
        Metadata.addProfileTimezone(builder, timezone);
        return Metadata.endMetadata(builder);
    }

    private Set<Long> snapshotCoordinateFirstBits(AddressingTraitEngine engine) {
        try {
            Field cacheField = AddressingTraitEngine.class.getDeclaredField("coordinateResolutionCache");
            cacheField.setAccessible(true);
            Object cache = cacheField.get(engine);

            Field segmentsField = cache.getClass().getDeclaredField("segments");
            segmentsField.setAccessible(true);
            Object[] segments = (Object[]) segmentsField.get(cache);

            Set<Long> coordinateFirstBits = new HashSet<>();
            for (Object segment : segments) {
                Field entriesField = segment.getClass().getDeclaredField("entries");
                entriesField.setAccessible(true);
                @SuppressWarnings("unchecked")
                java.util.Map<Object, ?> entries = (java.util.Map<Object, ?>) entriesField.get(segment);
                for (Object key : entries.keySet()) {
                    Field firstBitsField = key.getClass().getDeclaredField("coordinateFirstBits");
                    firstBitsField.setAccessible(true);
                    coordinateFirstBits.add((Long) firstBitsField.get(key));
                }
            }
            return coordinateFirstBits;
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException("failed to inspect coordinate cache state", ex);
        }
    }

    private long canonicalCoordinateBits(double value) {
        if (value == 0.0d) {
            return Double.doubleToLongBits(0.0d);
        }
        return Double.doubleToLongBits(value);
    }
}
