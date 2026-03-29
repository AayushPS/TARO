package org.Aayush.api;

import org.Aayush.routing.core.RouteCore;
import org.Aayush.routing.execution.ResolvedExecutionProfileContext;
import org.Aayush.routing.future.FutureMatrixAggregate;
import org.Aayush.routing.future.FutureMatrixResultSet;
import org.Aayush.routing.future.FutureRouteResultSet;
import org.Aayush.routing.future.ScenarioBundle;
import org.Aayush.routing.future.ScenarioDefinition;
import org.Aayush.routing.future.ScenarioRouteSelection;
import org.Aayush.routing.topology.TopologyRuntimeSnapshot;
import org.Aayush.routing.topology.TopologyVersion;
import org.Aayush.routing.traits.registry.ResolvedTraitBundleContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Stage F2 bounded telemetry store that joins served prediction lineage with eventual outcome feedback.
 * Satisfies closure criteria: served predictions can be joined back to outcome telemetry with stable lineage, and export rows are available without ad hoc log joins.
 */
@Component
public final class PredictionTelemetryStore {
    private static final Config DEFAULT_CONFIG = new Config(4_096, Duration.ofHours(24));

    private final LinkedHashMap<String, StoredRecord> entries = new LinkedHashMap<>();
    private final Clock clock;
    private final Config config;

    @Autowired
    public PredictionTelemetryStore(ObjectProvider<Clock> clockProvider) {
        Clock providedClock = clockProvider.getIfAvailable();
        this.clock = providedClock == null ? Clock.systemUTC() : providedClock;
        this.config = DEFAULT_CONFIG;
    }

    PredictionTelemetryStore(Clock clock, Config config) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.config = Objects.requireNonNull(config, "config");
    }

    /**
     * Stage F2 records one served route prediction immediately after F1 returns a retained `resultSetId`.
     * Satisfies closure criterion: served predictions can be joined back to outcome telemetry with stable lineage.
     */
    public synchronized void recordRoutePrediction(
            String callerId,
            TopologyRuntimeSnapshot snapshot,
            FutureRouteResultSet resultSet
    ) {
        recordPrediction(buildRouteRecord(callerId, snapshot, resultSet));
    }

    /**
     * Stage F2 records one served matrix prediction immediately after F1 returns a retained `resultSetId`.
     * Satisfies closure criterion: served predictions can be joined back to outcome telemetry with stable lineage.
     */
    public synchronized void recordMatrixPrediction(
            String callerId,
            TopologyRuntimeSnapshot snapshot,
            FutureMatrixResultSet resultSet
    ) {
        recordPrediction(buildMatrixRecord(callerId, snapshot, resultSet));
    }

    /**
     * Stage F2 joins a route outcome update back to the served prediction lineage captured at serve time.
     * Satisfies closure criterion: eventual outcome telemetry joins to a stable served prediction identity without recomputation.
     */
    public synchronized PredictionFeedbackResponse recordRouteFeedback(
            String callerId,
            String resultSetId,
            PredictionFeedbackRequest request
    ) {
        return recordFeedback(CallerScopedRetainedResultRegistry.ResultKind.ROUTE, callerId, resultSetId, request);
    }

    /**
     * Stage F2 joins a matrix outcome update back to the served prediction lineage captured at serve time.
     * Satisfies closure criterion: eventual outcome telemetry joins to a stable served prediction identity without recomputation.
     */
    public synchronized PredictionFeedbackResponse recordMatrixFeedback(
            String callerId,
            String resultSetId,
            PredictionFeedbackRequest request
    ) {
        return recordFeedback(CallerScopedRetainedResultRegistry.ResultKind.MATRIX, callerId, resultSetId, request);
    }

    /**
     * Stage F2 exports already-joined telemetry rows filtered by lineage dimensions.
     * Satisfies closure criterion: retraining exports are possible without reconstructing ad hoc joins from scattered logs.
     */
    public synchronized List<ExportRow> exportRows(ExportFilter filter) {
        pruneExpiredLocked(clock.instant());
        ExportFilter effectiveFilter = filter == null ? new ExportFilter(null, null, null, null, false) : filter;
        List<ExportRow> rows = new ArrayList<>();
        for (StoredRecord record : entries.values()) {
            ExportRow row = record.toExportRow();
            if (matches(row, effectiveFilter)) {
                rows.add(row);
            }
        }
        return List.copyOf(rows);
    }

    synchronized Optional<ExportRow> findExportRow(
            CallerScopedRetainedResultRegistry.ResultKind resultKind,
            String resultSetId
    ) {
        pruneExpiredLocked(clock.instant());
        StoredRecord record = entries.get(key(resultKind, resultSetId));
        return record == null ? Optional.empty() : Optional.of(record.toExportRow());
    }

    synchronized int size() {
        pruneExpiredLocked(clock.instant());
        return entries.size();
    }

    private void recordPrediction(StoredRecord record) {
        pruneExpiredLocked(clock.instant());
        putLocked(record.identity.key(), record);
    }

    private PredictionFeedbackResponse recordFeedback(
            CallerScopedRetainedResultRegistry.ResultKind resultKind,
            String callerId,
            String resultSetId,
            PredictionFeedbackRequest request
    ) {
        PredictionFeedbackRequest nonNullRequest = Objects.requireNonNull(request, "request");
        validateFeedbackRequest(nonNullRequest);
        pruneExpiredLocked(clock.instant());

        String key = key(resultKind, resultSetId);
        StoredRecord existing = entries.get(key);
        if (existing == null) {
            throw TaroApiException.invalidResultSetId(resultSetId);
        }
        if (!existing.prediction.callerId.equals(callerId)) {
            throw TaroApiException.unauthorizedResultAccess(resultSetId);
        }

        OutcomeRecord outcomeRecord = new OutcomeRecord(
                nonNullRequest.outcomeStatus(),
                clock.instant(),
                nonNullRequest.observedAtTicks(),
                nonNullRequest.observedArrivalTicks(),
                nonNullRequest.observedCostSeconds(),
                nonNullRequest.observationCount()
        );
        StoredRecord updated = new StoredRecord(existing.identity, existing.prediction, outcomeRecord);
        putLocked(key, updated);
        return updated.toFeedbackResponse();
    }

    private void validateFeedbackRequest(PredictionFeedbackRequest request) {
        if (request.observedAtTicks() != null && request.observedAtTicks() < 0L) {
            throw TaroApiException.badRequest("observedAtTicks must be non-negative");
        }
        if (request.observedArrivalTicks() != null && request.observedArrivalTicks() < 0L) {
            throw TaroApiException.badRequest("observedArrivalTicks must be non-negative");
        }
        if (request.observedCostSeconds() != null && request.observedCostSeconds() < 0.0d) {
            throw TaroApiException.badRequest("observedCostSeconds must be non-negative");
        }
        if (request.observationCount() != null && request.observationCount() <= 0) {
            throw TaroApiException.badRequest("observationCount must be positive");
        }
        if (request.outcomeStatus() == PredictionFeedbackRequest.OutcomeStatus.COMPLETE
                && request.observedArrivalTicks() == null
                && request.observedCostSeconds() == null) {
            throw TaroApiException.badRequest(
                    "complete feedback requires observedArrivalTicks or observedCostSeconds"
            );
        }
    }

    private boolean matches(ExportRow row, ExportFilter filter) {
        if (filter.resultKind() != null && filter.resultKind() != row.resultKind()) {
            return false;
        }
        if (filter.topologyVersionId() != null && !filter.topologyVersionId().equals(row.topologyVersionId())) {
            return false;
        }
        if (filter.scenarioBundleId() != null && !filter.scenarioBundleId().equals(row.scenarioBundleId())) {
            return false;
        }
        if (filter.traitHash() != null && !Objects.equals(filter.traitHash(), row.traitHash())) {
            return false;
        }
        return !filter.completeOnly() || row.complete();
    }

    private void pruneExpiredLocked(Instant now) {
        ArrayList<String> expiredKeys = new ArrayList<>();
        for (var entry : entries.entrySet()) {
            if (!entry.getValue().prediction.expiresAt.plus(config.retentionGrace()).isAfter(now)) {
                expiredKeys.add(entry.getKey());
            }
        }
        expiredKeys.forEach(entries::remove);
    }

    private void putLocked(String key, StoredRecord record) {
        entries.remove(key);
        entries.put(key, record);
        while (entries.size() > config.maxEntries()) {
            String eldestKey = entries.keySet().iterator().next();
            entries.remove(eldestKey);
        }
    }

    private StoredRecord buildRouteRecord(
            String callerId,
            TopologyRuntimeSnapshot snapshot,
            FutureRouteResultSet resultSet
    ) {
        TopologyRuntimeSnapshot nonNullSnapshot = Objects.requireNonNull(snapshot, "snapshot");
        FutureRouteResultSet nonNullResultSet = Objects.requireNonNull(resultSet, "resultSet");
        PredictionMetadata prediction = basePrediction(
                CallerScopedRetainedResultRegistry.ResultKind.ROUTE,
                callerId,
                nonNullSnapshot,
                nonNullResultSet.getResultSetId(),
                nonNullResultSet.getCreatedAt(),
                nonNullResultSet.getExpiresAt(),
                nonNullResultSet.getQuarantineSnapshotId(),
                nonNullResultSet.getScenarioBundle(),
                nonNullResultSet.getRequest().getDepartureTicks(),
                nonNullResultSet.getRequest().getHorizonTicks(),
                nonNullResultSet.getRequest().getPreferredObjective().name(),
                nonNullResultSet.getRequest().getTopKAlternatives(),
                routeExpectedCost(nonNullResultSet.getExpectedRoute()),
                routeRobustCost(nonNullResultSet.getRobustRoute()),
                null,
                null
        );
        return new StoredRecord(
                new PredictionIdentity(CallerScopedRetainedResultRegistry.ResultKind.ROUTE, nonNullResultSet.getResultSetId()),
                prediction,
                null
        );
    }

    private StoredRecord buildMatrixRecord(
            String callerId,
            TopologyRuntimeSnapshot snapshot,
            FutureMatrixResultSet resultSet
    ) {
        TopologyRuntimeSnapshot nonNullSnapshot = Objects.requireNonNull(snapshot, "snapshot");
        FutureMatrixResultSet nonNullResultSet = Objects.requireNonNull(resultSet, "resultSet");
        FutureMatrixAggregate aggregate = nonNullResultSet.getAggregate();
        Integer sourceCount = aggregate == null ? null : aggregate.getSourceExternalIds().size();
        Integer targetCount = aggregate == null ? null : aggregate.getTargetExternalIds().size();
        PredictionMetadata prediction = basePrediction(
                CallerScopedRetainedResultRegistry.ResultKind.MATRIX,
                callerId,
                nonNullSnapshot,
                nonNullResultSet.getResultSetId(),
                nonNullResultSet.getCreatedAt(),
                nonNullResultSet.getExpiresAt(),
                nonNullResultSet.getQuarantineSnapshotId(),
                nonNullResultSet.getScenarioBundle(),
                nonNullResultSet.getRequest().getDepartureTicks(),
                nonNullResultSet.getRequest().getHorizonTicks(),
                null,
                null,
                aggregate == null ? null : average(aggregate.getExpectedCosts()),
                aggregate == null ? null : average(aggregate.getP90Costs()),
                sourceCount,
                targetCount
        );
        return new StoredRecord(
                new PredictionIdentity(CallerScopedRetainedResultRegistry.ResultKind.MATRIX, nonNullResultSet.getResultSetId()),
                prediction,
                null
        );
    }

    private PredictionMetadata basePrediction(
            CallerScopedRetainedResultRegistry.ResultKind resultKind,
            String callerId,
            TopologyRuntimeSnapshot snapshot,
            String resultSetId,
            Instant servedAt,
            Instant expiresAt,
            String quarantineSnapshotId,
            ScenarioBundle scenarioBundle,
            long departureTicks,
            long horizonTicks,
            String preferredObjective,
            Integer topKAlternatives,
            Double predictedExpectedCostSeconds,
            Double predictedRobustCostSeconds,
            Integer matrixSourceCount,
            Integer matrixTargetCount
    ) {
        TopologyVersion topologyVersion = Objects.requireNonNull(snapshot.getTopologyVersion(), "snapshot.topologyVersion");
        RouteCore routeCore = Objects.requireNonNull(snapshot.getRouteCore(), "snapshot.routeCore");
        ResolvedTraitBundleContext traitBundleContext = routeCore.resolvedTraitBundleContext();
        ResolvedExecutionProfileContext executionProfileContext = routeCore.executionProfileContext();
        ScenarioLineage scenarioLineage = scenarioLineage(Objects.requireNonNull(scenarioBundle, "scenarioBundle"));
        String normalizedCallerId = requireText(callerId, "callerId");
        return new PredictionMetadata(
                resultKind,
                normalizedCallerId,
                hashCaller(normalizedCallerId),
                requireText(resultSetId, "resultSetId"),
                Objects.requireNonNull(servedAt, "servedAt"),
                Objects.requireNonNull(expiresAt, "expiresAt"),
                topologyVersion.getTopologyVersion(),
                topologyVersion.getModelVersion(),
                topologyVersion.getSourceDataLineageHash(),
                topologyVersion.getChangeSetHash(),
                traitBundleContext == null ? null : traitBundleContext.getBundleId(),
                traitBundleContext == null ? null : traitBundleContext.getTraitHash(),
                executionProfileContext == null ? null : executionProfileContext.getProfileId(),
                quarantineSnapshotId,
                scenarioBundle.getScenarioBundleId(),
                scenarioLineage,
                departureTicks,
                horizonTicks,
                preferredObjective,
                topKAlternatives,
                predictedExpectedCostSeconds,
                predictedRobustCostSeconds,
                matrixSourceCount,
                matrixTargetCount,
                servedAt.atOffset(ZoneOffset.UTC).toLocalDate().toString()
        );
    }

    private ScenarioLineage scenarioLineage(ScenarioBundle scenarioBundle) {
        ArrayList<String> scenarioIds = new ArrayList<>();
        ArrayList<String> scenarioLabels = new ArrayList<>();
        ArrayList<Double> scenarioProbabilities = new ArrayList<>();
        for (ScenarioDefinition scenario : scenarioBundle.getScenarios()) {
            scenarioIds.add(Objects.toString(scenario.getScenarioId(), ""));
            scenarioLabels.add(Objects.toString(scenario.getLabel(), ""));
            scenarioProbabilities.add(scenario.getProbability());
        }
        return new ScenarioLineage(
                List.copyOf(scenarioIds),
                List.copyOf(scenarioLabels),
                List.copyOf(scenarioProbabilities)
        );
    }

    private Double routeExpectedCost(ScenarioRouteSelection selection) {
        return selection == null ? null : (double) selection.getExpectedCost();
    }

    private Double routeRobustCost(ScenarioRouteSelection selection) {
        return selection == null ? null : (double) selection.getP90Cost();
    }

    private Double average(float[][] values) {
        if (values.length == 0) {
            return null;
        }
        double total = 0.0d;
        int count = 0;
        for (float[] row : values) {
            for (float value : row) {
                total += value;
                count++;
            }
        }
        return count == 0 ? null : total / count;
    }

    private String key(CallerScopedRetainedResultRegistry.ResultKind resultKind, String resultSetId) {
        return requireText(resultSetId, "resultSetId") + ":" + Objects.requireNonNull(resultKind, "resultKind").name();
    }

    private String hashCaller(String callerId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(
                    digest.digest(callerId.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must be non-blank");
        }
        return value;
    }

    record PredictionIdentity(
            CallerScopedRetainedResultRegistry.ResultKind resultKind,
            String resultSetId
    ) {
        String key() {
            return resultSetId + ":" + resultKind.name();
        }
    }

    record ScenarioLineage(
            List<String> scenarioIds,
            List<String> scenarioLabels,
            List<Double> scenarioProbabilities
    ) {
    }

    record PredictionMetadata(
            CallerScopedRetainedResultRegistry.ResultKind resultKind,
            String callerId,
            String callerHash,
            String resultSetId,
            Instant servedAt,
            Instant expiresAt,
            String topologyVersionId,
            String modelVersion,
            String sourceDataLineageHash,
            String changeSetHash,
            String traitBundleId,
            String traitHash,
            String executionProfileId,
            String quarantineSnapshotId,
            String scenarioBundleId,
            ScenarioLineage scenarioLineage,
            long departureTicks,
            long horizonTicks,
            String preferredObjective,
            Integer topKAlternatives,
            Double predictedExpectedCostSeconds,
            Double predictedRobustCostSeconds,
            Integer matrixSourceCount,
            Integer matrixTargetCount,
            String partitionDate
    ) {
    }

    record OutcomeRecord(
            PredictionFeedbackRequest.OutcomeStatus outcomeStatus,
            Instant feedbackRecordedAt,
            Long observedAtTicks,
            Long observedArrivalTicks,
            Double observedCostSeconds,
            Integer observationCount
    ) {
        boolean complete() {
            return outcomeStatus == PredictionFeedbackRequest.OutcomeStatus.COMPLETE;
        }
    }

    record StoredRecord(
            PredictionIdentity identity,
            PredictionMetadata prediction,
            OutcomeRecord outcome
    ) {
        PredictionFeedbackResponse toFeedbackResponse() {
            boolean complete = outcome != null && outcome.complete();
            return new PredictionFeedbackResponse(
                    prediction.resultKind(),
                    prediction.resultSetId(),
                    outcome == null ? PredictionFeedbackRequest.OutcomeStatus.PARTIAL : outcome.outcomeStatus(),
                    complete,
                    outcome == null ? prediction.servedAt() : outcome.feedbackRecordedAt(),
                    prediction.topologyVersionId(),
                    prediction.scenarioBundleId()
            );
        }

        ExportRow toExportRow() {
            boolean complete = outcome != null && outcome.complete();
            return new ExportRow(
                    prediction.resultKind(),
                    prediction.resultKind().name() + ":" + prediction.resultSetId(),
                    prediction.resultSetId(),
                    prediction.servedAt(),
                    outcome == null ? null : outcome.feedbackRecordedAt(),
                    outcome == null ? PredictionFeedbackRequest.OutcomeStatus.PARTIAL.name() : outcome.outcomeStatus().name(),
                    complete,
                    prediction.callerHash(),
                    prediction.topologyVersionId(),
                    prediction.modelVersion(),
                    prediction.sourceDataLineageHash(),
                    prediction.changeSetHash(),
                    prediction.traitBundleId(),
                    prediction.traitHash(),
                    prediction.executionProfileId(),
                    prediction.quarantineSnapshotId(),
                    prediction.scenarioBundleId(),
                    prediction.scenarioLineage().scenarioIds().size(),
                    prediction.scenarioLineage().scenarioIds(),
                    prediction.scenarioLineage().scenarioLabels(),
                    prediction.scenarioLineage().scenarioProbabilities(),
                    prediction.departureTicks(),
                    prediction.horizonTicks(),
                    prediction.preferredObjective(),
                    prediction.topKAlternatives(),
                    prediction.predictedExpectedCostSeconds(),
                    prediction.predictedRobustCostSeconds(),
                    prediction.matrixSourceCount(),
                    prediction.matrixTargetCount(),
                    outcome == null ? null : outcome.observedAtTicks(),
                    outcome == null ? null : outcome.observedArrivalTicks(),
                    outcome == null ? null : outcome.observedCostSeconds(),
                    outcome == null ? null : outcome.observationCount(),
                    prediction.partitionDate()
            );
        }
    }

    /**
     * Stage F2 bounded-buffer configuration for joined telemetry retention.
     * Satisfies closure criterion: feedback capture remains bounded and explicit under load.
     */
    public record Config(
            int maxEntries,
            Duration retentionGrace
    ) {
        public Config {
            if (maxEntries <= 0) {
                throw new IllegalArgumentException("maxEntries must be positive");
            }
            Objects.requireNonNull(retentionGrace, "retentionGrace");
            if (retentionGrace.isNegative()) {
                throw new IllegalArgumentException("retentionGrace must be non-negative");
            }
        }
    }

    /**
     * Stage F2 lineage-filter contract for joined telemetry export rows.
     * Satisfies closure criterion: retraining export can select already-joined rows by stable lineage dimensions.
     */
    public record ExportFilter(
            CallerScopedRetainedResultRegistry.ResultKind resultKind,
            String topologyVersionId,
            String scenarioBundleId,
            String traitHash,
            boolean completeOnly
    ) {
    }

    /**
     * Stage F2 joined telemetry export row used by retraining and calibration consumers.
     * Satisfies closure criterion: served prediction lineage and eventual outcomes remain exportable without ad hoc joins.
     */
    public record ExportRow(
            CallerScopedRetainedResultRegistry.ResultKind resultKind,
            String predictionId,
            String resultSetId,
            Instant servedAt,
            Instant feedbackRecordedAt,
            String outcomeStatus,
            boolean complete,
            String callerHash,
            String topologyVersionId,
            String modelVersion,
            String sourceDataLineageHash,
            String changeSetHash,
            String traitBundleId,
            String traitHash,
            String executionProfileId,
            String quarantineSnapshotId,
            String scenarioBundleId,
            int scenarioCount,
            List<String> scenarioIds,
            List<String> scenarioLabels,
            List<Double> scenarioProbabilities,
            long departureTicks,
            long horizonTicks,
            String preferredObjective,
            Integer topKAlternatives,
            Double predictedExpectedCostSeconds,
            Double predictedRobustCostSeconds,
            Integer matrixSourceCount,
            Integer matrixTargetCount,
            Long observedAtTicks,
            Long observedArrivalTicks,
            Double observedCostSeconds,
            Integer observationCount,
            String partitionDate
    ) {
    }
}
