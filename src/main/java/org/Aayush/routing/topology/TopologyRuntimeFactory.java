package org.Aayush.routing.topology;

import org.Aayush.routing.core.RouteCore;
import org.Aayush.routing.cost.CostEngine;
import org.Aayush.routing.graph.EdgeGraph;
import org.Aayush.routing.graph.TurnCostMap;
import org.Aayush.routing.heuristic.LandmarkArtifact;
import org.Aayush.routing.heuristic.LandmarkPreprocessor;
import org.Aayush.routing.heuristic.LandmarkStore;
import org.Aayush.routing.overlay.LiveOverlay;
import org.Aayush.routing.profile.ProfileRecurrenceCalibrationStore;
import org.Aayush.routing.profile.ProfileRecencyCalibrationStore;
import org.Aayush.routing.profile.ProfileStore;
import org.Aayush.routing.spatial.SpatialRuntime;
import org.Aayush.routing.traits.temporal.TemporalGranularityLossPolicy;
import org.Aayush.routing.traits.temporal.TemporalTraitCatalog;

import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
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
        validateTemporalGranularityContract(nonNullSource);

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
                runtimeTemplate.getTemporalSamplingPolicy(),
                edgeId -> "edge '" + candidateLayout.edgeId(edgeId) + "'",
                profileValidationMode(),
                buildRecurrenceCalibrationStore(nonNullSource),
                buildRecencyCalibrationStore(nonNullSource)
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
                quarantine.quarantineEdge(
                        candidateEdgeIndex,
                        record.validUntilTicks(),
                        record.observedAtTicks(),
                        record.reason(),
                        record.source()
                );
            }
        }
        for (FailureQuarantine.FailureRecord record : activeFailures.activeNodeFailures().values()) {
            if (record.subjectId() < 0 || record.subjectId() >= previousLayout.nodeCount()) {
                continue;
            }
            String nodeId = previousLayout.nodeId(record.subjectId());
            Integer candidateNodeIndex = candidateLayout.findNodeIndex(nodeId);
            if (candidateNodeIndex != null) {
                quarantine.quarantineNode(
                        candidateNodeIndex,
                        record.validUntilTicks(),
                        record.observedAtTicks(),
                        record.reason(),
                        record.source()
                );
            }
        }
        return quarantine;
    }

    private CostEngine.ProfileValidationMode profileValidationMode() {
        if (runtimeTemplate.getTemporalRuntimeConfig() != null
                && TemporalTraitCatalog.TRAIT_CALENDAR.equals(runtimeTemplate.getTemporalRuntimeConfig().getTemporalTraitId())) {
            return CostEngine.ProfileValidationMode.DAY_MASK_AWARE_WEEKLY;
        }
        return CostEngine.ProfileValidationMode.DAILY_ONLY;
    }

    private ProfileRecurrenceCalibrationStore buildRecurrenceCalibrationStore(TopologyModelSource source) {
        Map<Integer, ProfileRecurrenceCalibrationStore.ProfileRecurrenceCalibration> calibrationByProfileId = new HashMap<>();
        for (TopologyModelSource.ProfileDefinition profile : source.getProfiles()) {
            if (profile.getRecurringSignalFlavor() == null) {
                continue;
            }
            calibrationByProfileId.put(
                    profile.getProfileId(),
                    new ProfileRecurrenceCalibrationStore.ProfileRecurrenceCalibration(
                            profile.getRecurringObservationCount(),
                            profile.getRecurringConfidence(),
                            profile.getRecurringSignalFlavor(),
                            ProfileRecurrenceCalibrationStore.CalibrationSource.EXPLICIT_SOURCE
                    )
            );
        }
        return calibrationByProfileId.isEmpty()
                ? ProfileRecurrenceCalibrationStore.empty()
                : new ProfileRecurrenceCalibrationStore(calibrationByProfileId);
    }

    private ProfileRecencyCalibrationStore buildRecencyCalibrationStore(TopologyModelSource source) {
        Map<Integer, ProfileRecencyCalibrationStore.ProfileRecencyCalibration> calibrationByProfileId = new HashMap<>();
        for (TopologyModelSource.ProfileDefinition profile : source.getProfiles()) {
            if (profile.getLastObservedAtTicks() == null) {
                continue;
            }
            calibrationByProfileId.put(
                    profile.getProfileId(),
                    new ProfileRecencyCalibrationStore.ProfileRecencyCalibration(
                            profile.getLastObservedAtTicks(),
                            ProfileRecencyCalibrationStore.CalibrationSource.EXPLICIT_SOURCE
                    )
            );
        }
        return calibrationByProfileId.isEmpty()
                ? ProfileRecencyCalibrationStore.empty()
                : new ProfileRecencyCalibrationStore(calibrationByProfileId);
    }

    private void validateTemporalGranularityContract(TopologyModelSource source) {
        if (runtimeTemplate.getTemporalRuntimeConfig() == null) {
            return;
        }
        long driftBudgetSeconds = runtimeTemplate.getTemporalRuntimeConfig().getMaxDiscretizationDrift().toSeconds();
        long driftBudgetTicks = Math.multiplyExact(driftBudgetSeconds, source.getEngineTimeUnit().ticksPerSecond());
        long bucketTicks = Math.multiplyExact(runtimeTemplate.getBucketSizeSeconds(), source.getEngineTimeUnit().ticksPerSecond());
        long worstCaseDriftTicks = (bucketTicks + 1L) / 2L;
        if (runtimeTemplate.getTemporalRuntimeConfig().getGranularityLossPolicy() == TemporalGranularityLossPolicy.REJECT_EXCESS_DRIFT
                && worstCaseDriftTicks > driftBudgetTicks) {
            throw new IllegalArgumentException(
                    "bucketSizeSeconds="
                            + runtimeTemplate.getBucketSizeSeconds()
                            + " exceeds temporal drift budget "
                            + driftBudgetSeconds
                            + "s under "
                            + TemporalGranularityLossPolicy.REJECT_EXCESS_DRIFT
                            + " (worst-case discretization drift="
                            + worstCaseDriftTicks
                            + " ticks)"
            );
        }
    }
}
