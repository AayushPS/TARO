package org.Aayush.routing.topology;

/**
 * Stage F3 observer for reload validation and apply events.
 * Satisfies closure criterion: the system exposes enough operational signal to detect topology-evolution health without changing reload behavior.
 */
public interface TopologyReloadObserver {
    /**
     * Stage F3 records one successful reload validation outcome.
     * Satisfies closure criterion: reload health is visible to operational metrics and alerts.
     */
    default void onReloadValidationSuccess(TopologyVersion candidateVersion) {
    }

    /**
     * Stage F3 records one failed reload validation outcome.
     * Satisfies closure criterion: degraded reload posture is explicit for operational governance.
     */
    default void onReloadValidationFailure(String failureReason) {
    }

    /**
     * Stage F3 records one successful atomic reload application.
     * Satisfies closure criterion: topology-evolution outcomes are observable at the system level.
     */
    default void onReloadApplied(TopologyVersion previousVersion, TopologyVersion candidateVersion) {
    }

    /**
     * Stage F3 returns a no-op observer when no operational listener is configured.
     * Satisfies closure criterion: observability remains optional and does not change reload semantics.
     */
    static TopologyReloadObserver noop() {
        return new TopologyReloadObserver() {
        };
    }
}
