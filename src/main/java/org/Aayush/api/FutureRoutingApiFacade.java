package org.Aayush.api;

import org.Aayush.routing.future.FutureMatrixResultSet;
import org.Aayush.routing.future.FutureMatrixService;
import org.Aayush.routing.future.FutureRouteResultSet;
import org.Aayush.routing.future.FutureRouteService;
import org.Aayush.routing.future.RetainedMatrixResultView;
import org.Aayush.routing.future.RetainedRouteResultView;
import org.Aayush.routing.future.TopologyAwareFutureMatrixService;
import org.Aayush.routing.future.TopologyAwareFutureRouteService;
import org.Aayush.routing.topology.TopologyReloadCoordinator;
import org.Aayush.routing.topology.TopologyRuntimeSnapshot;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Stage F1 facade that keeps the HTTP layer thin and delegates into canonical future-routing services.
 * Satisfies closure criterion: frontend retrieval flow can inspect retained future-aware results without recomputing them.
 */
@Component
public final class FutureRoutingApiFacade {
    public static final String CALLER_HEADER = "X-Taro-Caller-Id";

    private final ObjectProvider<TopologyAwareFutureRouteService> topologyAwareRouteServiceProvider;
    private final ObjectProvider<TopologyAwareFutureMatrixService> topologyAwareMatrixServiceProvider;
    private final ObjectProvider<FutureRouteService> routeServiceProvider;
    private final ObjectProvider<FutureMatrixService> matrixServiceProvider;
    private final ObjectProvider<TopologyReloadCoordinator> reloadCoordinatorProvider;
    private final ApiRequestMapper requestMapper;
    private final CallerScopedRetainedResultRegistry retainedResultRegistry;
    private final PredictionTelemetryStore predictionTelemetryStore;
    private final OperationalMetricsService operationalMetricsService;
    private final Clock clock;

    public FutureRoutingApiFacade(
            ObjectProvider<TopologyAwareFutureRouteService> topologyAwareRouteServiceProvider,
            ObjectProvider<TopologyAwareFutureMatrixService> topologyAwareMatrixServiceProvider,
            ObjectProvider<FutureRouteService> routeServiceProvider,
            ObjectProvider<FutureMatrixService> matrixServiceProvider,
            ObjectProvider<TopologyReloadCoordinator> reloadCoordinatorProvider,
            ApiRequestMapper requestMapper,
            CallerScopedRetainedResultRegistry retainedResultRegistry,
            PredictionTelemetryStore predictionTelemetryStore,
            OperationalMetricsService operationalMetricsService,
            ObjectProvider<Clock> clockProvider
    ) {
        this.topologyAwareRouteServiceProvider = Objects.requireNonNull(topologyAwareRouteServiceProvider, "topologyAwareRouteServiceProvider");
        this.topologyAwareMatrixServiceProvider = Objects.requireNonNull(topologyAwareMatrixServiceProvider, "topologyAwareMatrixServiceProvider");
        this.routeServiceProvider = Objects.requireNonNull(routeServiceProvider, "routeServiceProvider");
        this.matrixServiceProvider = Objects.requireNonNull(matrixServiceProvider, "matrixServiceProvider");
        this.reloadCoordinatorProvider = Objects.requireNonNull(reloadCoordinatorProvider, "reloadCoordinatorProvider");
        this.requestMapper = Objects.requireNonNull(requestMapper, "requestMapper");
        this.retainedResultRegistry = Objects.requireNonNull(retainedResultRegistry, "retainedResultRegistry");
        this.predictionTelemetryStore = Objects.requireNonNull(predictionTelemetryStore, "predictionTelemetryStore");
        this.operationalMetricsService = Objects.requireNonNull(operationalMetricsService, "operationalMetricsService");
        Clock providedClock = clockProvider.getIfAvailable();
        this.clock = providedClock == null ? Clock.systemUTC() : providedClock;
    }

    /**
     * Stage F1 evaluates one future-aware route request through the HTTP layer.
     * Satisfies closure criterion: route results can be inspected later without recomputation.
     */
    public RouteApiResponse evaluateRoute(String callerId, RouteApiRequest apiRequest) {
        return observe(operationalMetricsService::recordRouteEvaluation, () -> {
            TopologyRuntimeSnapshot snapshot = requireActiveSnapshot("route API is not configured with an active topology snapshot");
            FutureRouteService routeService = requireDirectRouteService();
            FutureRouteResultSet resultSet = routeService.evaluate(snapshot, requestMapper.toFutureRouteRequest(apiRequest));
            Optional<RetainedRouteResultView.Summary> retainedSummary = routeService.getResultSummary(resultSet.getResultSetId());
            retainedSummary.ifPresent(summary -> retainedResultRegistry.register(
                    CallerScopedRetainedResultRegistry.ResultKind.ROUTE,
                    resultSet.getResultSetId(),
                    callerId,
                    resultSet.getTopologyVersion(),
                    resultSet.getExpiresAt()
            ));
            retainedSummary.ifPresent(summary -> predictionTelemetryStore.recordRoutePrediction(callerId, snapshot, resultSet));
            return new RouteApiResponse(
                    retainedSummary.isPresent() ? resultSet.getResultSetId() : null,
                    retainedSummary.isPresent(),
                    resultSet.getExpiresAt(),
                    resultSet.getTopologyVersion(),
                    retainedSummary.orElse(null)
            );
        });
    }

    /**
     * Stage F1 evaluates one future-aware matrix request through the HTTP layer.
     * Satisfies closure criterion: matrix results can be inspected later without recomputation.
     */
    public MatrixApiResponse evaluateMatrix(String callerId, MatrixApiRequest apiRequest) {
        return observe(operationalMetricsService::recordMatrixEvaluation, () -> {
            TopologyRuntimeSnapshot snapshot = requireActiveSnapshot("matrix API is not configured with an active topology snapshot");
            FutureMatrixService matrixService = requireDirectMatrixService();
            FutureMatrixResultSet resultSet = matrixService.evaluate(snapshot, requestMapper.toFutureMatrixRequest(apiRequest));
            Optional<RetainedMatrixResultView.Summary> retainedSummary = matrixService.getResultSummary(resultSet.getResultSetId());
            retainedSummary.ifPresent(summary -> retainedResultRegistry.register(
                    CallerScopedRetainedResultRegistry.ResultKind.MATRIX,
                    resultSet.getResultSetId(),
                    callerId,
                    resultSet.getTopologyVersion(),
                    resultSet.getExpiresAt()
            ));
            retainedSummary.ifPresent(summary -> predictionTelemetryStore.recordMatrixPrediction(callerId, snapshot, resultSet));
            return new MatrixApiResponse(
                    retainedSummary.isPresent() ? resultSet.getResultSetId() : null,
                    retainedSummary.isPresent(),
                    resultSet.getExpiresAt(),
                    resultSet.getTopologyVersion(),
                    retainedSummary.orElse(null)
            );
        });
    }

    /**
     * Stage F1 retrieves a retained route summary without recomputation.
     * Satisfies closure criterion: frontend retrieval flow can inspect retained future-aware route results without recomputing them.
     */
    public RetainedRouteResultView.Summary routeSummary(String callerId, String resultSetId) {
        return observe(operationalMetricsService::recordRouteLookup, () -> {
            TopologyAwareFutureRouteService routeService = requireRouteService();
            return resolveRetainedResult(
                    CallerScopedRetainedResultRegistry.ResultKind.ROUTE,
                    callerId,
                    resultSetId,
                    () -> routeService.getResultSummary(resultSetId)
            );
        });
    }

    /**
     * Stage F1 retrieves retained route detail without recomputation.
     * Satisfies closure criterion: frontend retrieval flow can inspect retained future-aware route results without recomputing them.
     */
    public RetainedRouteResultView.Detail routeDetail(String callerId, String resultSetId) {
        return observe(operationalMetricsService::recordRouteLookup, () -> {
            TopologyAwareFutureRouteService routeService = requireRouteService();
            return resolveRetainedResult(
                    CallerScopedRetainedResultRegistry.ResultKind.ROUTE,
                    callerId,
                    resultSetId,
                    () -> routeService.getResultDetail(resultSetId)
            );
        });
    }

    /**
     * Stage F1 retrieves a retained matrix summary without recomputation.
     * Satisfies closure criterion: frontend retrieval flow can inspect retained future-aware matrix results without recomputing them.
     */
    public RetainedMatrixResultView.Summary matrixSummary(String callerId, String resultSetId) {
        return observe(operationalMetricsService::recordMatrixLookup, () -> {
            TopologyAwareFutureMatrixService matrixService = requireMatrixService();
            return resolveRetainedResult(
                    CallerScopedRetainedResultRegistry.ResultKind.MATRIX,
                    callerId,
                    resultSetId,
                    () -> matrixService.getResultSummary(resultSetId)
            );
        });
    }

    /**
     * Stage F1 retrieves retained matrix detail without recomputation.
     * Satisfies closure criterion: frontend retrieval flow can inspect retained future-aware matrix results without recomputing them.
     */
    public RetainedMatrixResultView.Detail matrixDetail(String callerId, String resultSetId) {
        return observe(operationalMetricsService::recordMatrixLookup, () -> {
            TopologyAwareFutureMatrixService matrixService = requireMatrixService();
            return resolveRetainedResult(
                    CallerScopedRetainedResultRegistry.ResultKind.MATRIX,
                    callerId,
                    resultSetId,
                    () -> matrixService.getResultDetail(resultSetId)
            );
        });
    }

    /**
     * Stage F2 records one route outcome update through the canonical API facade.
     * Satisfies closure criterion: served route predictions can be joined back to outcome telemetry with stable lineage.
     */
    public PredictionFeedbackResponse recordRouteFeedback(
            String callerId,
            String resultSetId,
            PredictionFeedbackRequest request
    ) {
        return observe(
                operationalMetricsService::recordRouteFeedback,
                () -> predictionTelemetryStore.recordRouteFeedback(callerId, resultSetId, request)
        );
    }

    /**
     * Stage F2 records one matrix outcome update through the canonical API facade.
     * Satisfies closure criterion: served matrix predictions can be joined back to outcome telemetry with stable lineage.
     */
    public PredictionFeedbackResponse recordMatrixFeedback(
            String callerId,
            String resultSetId,
            PredictionFeedbackRequest request
    ) {
        return observe(
                operationalMetricsService::recordMatrixFeedback,
                () -> predictionTelemetryStore.recordMatrixFeedback(callerId, resultSetId, request)
        );
    }

    /**
     * Stage F3 exposes the canonical low-cardinality operational metrics snapshot.
     * Satisfies closure criterion: the system exposes enough metrics and health signal to detect failures in future-aware serving and topology evolution.
     */
    public OperationalMetricsResponse metrics() {
        return operationalMetricsService.metrics();
    }

    /**
     * Stage F3 exposes the canonical builder-side and serving-side governance posture.
     * Satisfies closure criterion: rollout posture is explicit for both builder-time and serving-time changes.
     */
    public OperationalGovernanceResponse governance() {
        return operationalMetricsService.governance();
    }

    /**
     * Stage F1 exposes API readiness and active topology identity.
     * Satisfies closure criterion: health behavior for future-aware serving is explicit.
     */
    public HealthApiResponse health() {
        TopologyRuntimeSnapshot snapshot = currentSnapshot();
        boolean routeConfigured = topologyAwareRouteServiceProvider.getIfAvailable() != null;
        boolean matrixConfigured = topologyAwareMatrixServiceProvider.getIfAvailable() != null;
        OperationalMetricsResponse.AlertSummary alertSummary = operationalMetricsService.alertSummary();
        String status = routeConfigured
                && matrixConfigured
                && snapshot != null
                && !"DEGRADED".equals(alertSummary.overallStatus())
                ? "UP"
                : "DEGRADED";
        return new HealthApiResponse(
                status,
                clock.instant(),
                routeConfigured,
                matrixConfigured,
                snapshot == null ? null : snapshot.getTopologyVersion().getTopologyVersion(),
                snapshot == null ? null : snapshot.getFailureQuarantine().snapshot(0L).snapshotId(),
                retainedResultRegistry.size(),
                alertSummary.reloadStatus(),
                alertSummary.overallStatus()
        );
    }

    /**
     * Stage F1 exposes retained-result maintenance through the admin API.
     * Satisfies closure criterion: admin behavior around retained-result access is explicit.
     */
    public AdminPurgeResponse purgeExpiredResults() {
        return observe(operationalMetricsService::recordRetainedResultPurge, () -> {
            FutureRouteService routeService = routeServiceProvider.getIfAvailable();
            FutureMatrixService matrixService = matrixServiceProvider.getIfAvailable();
            if (routeService != null) {
                routeService.purgeExpired();
            }
            if (matrixService != null) {
                matrixService.purgeExpired();
            }
            retainedResultRegistry.pruneExpiredMetadata();
            TopologyRuntimeSnapshot snapshot = currentSnapshot();
            return new AdminPurgeResponse(
                    clock.instant(),
                    routeService != null,
                    matrixService != null,
                    snapshot == null ? null : snapshot.getTopologyVersion().getTopologyVersion(),
                    retainedResultRegistry.size()
            );
        });
    }

    private TopologyAwareFutureRouteService requireRouteService() {
        TopologyAwareFutureRouteService routeService = topologyAwareRouteServiceProvider.getIfAvailable();
        if (routeService == null) {
            throw TaroApiException.serviceUnavailable("route API is not configured with an active future-aware route service");
        }
        return routeService;
    }

    private FutureRouteService requireDirectRouteService() {
        FutureRouteService routeService = routeServiceProvider.getIfAvailable();
        if (routeService == null) {
            throw TaroApiException.serviceUnavailable("route API is not configured with a direct future-aware route service");
        }
        return routeService;
    }

    private TopologyAwareFutureMatrixService requireMatrixService() {
        TopologyAwareFutureMatrixService matrixService = topologyAwareMatrixServiceProvider.getIfAvailable();
        if (matrixService == null) {
            throw TaroApiException.serviceUnavailable("matrix API is not configured with an active future-aware matrix service");
        }
        return matrixService;
    }

    private FutureMatrixService requireDirectMatrixService() {
        FutureMatrixService matrixService = matrixServiceProvider.getIfAvailable();
        if (matrixService == null) {
            throw TaroApiException.serviceUnavailable("matrix API is not configured with a direct future-aware matrix service");
        }
        return matrixService;
    }

    private <T> T resolveRetainedResult(
            CallerScopedRetainedResultRegistry.ResultKind resultKind,
            String callerId,
            String resultSetId,
            Supplier<Optional<T>> lookup
    ) {
        CallerScopedRetainedResultRegistry.Entry entry = retainedResultRegistry.find(resultKind, resultSetId)
                .orElseThrow(() -> TaroApiException.invalidResultSetId(resultSetId));
        if (!entry.callerId().equals(callerId)) {
            throw TaroApiException.unauthorizedResultAccess(resultSetId);
        }
        Optional<T> retainedValue = lookup.get();
        if (retainedValue.isPresent()) {
            return retainedValue.get();
        }
        if (!entry.expiresAt().isAfter(clock.instant())) {
            throw TaroApiException.expiredResult(resultSetId);
        }
        TopologyRuntimeSnapshot snapshot = currentSnapshot();
        if (snapshot != null && !entry.topologyVersionId().equals(snapshot.getTopologyVersion().getTopologyVersion())) {
            throw TaroApiException.incompatibleResult(resultSetId);
        }
        throw TaroApiException.evictedResult(resultSetId);
    }

    private TopologyRuntimeSnapshot currentSnapshot() {
        TopologyReloadCoordinator reloadCoordinator = reloadCoordinatorProvider.getIfAvailable();
        return reloadCoordinator == null ? null : reloadCoordinator.currentSnapshot();
    }

    private TopologyRuntimeSnapshot requireActiveSnapshot(String message) {
        TopologyRuntimeSnapshot snapshot = currentSnapshot();
        if (snapshot == null) {
            throw TaroApiException.serviceUnavailable(message);
        }
        return snapshot;
    }

    private <T> T observe(OperationRecorder recorder, Supplier<T> supplier) {
        long startedAt = System.nanoTime();
        boolean success = false;
        try {
            T result = supplier.get();
            success = true;
            return result;
        } finally {
            recorder.record(System.nanoTime() - startedAt, success);
        }
    }

    @FunctionalInterface
    private interface OperationRecorder {
        void record(long latencyNanos, boolean success);
    }
}
