package org.Aayush.app;

import com.google.flatbuffers.FlatBufferBuilder;
import org.Aayush.core.id.FastUtilIDMapper;
import org.Aayush.core.id.IDMapper;
import org.Aayush.core.time.TimeUtils;
import org.Aayush.api.OperationalMetricsService;
import org.Aayush.routing.core.FutureMatrixEvaluator;
import org.Aayush.routing.core.FutureRouteEvaluator;
import org.Aayush.routing.core.RouteCore;
import org.Aayush.routing.cost.CostEngine;
import org.Aayush.routing.execution.ExecutionRuntimeConfig;
import org.Aayush.routing.future.FutureMatrixService;
import org.Aayush.routing.future.FutureRouteService;
import org.Aayush.routing.future.InMemoryEphemeralMatrixResultStore;
import org.Aayush.routing.future.InMemoryEphemeralRouteResultStore;
import org.Aayush.routing.future.ScenarioBundle;
import org.Aayush.routing.future.ScenarioBundleRequest;
import org.Aayush.routing.future.ScenarioBundleResolver;
import org.Aayush.routing.future.ScenarioDefinition;
import org.Aayush.routing.future.TopologyAwareFutureMatrixService;
import org.Aayush.routing.future.TopologyAwareFutureRouteService;
import org.Aayush.routing.graph.EdgeGraph;
import org.Aayush.routing.overlay.LiveOverlay;
import org.Aayush.routing.overlay.LiveUpdate;
import org.Aayush.routing.profile.ProfileStore;
import org.Aayush.routing.topology.FailureQuarantine;
import org.Aayush.routing.topology.ReloadCompatibilityPolicy;
import org.Aayush.routing.topology.TopologyReloadCoordinator;
import org.Aayush.routing.topology.TopologyRuntimeSnapshot;
import org.Aayush.routing.topology.TopologyVersion;
import org.Aayush.routing.traits.addressing.AddressingRuntimeConfig;
import org.Aayush.routing.traits.temporal.TemporalRuntimeConfig;
import org.Aayush.routing.traits.transition.TransitionRuntimeConfig;
import org.Aayush.serialization.flatbuffers.taro.model.GraphTopology;
import org.Aayush.serialization.flatbuffers.taro.model.Metadata;
import org.Aayush.serialization.flatbuffers.taro.model.Model;
import org.Aayush.serialization.flatbuffers.taro.model.TemporalProfile;
import org.Aayush.serialization.flatbuffers.taro.model.TimeUnit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main-source demo bootstrap so Spring Boot starts with a usable future-routing runtime.
 * This can be disabled with {@code taro.demo-topology.enabled=false} or replaced by user-provided beans.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "taro.demo-topology", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DemoFutureRoutingBootstrapConfiguration {
    private static final Instant BASE_INSTANT = Instant.parse("2026-03-21T00:00:00Z");
    private static final int ALL_DAYS_MASK = 0x7F;
    private static final int BUCKET_SIZE_SECONDS = 3_600;

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock taroClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean(InMemoryEphemeralRouteResultStore.class)
    InMemoryEphemeralRouteResultStore routeResultStore(Clock clock) {
        return new InMemoryEphemeralRouteResultStore(clock);
    }

    @Bean
    @ConditionalOnMissingBean(InMemoryEphemeralMatrixResultStore.class)
    InMemoryEphemeralMatrixResultStore matrixResultStore(Clock clock) {
        return new InMemoryEphemeralMatrixResultStore(clock);
    }

    @Bean
    @ConditionalOnMissingBean(ScenarioBundleResolver.class)
    ScenarioBundleResolver demoScenarioBundleResolver() {
        return (
                ScenarioBundleRequest request,
                org.Aayush.routing.cost.CostEngine baseCostEngine,
                org.Aayush.routing.traits.temporal.ResolvedTemporalContext temporalContext,
                TopologyVersion topologyVersion,
                FailureQuarantine.Snapshot quarantineSnapshot,
                Clock clock
        ) -> ScenarioBundle.builder()
                .scenarioBundleId("bundle-demo")
                .generatedAt(clock.instant())
                .validUntil(clock.instant().plus(Duration.ofMinutes(10)))
                .horizonTicks(request.getHorizonTicks())
                .topologyVersion(topologyVersion)
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
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(FutureRouteService.class)
    FutureRouteService futureRouteService(
            ScenarioBundleResolver demoScenarioBundleResolver,
            Clock clock,
            InMemoryEphemeralRouteResultStore routeResultStore
    ) {
        return new FutureRouteService(
                new FutureRouteEvaluator(demoScenarioBundleResolver, clock),
                routeResultStore
        );
    }

    @Bean
    @ConditionalOnMissingBean(FutureMatrixService.class)
    FutureMatrixService futureMatrixService(
            ScenarioBundleResolver demoScenarioBundleResolver,
            Clock clock,
            InMemoryEphemeralMatrixResultStore matrixResultStore
    ) {
        return new FutureMatrixService(
                new FutureMatrixEvaluator(demoScenarioBundleResolver, clock),
                matrixResultStore
        );
    }

    @Bean
    @ConditionalOnMissingBean(TopologyReloadCoordinator.class)
    TopologyReloadCoordinator topologyReloadCoordinator(
            OperationalMetricsService operationalMetricsService,
            InMemoryEphemeralRouteResultStore routeResultStore,
            InMemoryEphemeralMatrixResultStore matrixResultStore
    ) {
        return new TopologyReloadCoordinator(
                initialSnapshot(),
                ReloadCompatibilityPolicy.invalidateStaleTopologyResults(),
                List.of(routeResultStore, matrixResultStore),
                operationalMetricsService
        );
    }

    @Bean
    @ConditionalOnMissingBean(TopologyAwareFutureRouteService.class)
    TopologyAwareFutureRouteService topologyAwareFutureRouteService(
            TopologyReloadCoordinator topologyReloadCoordinator,
            FutureRouteService futureRouteService
    ) {
        return new TopologyAwareFutureRouteService(topologyReloadCoordinator, futureRouteService);
    }

    @Bean
    @ConditionalOnMissingBean(TopologyAwareFutureMatrixService.class)
    TopologyAwareFutureMatrixService topologyAwareFutureMatrixService(
            TopologyReloadCoordinator topologyReloadCoordinator,
            FutureMatrixService futureMatrixService
    ) {
        return new TopologyAwareFutureMatrixService(topologyReloadCoordinator, futureMatrixService);
    }

    static TopologyRuntimeSnapshot initialSnapshot() {
        return snapshot(createRouteCore(createAlternativeRouteFixture()), "topo-demo");
    }

    private static RouteCore createRouteCore(Fixture fixture) {
        return RouteCore.builder()
                .edgeGraph(fixture.edgeGraph())
                .profileStore(fixture.profileStore())
                .costEngine(fixture.costEngine())
                .nodeIdMapper(fixture.nodeIdMapper())
                .executionRuntimeConfig(ExecutionRuntimeConfig.dijkstra())
                .addressingRuntimeConfig(AddressingRuntimeConfig.defaultRuntime())
                .temporalRuntimeConfig(TemporalRuntimeConfig.calendarUtc())
                .transitionRuntimeConfig(TransitionRuntimeConfig.edgeBased())
                .build();
    }

    private static TopologyRuntimeSnapshot snapshot(RouteCore routeCore, String topologyId) {
        TopologyVersion topologyVersion = TopologyVersion.builder()
                .modelVersion("model-demo")
                .topologyVersion(topologyId)
                .generatedAt(BASE_INSTANT)
                .sourceDataLineageHash("lineage-" + topologyId)
                .changeSetHash("change-" + topologyId)
                .build();
        return TopologyRuntimeSnapshot.builder()
                .routeCore(routeCore)
                .topologyVersion(topologyVersion)
                .failureQuarantine(new FailureQuarantine("quarantine-" + topologyId))
                .build();
    }

    private static Fixture createAlternativeRouteFixture() {
        return createFixture(
                4,
                new int[]{0, 2, 3, 4, 4},
                new int[]{1, 2, 3, 3},
                new int[]{0, 0, 1, 2},
                new float[]{1.0f, 2.0f, 1.0f, 1.0f},
                new int[]{1, 1, 1, 1},
                new double[]{
                        0.0d, 0.0d,
                        1.0d, 0.0d,
                        0.0d, 1.0d,
                        1.0d, 1.0d
                },
                new ProfileSpec(1, ALL_DAYS_MASK, new float[]{1.0f}, 1.0f)
        );
    }

    private static Fixture createFixture(
            int nodeCount,
            int[] firstEdge,
            int[] edgeTarget,
            int[] edgeOrigin,
            float[] baseWeights,
            int[] edgeProfileIds,
            double[] coordinates,
            ProfileSpec... profiles
    ) {
        ByteBuffer model = buildModelBuffer(
                nodeCount,
                firstEdge,
                edgeTarget,
                edgeOrigin,
                baseWeights,
                edgeProfileIds,
                coordinates,
                profiles
        );
        EdgeGraph edgeGraph = EdgeGraph.fromFlatBuffer(model.duplicate().order(ByteOrder.LITTLE_ENDIAN));
        ProfileStore profileStore = ProfileStore.fromFlatBuffer(model.duplicate().order(ByteOrder.LITTLE_ENDIAN));
        CostEngine costEngine = new CostEngine(
                edgeGraph,
                profileStore,
                new LiveOverlay(Math.max(16, edgeGraph.edgeCount())),
                TimeUtils.EngineTimeUnit.SECONDS,
                BUCKET_SIZE_SECONDS
        );

        Map<String, Integer> mappings = new HashMap<>(nodeCount);
        for (int index = 0; index < nodeCount; index++) {
            mappings.put("N" + index, index);
        }
        IDMapper mapper = new FastUtilIDMapper(mappings);
        return new Fixture(edgeGraph, profileStore, costEngine, mapper);
    }

    private static ByteBuffer buildModelBuffer(
            int nodeCount,
            int[] firstEdge,
            int[] edgeTarget,
            int[] edgeOrigin,
            float[] baseWeights,
            int[] edgeProfileIds,
            double[] coordinates,
            ProfileSpec... profiles
    ) {
        FlatBufferBuilder builder = new FlatBufferBuilder(4096);

        int firstEdgeVec = GraphTopology.createFirstEdgeVector(builder, firstEdge);
        int edgeTargetVec = GraphTopology.createEdgeTargetVector(builder, edgeTarget);
        int edgeOriginVec = GraphTopology.createEdgeOriginVector(builder, edgeOrigin);
        int baseWeightsVec = GraphTopology.createBaseWeightsVector(builder, baseWeights);
        int profileIdVec = GraphTopology.createEdgeProfileIdVector(builder, edgeProfileIds);

        int coordinatesVec = 0;
        if (coordinates != null) {
            GraphTopology.startCoordinatesVector(builder, nodeCount);
            for (int index = nodeCount - 1; index >= 0; index--) {
                org.Aayush.serialization.flatbuffers.taro.model.Coordinate.createCoordinate(
                        builder,
                        coordinates[index * 2],
                        coordinates[(index * 2) + 1]
                );
            }
            coordinatesVec = builder.endVector();
        }

        GraphTopology.startGraphTopology(builder);
        GraphTopology.addNodeCount(builder, nodeCount);
        GraphTopology.addEdgeCount(builder, edgeTarget.length);
        GraphTopology.addFirstEdge(builder, firstEdgeVec);
        GraphTopology.addEdgeTarget(builder, edgeTargetVec);
        GraphTopology.addEdgeOrigin(builder, edgeOriginVec);
        GraphTopology.addBaseWeights(builder, baseWeightsVec);
        GraphTopology.addEdgeProfileId(builder, profileIdVec);
        if (coordinatesVec != 0) {
            GraphTopology.addCoordinates(builder, coordinatesVec);
        }
        int topologyOffset = GraphTopology.endGraphTopology(builder);

        int profilesOffset = 0;
        if (profiles != null && profiles.length > 0) {
            int[] profileOffsets = new int[profiles.length];
            for (int index = 0; index < profiles.length; index++) {
                ProfileSpec profile = profiles[index];
                int bucketsOffset = TemporalProfile.createBucketsVector(builder, profile.buckets());
                profileOffsets[index] = TemporalProfile.createTemporalProfile(
                        builder,
                        profile.profileId(),
                        profile.dayMask(),
                        bucketsOffset,
                        profile.multiplier()
                );
            }
            profilesOffset = Model.createProfilesVector(builder, profileOffsets);
        }

        int metadataOffset = createMetadata(builder);

        Model.startModel(builder);
        Model.addMetadata(builder, metadataOffset);
        Model.addTopology(builder, topologyOffset);
        if (profilesOffset != 0) {
            Model.addProfiles(builder, profilesOffset);
        }
        int root = Model.endModel(builder);
        Model.finishModelBuffer(builder, root);
        return ByteBuffer.wrap(builder.sizedByteArray()).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static int createMetadata(FlatBufferBuilder builder) {
        int modelVersion = builder.createString("demo-v1");
        int timezone = builder.createString("UTC");
        Metadata.startMetadata(builder);
        Metadata.addSchemaVersion(builder, 1);
        Metadata.addModelVersion(builder, modelVersion);
        Metadata.addTimeUnit(builder, TimeUnit.SECONDS);
        Metadata.addTickDurationNs(builder, 1_000_000_000L);
        Metadata.addProfileTimezone(builder, timezone);
        return Metadata.endMetadata(builder);
    }

    private record ProfileSpec(int profileId, int dayMask, float[] buckets, float multiplier) {
    }

    private record Fixture(
            EdgeGraph edgeGraph,
            ProfileStore profileStore,
            CostEngine costEngine,
            IDMapper nodeIdMapper
    ) {
    }
}
