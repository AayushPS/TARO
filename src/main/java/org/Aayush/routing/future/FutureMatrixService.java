package org.Aayush.routing.future;

import org.Aayush.routing.core.FutureMatrixEvaluator;
import org.Aayush.routing.topology.TopologyRuntimeSnapshot;

import java.util.Objects;
import java.util.Optional;

/**
 * v12 service facade for future-aware matrix evaluation and retained-result lookup.
 */
public final class FutureMatrixService {
    private final FutureMatrixEvaluator evaluator;
    private final EphemeralMatrixResultStore resultStore;

    public FutureMatrixService(
            FutureMatrixEvaluator evaluator,
            EphemeralMatrixResultStore resultStore
    ) {
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
        this.resultStore = Objects.requireNonNull(resultStore, "resultStore");
    }

    public FutureMatrixResultSet evaluate(TopologyRuntimeSnapshot snapshot, FutureMatrixRequest request) {
        FutureMatrixResultSet resultSet = evaluator.evaluate(snapshot, request);
        return resultStore.put(resultSet);
    }

    public Optional<FutureMatrixResultSet> getResultSet(String resultSetId) {
        return resultStore.get(resultSetId);
    }

    public void purgeExpired() {
        resultStore.purgeExpired();
    }
}
