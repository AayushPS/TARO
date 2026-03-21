package org.Aayush.routing.topology;

import org.Aayush.routing.core.RouteCore;
import org.Aayush.routing.cost.CostEngine;
import org.Aayush.routing.graph.EdgeGraph;
import org.Aayush.routing.graph.TurnCostMap;
import org.Aayush.routing.heuristic.LandmarkArtifact;
import org.Aayush.routing.heuristic.LandmarkPreprocessor;
import org.Aayush.routing.heuristic.LandmarkStore;
import org.Aayush.routing.overlay.LiveOverlay;
import org.Aayush.routing.profile.ProfileStore;
import org.Aayush.routing.spatial.SpatialRuntime;

import java.nio.ByteOrder;
import java.util.Objects;

/**
 * Materializes one rebuilt runtime snapshot from source-level topology inputs.
 */
public final class TopologyRuntimeFactory {
    private final TopologyModelCompiler modelCompiler;
    private final TopologyRuntimeTemplate runtimeTemplate;

    public TopologyRuntimeFactory(TopologyRuntimeTemplate runtimeTemplate) {
        this(new TopologyModelCompiler(), runtimeTemplate);
    }

    public TopologyRuntimeFactory(TopologyModelCompiler modelCompiler, TopologyRuntimeTemplate runtimeTemplate) {
        this.modelCompiler = Objects.requireNonNull(modelCompiler, "modelCompiler");
        this.runtimeTemplate = Objects.requireNonNull(runtimeTemplate, "runtimeTemplate");
    }

    public TopologyRuntimeSnapshot buildSnapshot(
            TopologyModelSource source,
            TopologyVersion topologyVersion,
            long publicationTicks,
            TopologyRuntimeSnapshot previousSnapshot
    ) {
        return buildSnapshot(source, topologyVersion, publicationTicks, previousSnapshot, null);
    }

    public TopologyRuntimeSnapshot buildSnapshot(
            TopologyModelSource source,
            TopologyVersion topologyVersion,
            long publicationTicks,
            TopologyRuntimeSnapshot previousSnapshot,
            TopologyModelSource previousSource
    ) {
        TopologyModelSource nonNullSource = Objects.requireNonNull(source, "source");
        TopologyVersion nonNullTopologyVersion = Objects.requireNonNull(topologyVersion, "topologyVersion");
        TopologyIndexLayout candidateLayout = TopologyIndexLayout.fromSource(nonNullSource);
        TopologyIncidentIndex incidentIndex = TopologyIncidentIndex.fromLayout(candidateLayout);
        CompiledTopologyModel compiled = modelCompiler.compile(nonNullSource);

        EdgeGraph edgeGraph = EdgeGraph.fromFlatBuffer(compiled.getModelBuffer().duplicate().order(ByteOrder.LITTLE_ENDIAN));
        ProfileStore profileStore = ProfileStore.fromFlatBuffer(compiled.getModelBuffer().duplicate().order(ByteOrder.LITTLE_ENDIAN));
        TurnCostMap turnCostMap = TurnCostMap.fromFlatBuffer(compiled.getModelBuffer().duplicate().order(ByteOrder.LITTLE_ENDIAN));

        LiveOverlay liveOverlay = createLiveOverlay(publicationTicks, previousSnapshot);
        CostEngine costEngine = new CostEngine(
                edgeGraph,
                profileStore,
                liveOverlay,
                turnCostMap,
                nonNullSource.getEngineTimeUnit(),
                runtimeTemplate.getBucketSizeSeconds(),
                runtimeTemplate.getTemporalSamplingPolicy()
        );

        SpatialRuntime spatialRuntime = compiled.isCoordinatesEnabled() && runtimeTemplate.isSpatialEnabled()
                ? SpatialRuntime.fromFlatBuffer(compiled.getModelBuffer().duplicate().order(ByteOrder.LITTLE_ENDIAN), edgeGraph, true)
                : null;
        LandmarkStore landmarkStore = buildLandmarkStore(edgeGraph, profileStore);

        RouteCore routeCore = RouteCore.builder()
                .edgeGraph(edgeGraph)
                .profileStore(profileStore)
                .costEngine(costEngine)
                .nodeIdMapper(compiled.getNodeIdMapper())
                .landmarkStore(landmarkStore)
                .spatialRuntime(spatialRuntime)
                .executionRuntimeConfig(runtimeTemplate.getExecutionRuntimeConfig())
                .traitBundleRuntimeConfig(runtimeTemplate.getTraitBundleRuntimeConfig())
                .addressingRuntimeConfig(runtimeTemplate.getAddressingRuntimeConfig())
                .temporalRuntimeConfig(runtimeTemplate.getTemporalRuntimeConfig())
                .transitionRuntimeConfig(runtimeTemplate.getTransitionRuntimeConfig())
                .build();

        return TopologyRuntimeSnapshot.builder()
                .routeCore(routeCore)
                .topologyVersion(nonNullTopologyVersion)
                .failureQuarantine(createFailureQuarantine(
                        nonNullTopologyVersion,
                        publicationTicks,
                        previousSnapshot,
                        previousSource,
                        candidateLayout,
                        incidentIndex
                ))
                .build();
    }

    private LiveOverlay createLiveOverlay(long publicationTicks, TopologyRuntimeSnapshot previousSnapshot) {
        if (runtimeTemplate.isPreserveActiveLiveOverlay() && previousSnapshot != null) {
            return previousSnapshot.getRouteCore().liveOverlayContract().copyActiveSnapshot(publicationTicks);
        }
        return new LiveOverlay(
                runtimeTemplate.getLiveOverlayCapacity(),
                runtimeTemplate.getLiveOverlayCapacityPolicy(),
                runtimeTemplate.getLiveOverlayWriteCleanupBudget(),
                runtimeTemplate.isLiveOverlayReadCleanupEnabled()
        );
    }

    private LandmarkStore buildLandmarkStore(EdgeGraph edgeGraph, ProfileStore profileStore) {
        if (runtimeTemplate.getLandmarkPreprocessorConfig() == null) {
            return null;
        }
        LandmarkArtifact artifact = LandmarkPreprocessor.preprocess(
                edgeGraph,
                profileStore,
                runtimeTemplate.getLandmarkPreprocessorConfig()
        );
        return LandmarkStore.fromArtifact(artifact);
    }

    private FailureQuarantine createFailureQuarantine(
            TopologyVersion topologyVersion,
            long publicationTicks,
            TopologyRuntimeSnapshot previousSnapshot,
            TopologyModelSource previousSource,
            TopologyIndexLayout candidateLayout,
            TopologyIncidentIndex incidentIndex
    ) {
        FailureQuarantine quarantine = new FailureQuarantine(
                "quarantine-" + topologyVersion.getTopologyVersion(),
                incidentIndex
        );
        if (previousSnapshot == null || previousSource == null) {
            return quarantine;
        }

        FailureQuarantine.Snapshot activeFailures = previousSnapshot.getFailureQuarantine().snapshot(publicationTicks);
        if (!activeFailures.hasActiveFailures()) {
            return quarantine;
        }

        TopologyIndexLayout previousLayout = TopologyIndexLayout.fromSource(previousSource);
        for (FailureQuarantine.FailureRecord record : activeFailures.activeEdgeFailures().values()) {
            if (record.subjectId() < 0 || record.subjectId() >= previousLayout.edgeCount()) {
                continue;
            }
            String edgeId = previousLayout.edgeId(record.subjectId());
            Integer candidateEdgeIndex = candidateLayout.findEdgeIndex(edgeId);
            if (candidateEdgeIndex != null) {
                quarantine.quarantineEdge(candidateEdgeIndex, record.validUntilTicks(), record.reason(), record.source());
            }
        }
        for (FailureQuarantine.FailureRecord record : activeFailures.activeNodeFailures().values()) {
            if (record.subjectId() < 0 || record.subjectId() >= previousLayout.nodeCount()) {
                continue;
            }
            String nodeId = previousLayout.nodeId(record.subjectId());
            Integer candidateNodeIndex = candidateLayout.findNodeIndex(nodeId);
            if (candidateNodeIndex != null) {
                quarantine.quarantineNode(candidateNodeIndex, record.validUntilTicks(), record.reason(), record.source());
            }
        }
        return quarantine;
    }
}
