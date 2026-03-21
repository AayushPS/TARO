package org.Aayush.routing.future;

import org.Aayush.routing.topology.TopologyBoundResultStore;
import org.Aayush.routing.topology.TopologyVersion;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Temporary store for future-aware result sets.
 */
public interface EphemeralRouteResultStore extends TopologyBoundResultStore {
    FutureRouteResultSet put(FutureRouteResultSet resultSet);

    Optional<FutureRouteResultSet> get(String resultSetId);

    void invalidate(Predicate<FutureRouteResultSet> predicate);

    default void invalidateAll() {
        invalidate(resultSet -> true);
    }

    @Override
    default void invalidateForTopology(TopologyVersion topologyVersion) {
        TopologyVersion nonNullTopologyVersion = Objects.requireNonNull(topologyVersion, "topologyVersion");
        invalidate(resultSet -> !nonNullTopologyVersion.equals(resultSet.getTopologyVersion()));
    }
}
