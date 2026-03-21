package org.Aayush.routing.future;

import org.Aayush.routing.topology.TopologyReloadCoordinator;

import java.util.Objects;
import java.util.Optional;

/**
 * v13 facade that binds future-aware matrix evaluation to the currently active topology snapshot.
 */
public final class TopologyAwareFutureMatrixService {
    private final TopologyReloadCoordinator reloadCoordinator;
    private final FutureMatrixService futureMatrixService;

    public TopologyAwareFutureMatrixService(
            TopologyReloadCoordinator reloadCoordinator,
            FutureMatrixService futureMatrixService
    ) {
        this.reloadCoordinator = Objects.requireNonNull(reloadCoordinator, "reloadCoordinator");
        this.futureMatrixService = Objects.requireNonNull(futureMatrixService, "futureMatrixService");
    }

    public FutureMatrixResultSet evaluate(FutureMatrixRequest request) {
        return futureMatrixService.evaluate(reloadCoordinator.currentSnapshot(), request);
    }

    public Optional<FutureMatrixResultSet> getResultSet(String resultSetId) {
        return futureMatrixService.getResultSet(resultSetId);
    }
}
