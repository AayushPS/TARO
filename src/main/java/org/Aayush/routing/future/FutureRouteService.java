package org.Aayush.routing.future;

import org.Aayush.routing.core.FutureRouteEvaluator;
import org.Aayush.routing.topology.TopologyRuntimeSnapshot;

import java.util.Objects;
import java.util.Optional;

/**
 * v12 service facade for future-aware route evaluation and retained-result lookup.
 */
public final class FutureRouteService {
    private final FutureRouteEvaluator evaluator;
    private final EphemeralRouteResultStore resultStore;

    public FutureRouteService(
            FutureRouteEvaluator evaluator,
            EphemeralRouteResultStore resultStore
    ) {
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
        this.resultStore = Objects.requireNonNull(resultStore, "resultStore");
    }

    public FutureRouteResultSet evaluate(TopologyRuntimeSnapshot snapshot, FutureRouteRequest request) {
        FutureRouteResultSet resultSet = evaluator.evaluate(snapshot, request);
        return resultStore.put(resultSet);
    }

    public Optional<FutureRouteResultSet> getResultSet(String resultSetId) {
        return resultStore.get(resultSetId);
    }

    public void purgeExpired() {
        resultStore.purgeExpired();
    }
}
