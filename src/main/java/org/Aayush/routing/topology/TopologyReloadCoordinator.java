package org.Aayush.routing.topology;

import org.Aayush.routing.future.EphemeralRouteResultStore;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Atomic coordinator for topology-bound runtime snapshot swaps.
 */
public final class TopologyReloadCoordinator {
    private final AtomicReference<TopologyRuntimeSnapshot> activeSnapshot;
    private final ReloadCompatibilityPolicy compatibilityPolicy;
    private final List<TopologyBoundResultStore> resultStores;
    private final TopologyReloadObserver reloadObserver;

    public TopologyReloadCoordinator(
            TopologyRuntimeSnapshot initialSnapshot,
            ReloadCompatibilityPolicy compatibilityPolicy,
            EphemeralRouteResultStore resultStore
    ) {
        this(initialSnapshot, compatibilityPolicy, List.of(resultStore), TopologyReloadObserver.noop());
    }

    public TopologyReloadCoordinator(
            TopologyRuntimeSnapshot initialSnapshot,
            ReloadCompatibilityPolicy compatibilityPolicy,
            List<? extends TopologyBoundResultStore> resultStores
    ) {
        this(initialSnapshot, compatibilityPolicy, resultStores, TopologyReloadObserver.noop());
    }

    /**
     * Stage F3 wires optional reload observation into the canonical atomic snapshot coordinator.
     * Satisfies closure criterion: topology-evolution health is observable without changing reload behavior.
     */
    public TopologyReloadCoordinator(
            TopologyRuntimeSnapshot initialSnapshot,
            ReloadCompatibilityPolicy compatibilityPolicy,
            List<? extends TopologyBoundResultStore> resultStores,
            TopologyReloadObserver reloadObserver
    ) {
        this.activeSnapshot = new AtomicReference<>(validateSnapshot(initialSnapshot));
        this.compatibilityPolicy = Objects.requireNonNull(compatibilityPolicy, "compatibilityPolicy");
        this.resultStores = List.copyOf(Objects.requireNonNull(resultStores, "resultStores"));
        this.reloadObserver = Objects.requireNonNull(reloadObserver, "reloadObserver");
    }

    /**
     * Stage D4/F3 returns the currently active topology snapshot for serving and observability.
     * Satisfies closure criterion: reload state remains explicit for future-aware serving and operational health.
     */
    public TopologyRuntimeSnapshot currentSnapshot() {
        return activeSnapshot.get();
    }

    /**
     * Stage D4/F3 validates a candidate reload while surfacing the outcome to operational observability.
     * Satisfies closure criterion: topology-evolution health is detectable without mutating the active snapshot.
     */
    public TopologyVersion validateReload(TopologyRuntimeSnapshot candidateSnapshot) {
        try {
            TopologyVersion candidateVersion = validateSnapshot(candidateSnapshot).getTopologyVersion();
            safeObserve(observer -> observer.onReloadValidationSuccess(candidateVersion));
            return candidateVersion;
        } catch (RuntimeException error) {
            safeObserve(observer -> observer.onReloadValidationFailure(error.getMessage()));
            throw error;
        }
    }

    /**
     * Stage D4/F3 atomically applies a validated candidate snapshot and emits side-band observability events.
     * Satisfies closure criterion: topology-evolution outcomes remain explicit without altering atomic swap semantics.
     */
    public TopologyVersion applyReload(TopologyRuntimeSnapshot candidateSnapshot) {
        try {
            TopologyRuntimeSnapshot validatedCandidate = validateSnapshot(candidateSnapshot);
            safeObserve(observer -> observer.onReloadValidationSuccess(validatedCandidate.getTopologyVersion()));
            TopologyRuntimeSnapshot previous = activeSnapshot.getAndSet(validatedCandidate);
            compatibilityPolicy.onReload(previous.getTopologyVersion(), validatedCandidate.getTopologyVersion(), resultStores);
            safeObserve(observer -> observer.onReloadApplied(previous.getTopologyVersion(), validatedCandidate.getTopologyVersion()));
            return validatedCandidate.getTopologyVersion();
        } catch (RuntimeException error) {
            safeObserve(observer -> observer.onReloadValidationFailure(error.getMessage()));
            throw error;
        }
    }

    private static TopologyRuntimeSnapshot validateSnapshot(TopologyRuntimeSnapshot snapshot) {
        TopologyRuntimeSnapshot nonNullSnapshot = Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(nonNullSnapshot.getRouteCore(), "snapshot.routeCore");
        Objects.requireNonNull(nonNullSnapshot.getTopologyVersion(), "snapshot.topologyVersion");
        Objects.requireNonNull(nonNullSnapshot.getFailureQuarantine(), "snapshot.failureQuarantine");
        return nonNullSnapshot;
    }

    private void safeObserve(Consumer<TopologyReloadObserver> callback) {
        try {
            callback.accept(reloadObserver);
        } catch (RuntimeException ignored) {
            // Observability must not change reload correctness.
        }
    }
}
