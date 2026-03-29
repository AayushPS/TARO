package org.Aayush.api;

import java.time.Instant;

/**
 * Stage F3 metrics response for operational observability over serving, retention, and reload behavior.
 * Satisfies closure criterion: the system exposes enough metrics and health signal to detect failures in future-aware serving and topology evolution.
 */
public record OperationalMetricsResponse(
        String status,
        Instant observedAt,
        String activeTopologyVersion,
        int callerScopedResultCount,
        OperationMetrics routeEvaluations,
        OperationMetrics matrixEvaluations,
        OperationMetrics routeLookups,
        OperationMetrics matrixLookups,
        OperationMetrics routeFeedback,
        OperationMetrics matrixFeedback,
        OperationMetrics retainedResultPurge,
        RetainedStoreMetrics routeStore,
        RetainedStoreMetrics matrixStore,
        ReloadMetrics reload,
        AlertSummary alerts
) {
    /**
     * Stage F3 low-cardinality per-operation metrics snapshot.
     * Satisfies closure criterion: future-aware serving metrics remain actionable without per-request cardinality explosion.
     */
    public record OperationMetrics(
            long requestCount,
            long errorCount,
            double averageLatencyMillis,
            double maxLatencyMillis,
            Instant lastObservedAt
    ) {
    }

    /**
     * Stage F3 retained-result pressure snapshot for one store.
     * Satisfies closure criterion: retained-result memory pressure is visible for operator dashboards and alerts.
     */
    public record RetainedStoreMetrics(
            int entryCount,
            long totalBytes,
            long maxEntries,
            long maxTotalBytes,
            double entryUsageRatio,
            double byteUsageRatio
    ) {
    }

    /**
     * Stage F3 reload-health snapshot over validation and atomic apply events.
     * Satisfies closure criterion: topology-evolution health is explicit at the system level.
     */
    public record ReloadMetrics(
            long validationSuccessCount,
            long validationFailureCount,
            long appliedReloadCount,
            Instant lastSuccessfulReloadAt,
            String lastSuccessfulTopologyVersion,
            String lastFailureReason
    ) {
    }

    /**
     * Stage F3 fixed alert posture summary for dashboards and rollback policy evaluation.
     * Satisfies closure criterion: alerting posture for reload, latency, pressure, and parity regressions is explicit.
     */
    public record AlertSummary(
            String overallStatus,
            String reloadStatus,
            String retainedResultPressureStatus,
            String routeLatencyStatus,
            String matrixLatencyStatus,
            String parityStatus
    ) {
    }
}
