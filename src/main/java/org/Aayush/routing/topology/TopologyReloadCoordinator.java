package org.Aayush.routing.topology;

import org.Aayush.routing.future.EphemeralRouteResultStore;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Atomic coordinator for topology-bound runtime snapshot swaps.
 */
public final class TopologyReloadCoordinator {
    private final AtomicReference<TopologyRuntimeSnapshot> activeSnapshot;
    private final ReloadCompatibilityPolicy compatibilityPolicy;
    private final List<TopologyBoundResultStore> resultStores;

    public TopologyReloadCoordinator(
            TopologyRuntimeSnapshot initialSnapshot,
            ReloadCompatibilityPolicy compatibilityPolicy,
            EphemeralRouteResultStore resultStore
    ) {
        this(initialSnapshot, compatibilityPolicy, List.of(resultStore));
    }

    public TopologyReloadCoordinator(
            TopologyRuntimeSnapshot initialSnapshot,
            ReloadCompatibilityPolicy compatibilityPolicy,
            List<? extends TopologyBoundResultStore> resultStores
    ) {
        this.activeSnapshot = new AtomicReference<>(validateSnapshot(initialSnapshot));
        this.compatibilityPolicy = Objects.requireNonNull(compatibilityPolicy, "compatibilityPolicy");
        this.resultStores = List.copyOf(Objects.requireNonNull(resultStores, "resultStores"));
    }

    public TopologyRuntimeSnapshot currentSnapshot() {
        return activeSnapshot.get();
    }

    public TopologyVersion validateReload(TopologyRuntimeSnapshot candidateSnapshot) {
        return validateSnapshot(candidateSnapshot).getTopologyVersion();
    }

    public TopologyVersion applyReload(TopologyRuntimeSnapshot candidateSnapshot) {
        TopologyRuntimeSnapshot validatedCandidate = validateSnapshot(candidateSnapshot);
        TopologyRuntimeSnapshot previous = activeSnapshot.getAndSet(validatedCandidate);
        compatibilityPolicy.onReload(previous.getTopologyVersion(), validatedCandidate.getTopologyVersion(), resultStores);
        return validatedCandidate.getTopologyVersion();
    }

    private static TopologyRuntimeSnapshot validateSnapshot(TopologyRuntimeSnapshot snapshot) {
        TopologyRuntimeSnapshot nonNullSnapshot = Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(nonNullSnapshot.getRouteCore(), "snapshot.routeCore");
        Objects.requireNonNull(nonNullSnapshot.getTopologyVersion(), "snapshot.topologyVersion");
        Objects.requireNonNull(nonNullSnapshot.getFailureQuarantine(), "snapshot.failureQuarantine");
        return nonNullSnapshot;
    }
}
