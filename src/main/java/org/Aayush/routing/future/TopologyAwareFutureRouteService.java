package org.Aayush.routing.future;

import org.Aayush.routing.topology.TopologyReloadCoordinator;

import java.util.Objects;
import java.util.Optional;

/**
 * v13 facade that binds future-aware evaluation to the currently active topology snapshot.
 */
public final class TopologyAwareFutureRouteService {
    private final TopologyReloadCoordinator reloadCoordinator;
    private final FutureRouteService futureRouteService;

    public TopologyAwareFutureRouteService(
            TopologyReloadCoordinator reloadCoordinator,
            FutureRouteService futureRouteService
    ) {
        this.reloadCoordinator = Objects.requireNonNull(reloadCoordinator, "reloadCoordinator");
        this.futureRouteService = Objects.requireNonNull(futureRouteService, "futureRouteService");
    }

    public FutureRouteResultSet evaluate(FutureRouteRequest request) {
        return futureRouteService.evaluate(reloadCoordinator.currentSnapshot(), request);
    }

    public Optional<FutureRouteResultSet> getResultSet(String resultSetId) {
        return futureRouteService.getResultSet(resultSetId);
    }

    /**
     * Stage C5 — forwards stable retained route summary lookup through the topology-aware service seam.
     * Satisfies closure criterion: retained results remain topology-aware and safe for API retrieval work.
     */
    public Optional<RetainedRouteResultView.Summary> getResultSummary(String resultSetId) {
        return futureRouteService.getResultSummary(resultSetId);
    }

    /**
     * Stage C5 — forwards stable retained route detail lookup through the topology-aware service seam.
     * Satisfies closure criterion: retained results remain topology-aware and safe for API retrieval work.
     */
    public Optional<RetainedRouteResultView.Detail> getResultDetail(String resultSetId) {
        return futureRouteService.getResultDetail(resultSetId);
    }
}
