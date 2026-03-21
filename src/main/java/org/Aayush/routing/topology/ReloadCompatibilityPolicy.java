package org.Aayush.routing.topology;

import java.util.Objects;
import java.util.List;

/**
 * Policy hook for retained future-aware results across topology reloads.
 */
@FunctionalInterface
public interface ReloadCompatibilityPolicy {
    /**
     * Applies one compatibility policy after the active topology snapshot swaps.
     */
    void onReload(
            TopologyVersion previous,
            TopologyVersion next,
            List<? extends TopologyBoundResultStore> resultStores
    );

    /**
     * Leaves retained results readable until their own TTL expiry.
     */
    static ReloadCompatibilityPolicy retainUntilExpiry() {
        return (previous, next, resultStores) -> {
            for (TopologyBoundResultStore resultStore : Objects.requireNonNull(resultStores, "resultStores")) {
                Objects.requireNonNull(resultStore, "resultStore").purgeExpired();
            }
        };
    }

    /**
     * Invalidates retained results that do not match the newly active topology.
     */
    static ReloadCompatibilityPolicy invalidateStaleTopologyResults() {
        return (previous, next, resultStores) -> {
            Objects.requireNonNull(next, "next");
            for (TopologyBoundResultStore resultStore : Objects.requireNonNull(resultStores, "resultStores")) {
                TopologyBoundResultStore nonNullStore = Objects.requireNonNull(resultStore, "resultStore");
                nonNullStore.purgeExpired();
                nonNullStore.invalidateForTopology(next);
            }
        };
    }
}
