package org.Aayush.api;

import org.Aayush.routing.future.InMemoryEphemeralMatrixResultStore;
import org.Aayush.routing.future.InMemoryEphemeralRouteResultStore;
import org.Aayush.routing.topology.TopologyReloadCoordinator;
import org.Aayush.routing.topology.TopologyReloadObserver;
import org.Aayush.routing.topology.TopologyRuntimeSnapshot;
import org.Aayush.routing.topology.TopologyVersion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Stage F3 aggregation service for operational metrics, alerts, and governance posture.
 * Satisfies closure criteria: the system exposes enough metrics and operational policy to detect and govern failures in future-aware serving and topology evolution.
 */
@Component
public final class OperationalMetricsService implements TopologyReloadObserver {
    private final EnumMap<OperationKey, OperationAccumulator> operations = new EnumMap<>(OperationKey.class);
    private final Supplier<TopologyRuntimeSnapshot> snapshotSupplier;
    private final Supplier<InMemoryEphemeralRouteResultStore> routeStoreSupplier;
    private final Supplier<InMemoryEphemeralMatrixResultStore> matrixStoreSupplier;
    private final Supplier<Integer> registrySizeSupplier;
    private final Clock clock;
    private final Thresholds thresholds;
    private final AtomicLong validationSuccessCount = new AtomicLong();
    private final AtomicLong validationFailureCount = new AtomicLong();
    private final AtomicLong appliedReloadCount = new AtomicLong();
    private final AtomicReference<Instant> lastSuccessfulReloadAt = new AtomicReference<>();
    private final AtomicReference<String> lastSuccessfulTopologyVersion = new AtomicReference<>();
    private final AtomicReference<String> lastFailureReason = new AtomicReference<>();
    private final AtomicReference<String> parityFailureReason = new AtomicReference<>();

    /**
     * Stage F3 wires the canonical API/runtime beans into the operational metrics aggregator.
     * Satisfies closure criterion: serving, retained-result, and reload signals share one canonical aggregation layer.
     */
    @Autowired
    public OperationalMetricsService(
            ObjectProvider<TopologyReloadCoordinator> reloadCoordinatorProvider,
            ObjectProvider<InMemoryEphemeralRouteResultStore> routeStoreProvider,
            ObjectProvider<InMemoryEphemeralMatrixResultStore> matrixStoreProvider,
            CallerScopedRetainedResultRegistry retainedResultRegistry,
            ObjectProvider<Clock> clockProvider
    ) {
        this(
                providerClock(clockProvider),
                () -> {
                    TopologyReloadCoordinator coordinator = reloadCoordinatorProvider.getIfAvailable();
                    return coordinator == null ? null : coordinator.currentSnapshot();
                },
                routeStoreProvider::getIfAvailable,
                matrixStoreProvider::getIfAvailable,
                Objects.requireNonNull(retainedResultRegistry, "retainedResultRegistry")::size,
                Thresholds.defaults()
        );
    }

    OperationalMetricsService(
            Clock clock,
            Supplier<TopologyRuntimeSnapshot> snapshotSupplier,
            Supplier<InMemoryEphemeralRouteResultStore> routeStoreSupplier,
            Supplier<InMemoryEphemeralMatrixResultStore> matrixStoreSupplier,
            Supplier<Integer> registrySizeSupplier,
            Thresholds thresholds
    ) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.snapshotSupplier = Objects.requireNonNull(snapshotSupplier, "snapshotSupplier");
        this.routeStoreSupplier = Objects.requireNonNull(routeStoreSupplier, "routeStoreSupplier");
        this.matrixStoreSupplier = Objects.requireNonNull(matrixStoreSupplier, "matrixStoreSupplier");
        this.registrySizeSupplier = Objects.requireNonNull(registrySizeSupplier, "registrySizeSupplier");
        this.thresholds = Objects.requireNonNull(thresholds, "thresholds");
        for (OperationKey key : OperationKey.values()) {
            operations.put(key, new OperationAccumulator());
        }
    }

    /**
     * Stage F3 returns the canonical low-cardinality operational metrics snapshot.
     * Satisfies closure criterion: the system exposes enough metrics and health signal to detect failures in future-aware serving and topology evolution.
     */
    public OperationalMetricsResponse metrics() {
        Instant now = clock.instant();
        TopologyRuntimeSnapshot snapshot = snapshotSupplier.get();
        OperationalMetricsResponse.RetainedStoreMetrics routeStoreMetrics = routeStoreMetrics();
        OperationalMetricsResponse.RetainedStoreMetrics matrixStoreMetrics = matrixStoreMetrics();
        OperationalMetricsResponse.AlertSummary alertSummary = alertSummary(routeStoreMetrics, matrixStoreMetrics);
        return new OperationalMetricsResponse(
                alertSummary.overallStatus(),
                now,
                snapshot == null ? null : snapshot.getTopologyVersion().getTopologyVersion(),
                registrySizeSupplier.get(),
                operations.get(OperationKey.ROUTE_EVALUATION).snapshot(),
                operations.get(OperationKey.MATRIX_EVALUATION).snapshot(),
                operations.get(OperationKey.ROUTE_LOOKUP).snapshot(),
                operations.get(OperationKey.MATRIX_LOOKUP).snapshot(),
                operations.get(OperationKey.ROUTE_FEEDBACK).snapshot(),
                operations.get(OperationKey.MATRIX_FEEDBACK).snapshot(),
                operations.get(OperationKey.RETAINED_PURGE).snapshot(),
                routeStoreMetrics,
                matrixStoreMetrics,
                new OperationalMetricsResponse.ReloadMetrics(
                        validationSuccessCount.get(),
                        validationFailureCount.get(),
                        appliedReloadCount.get(),
                        lastSuccessfulReloadAt.get(),
                        lastSuccessfulTopologyVersion.get(),
                        lastFailureReason.get()
                ),
                alertSummary
        );
    }

    /**
     * Stage F3 returns the canonical builder-side and serving-side governance posture.
     * Satisfies closure criterion: rollout posture is explicit for both builder-time and serving-time changes.
     */
    public OperationalGovernanceResponse governance() {
        TopologyRuntimeSnapshot snapshot = snapshotSupplier.get();
        OperationalMetricsResponse.AlertSummary alerts = alertSummary(routeStoreMetrics(), matrixStoreMetrics());
        return new OperationalGovernanceResponse(
                clock.instant(),
                snapshot == null ? null : snapshot.getTopologyVersion().getTopologyVersion(),
                alerts.overallStatus(),
                new OperationalGovernanceResponse.BuilderGovernance(
                        "VALIDATE_ONLY_THEN_ATOMIC_RELOAD",
                        List.of("reload_degraded", "parity_drift", "contract_test_failure"),
                        "keep_previous_topology_snapshot_active_and_block_candidate_publication"
                ),
                new OperationalGovernanceResponse.ServingGovernance(
                        "CANARY_THEN_FULL_ROLLOUT",
                        List.of(
                                "route_latency_regression",
                                "matrix_latency_regression",
                                "retained_result_memory_pressure",
                                "parity_drift",
                                "contract_test_failure"
                        ),
                        "rollback_serving_artifact_and_keep_topology_bound_result_compatibility_checks_enabled"
                )
        );
    }

    /**
     * Stage F3 records one successful reload validation event from the canonical reload seam.
     * Satisfies closure criterion: reload-health posture is explicit for operational monitoring.
     */
    @Override
    public void onReloadValidationSuccess(TopologyVersion candidateVersion) {
        validationSuccessCount.incrementAndGet();
        lastFailureReason.set(null);
    }

    /**
     * Stage F3 records one failed reload validation event from the canonical reload seam.
     * Satisfies closure criterion: degraded reload posture is explicit for operational governance.
     */
    @Override
    public void onReloadValidationFailure(String failureReason) {
        validationFailureCount.incrementAndGet();
        lastFailureReason.set(failureReason == null || failureReason.isBlank() ? "reload_validation_failure" : failureReason);
    }

    /**
     * Stage F3 records one successful atomic reload application from the canonical reload seam.
     * Satisfies closure criterion: topology-evolution outcomes are visible to dashboards and rollback policy.
     */
    @Override
    public void onReloadApplied(TopologyVersion previousVersion, TopologyVersion candidateVersion) {
        appliedReloadCount.incrementAndGet();
        lastSuccessfulReloadAt.set(clock.instant());
        lastFailureReason.set(null);
        if (candidateVersion != null) {
            lastSuccessfulTopologyVersion.set(candidateVersion.getTopologyVersion());
        }
    }

    void recordRouteEvaluation(long latencyNanos, boolean success) {
        operations.get(OperationKey.ROUTE_EVALUATION).record(latencyNanos, success, clock.instant());
    }

    void recordMatrixEvaluation(long latencyNanos, boolean success) {
        operations.get(OperationKey.MATRIX_EVALUATION).record(latencyNanos, success, clock.instant());
    }

    void recordRouteLookup(long latencyNanos, boolean success) {
        operations.get(OperationKey.ROUTE_LOOKUP).record(latencyNanos, success, clock.instant());
    }

    void recordMatrixLookup(long latencyNanos, boolean success) {
        operations.get(OperationKey.MATRIX_LOOKUP).record(latencyNanos, success, clock.instant());
    }

    void recordRouteFeedback(long latencyNanos, boolean success) {
        operations.get(OperationKey.ROUTE_FEEDBACK).record(latencyNanos, success, clock.instant());
    }

    void recordMatrixFeedback(long latencyNanos, boolean success) {
        operations.get(OperationKey.MATRIX_FEEDBACK).record(latencyNanos, success, clock.instant());
    }

    void recordRetainedResultPurge(long latencyNanos, boolean success) {
        operations.get(OperationKey.RETAINED_PURGE).record(latencyNanos, success, clock.instant());
    }

    void markParityContractFailure(String contractName, String detail) {
        String normalizedContract = contractName == null || contractName.isBlank() ? "contract_test_failure" : contractName;
        String normalizedDetail = detail == null || detail.isBlank() ? normalizedContract : normalizedContract + ": " + detail;
        parityFailureReason.set(normalizedDetail);
    }

    void clearParityContractFailure() {
        parityFailureReason.set(null);
    }

    public void clear() {
        operations.values().forEach(OperationAccumulator::clear);
        validationSuccessCount.set(0L);
        validationFailureCount.set(0L);
        appliedReloadCount.set(0L);
        lastSuccessfulReloadAt.set(null);
        lastSuccessfulTopologyVersion.set(null);
        lastFailureReason.set(null);
        parityFailureReason.set(null);
    }

    OperationalMetricsResponse.AlertSummary alertSummary() {
        return alertSummary(routeStoreMetrics(), matrixStoreMetrics());
    }

    private OperationalMetricsResponse.RetainedStoreMetrics routeStoreMetrics() {
        InMemoryEphemeralRouteResultStore routeStore = routeStoreSupplier.get();
        if (routeStore == null) {
            return new OperationalMetricsResponse.RetainedStoreMetrics(0, 0L, 0L, 0L, 0.0d, 0.0d);
        }
        InMemoryEphemeralRouteResultStore.Stats stats = routeStore.stats();
        return new OperationalMetricsResponse.RetainedStoreMetrics(
                stats.entryCount(),
                stats.totalBytes(),
                stats.maxEntries(),
                stats.maxTotalBytes(),
                stats.entryUsageRatio(),
                stats.byteUsageRatio()
        );
    }

    private OperationalMetricsResponse.RetainedStoreMetrics matrixStoreMetrics() {
        InMemoryEphemeralMatrixResultStore matrixStore = matrixStoreSupplier.get();
        if (matrixStore == null) {
            return new OperationalMetricsResponse.RetainedStoreMetrics(0, 0L, 0L, 0L, 0.0d, 0.0d);
        }
        InMemoryEphemeralMatrixResultStore.Stats stats = matrixStore.stats();
        return new OperationalMetricsResponse.RetainedStoreMetrics(
                stats.entryCount(),
                stats.totalBytes(),
                stats.maxEntries(),
                stats.maxTotalBytes(),
                stats.entryUsageRatio(),
                stats.byteUsageRatio()
        );
    }

    private OperationalMetricsResponse.AlertSummary alertSummary(
            OperationalMetricsResponse.RetainedStoreMetrics routeStoreMetrics,
            OperationalMetricsResponse.RetainedStoreMetrics matrixStoreMetrics
    ) {
        AlertState reloadState = lastFailureReason.get() == null ? AlertState.HEALTHY : AlertState.DEGRADED;
        AlertState pressureState = pressureState(routeStoreMetrics, matrixStoreMetrics);
        AlertState routeLatencyState = latencyState(operations.get(OperationKey.ROUTE_EVALUATION), thresholds.routeWarnMillis(), thresholds.routeDegradedMillis());
        AlertState matrixLatencyState = latencyState(operations.get(OperationKey.MATRIX_EVALUATION), thresholds.matrixWarnMillis(), thresholds.matrixDegradedMillis());
        AlertState parityState = parityFailureReason.get() == null ? AlertState.HEALTHY : AlertState.DEGRADED;
        AlertState overallState = AlertState.max(reloadState, pressureState, routeLatencyState, matrixLatencyState, parityState);
        return new OperationalMetricsResponse.AlertSummary(
                overallState.name(),
                reloadState.name(),
                pressureState.name(),
                routeLatencyState.name(),
                matrixLatencyState.name(),
                parityState.name()
        );
    }

    private AlertState pressureState(
            OperationalMetricsResponse.RetainedStoreMetrics routeStoreMetrics,
            OperationalMetricsResponse.RetainedStoreMetrics matrixStoreMetrics
    ) {
        double maxRatio = Math.max(
                Math.max(routeStoreMetrics.entryUsageRatio(), routeStoreMetrics.byteUsageRatio()),
                Math.max(matrixStoreMetrics.entryUsageRatio(), matrixStoreMetrics.byteUsageRatio())
        );
        if (maxRatio >= thresholds.pressureDegradedRatio()) {
            return AlertState.DEGRADED;
        }
        if (maxRatio >= thresholds.pressureWarnRatio()) {
            return AlertState.WARN;
        }
        return AlertState.HEALTHY;
    }

    private AlertState latencyState(OperationAccumulator accumulator, double warnMillis, double degradedMillis) {
        double effectiveLatencyMillis = Math.max(accumulator.averageLatencyMillis(), accumulator.maxLatencyMillis());
        if (effectiveLatencyMillis >= degradedMillis) {
            return AlertState.DEGRADED;
        }
        if (effectiveLatencyMillis >= warnMillis) {
            return AlertState.WARN;
        }
        return AlertState.HEALTHY;
    }

    private static Clock providerClock(ObjectProvider<Clock> clockProvider) {
        Clock providedClock = clockProvider.getIfAvailable();
        return providedClock == null ? Clock.systemUTC() : providedClock;
    }

    enum AlertState {
        HEALTHY,
        WARN,
        DEGRADED;

        static AlertState max(AlertState... states) {
            AlertState max = HEALTHY;
            for (AlertState state : states) {
                if (state.ordinal() > max.ordinal()) {
                    max = state;
                }
            }
            return max;
        }
    }

    enum OperationKey {
        ROUTE_EVALUATION,
        MATRIX_EVALUATION,
        ROUTE_LOOKUP,
        MATRIX_LOOKUP,
        ROUTE_FEEDBACK,
        MATRIX_FEEDBACK,
        RETAINED_PURGE
    }

    static final class OperationAccumulator {
        private final AtomicLong requestCount = new AtomicLong();
        private final AtomicLong errorCount = new AtomicLong();
        private final AtomicLong totalLatencyNanos = new AtomicLong();
        private final AtomicLong maxLatencyNanos = new AtomicLong();
        private final AtomicReference<Instant> lastObservedAt = new AtomicReference<>();

        void record(long latencyNanos, boolean success, Instant observedAt) {
            long boundedLatency = Math.max(0L, latencyNanos);
            requestCount.incrementAndGet();
            if (!success) {
                errorCount.incrementAndGet();
            }
            totalLatencyNanos.addAndGet(boundedLatency);
            maxLatencyNanos.accumulateAndGet(boundedLatency, Math::max);
            lastObservedAt.set(observedAt);
        }

        double averageLatencyMillis() {
            long count = requestCount.get();
            if (count == 0L) {
                return 0.0d;
            }
            return nanosToMillis(totalLatencyNanos.get() / (double) count);
        }

        double maxLatencyMillis() {
            return nanosToMillis(maxLatencyNanos.get());
        }

        OperationalMetricsResponse.OperationMetrics snapshot() {
            return new OperationalMetricsResponse.OperationMetrics(
                    requestCount.get(),
                    errorCount.get(),
                    averageLatencyMillis(),
                    maxLatencyMillis(),
                    lastObservedAt.get()
            );
        }

        private static double nanosToMillis(double nanos) {
            return nanos / 1_000_000.0d;
        }

        void clear() {
            requestCount.set(0L);
            errorCount.set(0L);
            totalLatencyNanos.set(0L);
            maxLatencyNanos.set(0L);
            lastObservedAt.set(null);
        }
    }

    record Thresholds(
            double routeWarnMillis,
            double routeDegradedMillis,
            double matrixWarnMillis,
            double matrixDegradedMillis,
            double pressureWarnRatio,
            double pressureDegradedRatio
    ) {
        Thresholds {
            if (routeWarnMillis < 0.0d || routeDegradedMillis < routeWarnMillis) {
                throw new IllegalArgumentException("route latency thresholds must be non-negative and ordered");
            }
            if (matrixWarnMillis < 0.0d || matrixDegradedMillis < matrixWarnMillis) {
                throw new IllegalArgumentException("matrix latency thresholds must be non-negative and ordered");
            }
            if (pressureWarnRatio < 0.0d || pressureDegradedRatio < pressureWarnRatio) {
                throw new IllegalArgumentException("pressure thresholds must be non-negative and ordered");
            }
        }

        static Thresholds defaults() {
            return new Thresholds(
                    25.0d,
                    100.0d,
                    50.0d,
                    200.0d,
                    0.75d,
                    0.90d
            );
        }
    }
}
