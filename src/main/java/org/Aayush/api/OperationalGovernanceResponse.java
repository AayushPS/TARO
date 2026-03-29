package org.Aayush.api;

import java.time.Instant;
import java.util.List;

/**
 * Stage F3 governance response for builder-side and serving-side rollout and rollback posture.
 * Satisfies closure criterion: rollout posture is explicit for both builder-time and serving-time changes.
 */
public record OperationalGovernanceResponse(
        Instant observedAt,
        String activeTopologyVersion,
        String currentOverallAlertStatus,
        BuilderGovernance builderGovernance,
        ServingGovernance servingGovernance
) {
    /**
     * Stage F3 builder-side rollout and rollback posture.
     * Satisfies closure criterion: builder-time topology publication policy is explicit and machine-readable.
     */
    public record BuilderGovernance(
            String rolloutSequence,
            List<String> rollbackTriggers,
            String rollbackAction
    ) {
    }

    /**
     * Stage F3 serving-side rollout and rollback posture.
     * Satisfies closure criterion: serving-time API and routing changes have explicit governance semantics.
     */
    public record ServingGovernance(
            String rolloutSequence,
            List<String> rollbackTriggers,
            String rollbackAction
    ) {
    }
}
