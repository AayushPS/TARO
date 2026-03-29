package org.Aayush.api;

import org.Aayush.api.CallerScopedRetainedResultRegistry.ResultKind;

import java.util.List;

/**
 * Request contract for creating one caller-scoped retraining job from joined telemetry exports.
 */
public record RetrainingJobCreateRequest(
        String trainingWindowLabel,
        List<String> selectedTraits,
        ResultKind resultKind,
        String topologyVersionId,
        String scenarioBundleId,
        String traitHash,
        boolean completeOnly
) {
}
