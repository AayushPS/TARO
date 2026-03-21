package org.Aayush.routing.topology;

import org.Aayush.routing.core.MatrixRequest;
import org.Aayush.routing.core.RouteCore;
import org.Aayush.routing.core.RouteRequest;
import org.Aayush.routing.core.RoutingAlgorithm;
import org.Aayush.routing.execution.ExecutionRuntimeConfig;
import org.Aayush.routing.future.FutureMatrixRequest;
import org.Aayush.routing.future.FutureRouteRequest;
import org.Aayush.routing.future.ScenarioBundle;
import org.Aayush.routing.future.ScenarioBundleRequest;
import org.Aayush.routing.future.ScenarioBundleResolver;
import org.Aayush.routing.future.ScenarioDefinition;
import org.Aayush.routing.heuristic.HeuristicType;
import org.Aayush.routing.topology.TopologyModelSource.EdgeDefinition;
import org.Aayush.routing.topology.TopologyModelSource.NodeDefinition;
import org.Aayush.routing.traits.addressing.AddressingRuntimeConfig;
import org.Aayush.routing.traits.temporal.TemporalRuntimeConfig;
import org.Aayush.routing.traits.transition.TransitionRuntimeConfig;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

final class TopologyTestFixtures {
    static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-03-21T00:00:00Z"), ZoneOffset.UTC);

    private TopologyTestFixtures() {
    }

    static TopologyRuntimeTemplate runtimeTemplate() {
        return TopologyRuntimeTemplate.builder()
                .executionRuntimeConfig(ExecutionRuntimeConfig.dijkstra())
                .addressingRuntimeConfig(AddressingRuntimeConfig.defaultRuntime())
                .temporalRuntimeConfig(TemporalRuntimeConfig.calendarUtc())
                .transitionRuntimeConfig(TransitionRuntimeConfig.edgeBased())
                .liveOverlayCapacity(64)
                .build();
    }

    static Harness createHarness(
            TopologyModelSource source,
            ReloadCompatibilityPolicy compatibilityPolicy,
            List<TopologyBoundResultStore> resultStores,
            List<TopologyValidationGate> validationGates
    ) {
        TopologyRuntimeTemplate runtimeTemplate = runtimeTemplate();
        TopologyRuntimeFactory runtimeFactory = new TopologyRuntimeFactory(runtimeTemplate);
        TopologyRuntimeSnapshot initialSnapshot = runtimeFactory.buildSnapshot(
                source,
                topologyVersion("topo-initial"),
                0L,
                null
        );
        TopologyReloadCoordinator reloadCoordinator = new TopologyReloadCoordinator(
                initialSnapshot,
                compatibilityPolicy,
                resultStores
        );
        TopologyPublicationService publicationService = new TopologyPublicationService(
                source,
                new StructuralChangeApplier(),
                runtimeFactory,
                reloadCoordinator,
                validationGates,
                FIXED_CLOCK
        );
        return new Harness(runtimeTemplate, runtimeFactory, reloadCoordinator, publicationService);
    }

    static Harness createHarness(TopologyModelSource source) {
        return createHarness(source, ReloadCompatibilityPolicy.invalidateStaleTopologyResults(), List.of(), List.of());
    }

    static TopologyVersion topologyVersion(String topologyId) {
        return TopologyVersion.builder()
                .modelVersion("model-v13")
                .topologyVersion(topologyId)
                .generatedAt(FIXED_CLOCK.instant())
                .sourceDataLineageHash("lineage-" + topologyId)
                .changeSetHash("change-" + topologyId)
                .build();
    }

    static RouteRequest routeRequest(String sourceExternalId, String targetExternalId) {
        return RouteRequest.builder()
                .sourceExternalId(sourceExternalId)
                .targetExternalId(targetExternalId)
                .departureTicks(0L)
                .algorithm(RoutingAlgorithm.DIJKSTRA)
                .heuristicType(HeuristicType.NONE)
                .build();
    }

    static MatrixRequest matrixRequest(String sourceExternalId, String targetExternalId) {
        return MatrixRequest.builder()
                .sourceExternalId(sourceExternalId)
                .targetExternalId(targetExternalId)
                .departureTicks(0L)
                .algorithm(RoutingAlgorithm.DIJKSTRA)
                .heuristicType(HeuristicType.NONE)
                .build();
    }

    static FutureRouteRequest futureRouteRequest(String sourceExternalId, String targetExternalId) {
        return FutureRouteRequest.builder()
                .routeRequest(routeRequest(sourceExternalId, targetExternalId))
                .resultTtl(Duration.ofMinutes(10))
                .build();
    }

    static FutureMatrixRequest futureMatrixRequest(String sourceExternalId, String targetExternalId) {
        return FutureMatrixRequest.builder()
                .matrixRequest(matrixRequest(sourceExternalId, targetExternalId))
                .resultTtl(Duration.ofMinutes(10))
                .build();
    }

    static ScenarioBundleResolver strictQuarantineResolver() {
        return (ScenarioBundleRequest request,
                org.Aayush.routing.graph.EdgeGraph edgeGraph,
                TopologyVersion topologyVersion,
                FailureQuarantine.Snapshot quarantineSnapshot,
                Clock clock) -> {
            ScenarioDefinition.ScenarioDefinitionBuilder scenario = ScenarioDefinition.builder()
                    .scenarioId("strict_quarantine")
                    .label("strict_quarantine")
                    .probability(1.0d)
                    .explanationTags(quarantineSnapshot.explanationTags());
            if (quarantineSnapshot.hasActiveFailures()) {
                scenario.liveUpdates(quarantineSnapshot.toLiveUpdates(edgeGraph));
            }
            return ScenarioBundle.builder()
                    .scenarioBundleId("bundle-" + topologyVersion.getTopologyVersion())
                    .generatedAt(clock.instant())
                    .validUntil(clock.instant().plus(Duration.ofMinutes(10)))
                    .horizonTicks(request.getHorizonTicks())
                    .topologyVersion(topologyVersion)
                    .quarantineSnapshotId(quarantineSnapshot.snapshotId())
                    .scenario(scenario.build())
                    .build();
        };
    }

    static TopologyModelSource gridSource(int width, int height) {
        TopologyModelSource.TopologyModelSourceBuilder builder = TopologyModelSource.builder()
                .modelVersion("grid-" + width + "x" + height)
                .profileTimezone("UTC")
                .profile(TopologyModelSource.ProfileDefinition.builder()
                        .profileId(1)
                        .dayMask(0x7F)
                        .bucket(1.0f)
                        .multiplier(1.0f)
                        .build());

        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int nodeId = row * width + col;
                builder.node(NodeDefinition.builder()
                        .nodeId("N" + nodeId)
                        .x((double) col)
                        .y((double) row)
                        .build());
            }
        }

        int edgeId = 0;
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int node = row * width + col;
                if (col + 1 < width) {
                    builder.edge(gridEdge(edgeId++, node, node + 1));
                    builder.edge(gridEdge(edgeId++, node + 1, node));
                }
                if (row + 1 < height) {
                    builder.edge(gridEdge(edgeId++, node, node + width));
                    builder.edge(gridEdge(edgeId++, node + width, node));
                }
            }
        }
        return builder.build();
    }

    private static EdgeDefinition gridEdge(int edgeId, int origin, int destination) {
        return EdgeDefinition.builder()
                .edgeId("E" + edgeId)
                .originNodeId("N" + origin)
                .destinationNodeId("N" + destination)
                .baseWeight(1.0f)
                .profileId(1)
                .build();
    }

    record Harness(
            TopologyRuntimeTemplate runtimeTemplate,
            TopologyRuntimeFactory runtimeFactory,
            TopologyReloadCoordinator reloadCoordinator,
            TopologyPublicationService publicationService
    ) {
        TopologyRuntimeSnapshot currentSnapshot() {
            return reloadCoordinator.currentSnapshot();
        }

        RouteCore currentRouteCore() {
            return currentSnapshot().getRouteCore();
        }
    }
}
