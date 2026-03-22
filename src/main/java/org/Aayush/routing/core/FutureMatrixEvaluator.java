package org.Aayush.routing.core;

import org.Aayush.routing.cost.CostEngine;
import org.Aayush.routing.future.FutureMatrixAggregate;
import org.Aayush.routing.future.FutureMatrixRequest;
import org.Aayush.routing.future.FutureMatrixResultSet;
import org.Aayush.routing.future.FutureMatrixScenarioResult;
import org.Aayush.routing.future.ScenarioBundle;
import org.Aayush.routing.future.ScenarioBundleResolver;
import org.Aayush.routing.future.ScenarioDefinition;
import org.Aayush.routing.topology.FailureQuarantine;
import org.Aayush.routing.topology.TopologyRuntimeSnapshot;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * v12 multi-scenario matrix evaluator built on top of the deterministic {@link RouteCore}.
 */
public final class FutureMatrixEvaluator {
    private static final String AGGREGATION_NOTE =
            "Aggregated over per-scenario shortest-path costs for each matrix cell.";

    private final ScenarioBundleResolver scenarioBundleResolver;
    private final Clock clock;

    public FutureMatrixEvaluator(ScenarioBundleResolver scenarioBundleResolver) {
        this(scenarioBundleResolver, Clock.systemUTC());
    }

    public FutureMatrixEvaluator(ScenarioBundleResolver scenarioBundleResolver, Clock clock) {
        this.scenarioBundleResolver = Objects.requireNonNull(scenarioBundleResolver, "scenarioBundleResolver");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public FutureMatrixResultSet evaluate(TopologyRuntimeSnapshot snapshot, FutureMatrixRequest request) {
        TopologyRuntimeSnapshot nonNullSnapshot = Objects.requireNonNull(snapshot, "snapshot");
        FutureMatrixRequest nonNullRequest = Objects.requireNonNull(request, "request");
        validateRequest(nonNullRequest);

        RouteCore routeCore = nonNullSnapshot.getRouteCore();
        RequestNormalizer.NormalizedMatrixRequest normalized = routeCore.normalizeMatrixRequest(nonNullRequest.getMatrixRequest());
        long departureTicks = normalized.getInternalRequest().departureTicks();
        FailureQuarantine.Snapshot quarantineSnapshot = nonNullSnapshot.getFailureQuarantine().snapshot(departureTicks);
        ScenarioBundle scenarioBundle = scenarioBundleResolver.resolve(
                nonNullRequest,
                routeCore.costEngineContract(),
                routeCore.temporalContextContract(),
                nonNullSnapshot.getTopologyVersion(),
                quarantineSnapshot,
                clock
        );
        FutureScenarioSupport.validateScenarioBundle(scenarioBundle, nonNullSnapshot, quarantineSnapshot);

        ArrayList<EvaluatedScenario> evaluatedScenarios = new ArrayList<>(scenarioBundle.getScenarios().size());
        for (ScenarioDefinition scenario : scenarioBundle.getScenarios()) {
            CostEngine scenarioCostEngine = FutureScenarioSupport.buildScenarioCostEngine(
                    routeCore.costEngineContract(),
                    departureTicks,
                    scenario.getLiveUpdates()
            );
            MatrixPlan matrixPlan = routeCore.computeMatrixInternal(normalized.getInternalRequest(), scenarioCostEngine);
            MatrixResponse response = RouteCoreMatrixSupport.buildResponse(normalized, matrixPlan);
            evaluatedScenarios.add(new EvaluatedScenario(scenario, matrixPlan, response));
        }

        Instant createdAt = clock.instant();
        return FutureMatrixResultSet.builder()
                .resultSetId(UUID.randomUUID().toString())
                .createdAt(createdAt)
                .expiresAt(createdAt.plus(nonNullRequest.getResultTtl()))
                .request(nonNullRequest)
                .topologyVersion(nonNullSnapshot.getTopologyVersion())
                .quarantineSnapshotId(quarantineSnapshot.snapshotId())
                .scenarioBundle(scenarioBundle)
                .aggregate(aggregate(normalized, evaluatedScenarios))
                .scenarioResults(toScenarioResults(evaluatedScenarios))
                .build();
    }

    private FutureMatrixAggregate aggregate(
            RequestNormalizer.NormalizedMatrixRequest normalized,
            List<EvaluatedScenario> evaluatedScenarios
    ) {
        int sourceCount = normalized.getSourceExternalIds().size();
        int targetCount = normalized.getTargetExternalIds().size();

        double[][] reachabilityProbabilities = new double[sourceCount][targetCount];
        float[][] expectedCosts = new float[sourceCount][targetCount];
        float[][] p50Costs = new float[sourceCount][targetCount];
        float[][] p90Costs = new float[sourceCount][targetCount];
        float[][] minCosts = new float[sourceCount][targetCount];
        float[][] maxCosts = new float[sourceCount][targetCount];
        long[][] minArrivalTicks = new long[sourceCount][targetCount];
        long[][] maxArrivalTicks = new long[sourceCount][targetCount];

        for (int row = 0; row < sourceCount; row++) {
            for (int col = 0; col < targetCount; col++) {
                ArrayList<CellScenarioSample> samples = new ArrayList<>(evaluatedScenarios.size());
                for (EvaluatedScenario evaluatedScenario : evaluatedScenarios) {
                    boolean reachable = evaluatedScenario.plan().reachable()[row][col];
                    float cost = reachable
                            ? evaluatedScenario.plan().totalCosts()[row][col]
                            : Float.POSITIVE_INFINITY;
                    long arrivalTicks = reachable
                            ? evaluatedScenario.plan().arrivalTicks()[row][col]
                            : Long.MAX_VALUE;
                    if (reachable) {
                        reachabilityProbabilities[row][col] += evaluatedScenario.scenario().getProbability();
                    }
                    samples.add(new CellScenarioSample(
                            evaluatedScenario.scenario().getProbability(),
                            cost,
                            arrivalTicks,
                            reachable
                    ));
                }

                expectedCosts[row][col] = expectedCost(samples);
                p50Costs[row][col] = percentileCost(samples, 0.50d);
                p90Costs[row][col] = percentileCost(samples, 0.90d);
                minCosts[row][col] = minCost(samples);
                maxCosts[row][col] = maxCost(samples);
                minArrivalTicks[row][col] = minArrival(samples);
                maxArrivalTicks[row][col] = maxArrival(samples);
            }
        }

        return FutureMatrixAggregate.builder()
                .sourceExternalIds(normalized.getSourceExternalIds())
                .targetExternalIds(normalized.getTargetExternalIds())
                .reachabilityProbabilities(reachabilityProbabilities)
                .expectedCosts(expectedCosts)
                .p50Costs(p50Costs)
                .p90Costs(p90Costs)
                .minCosts(minCosts)
                .maxCosts(maxCosts)
                .minArrivalTicks(minArrivalTicks)
                .maxArrivalTicks(maxArrivalTicks)
                .aggregationNote(AGGREGATION_NOTE)
                .build();
    }

    private List<FutureMatrixScenarioResult> toScenarioResults(List<EvaluatedScenario> evaluatedScenarios) {
        ArrayList<FutureMatrixScenarioResult> results = new ArrayList<>(evaluatedScenarios.size());
        for (EvaluatedScenario evaluatedScenario : evaluatedScenarios) {
            results.add(FutureMatrixScenarioResult.builder()
                    .scenarioId(evaluatedScenario.scenario().getScenarioId())
                    .label(evaluatedScenario.scenario().getLabel())
                    .probability(evaluatedScenario.scenario().getProbability())
                    .matrix(evaluatedScenario.matrixResponse())
                    .explanationTags(evaluatedScenario.scenario().getExplanationTags())
                    .build());
        }
        return List.copyOf(results);
    }

    private float expectedCost(List<CellScenarioSample> samples) {
        double weighted = 0.0d;
        for (CellScenarioSample sample : samples) {
            if (!Float.isFinite(sample.cost()) && sample.probability() > 0.0d) {
                return Float.POSITIVE_INFINITY;
            }
            weighted += sample.probability() * sample.cost();
        }
        if (!Double.isFinite(weighted) || weighted > Float.MAX_VALUE) {
            return Float.POSITIVE_INFINITY;
        }
        return (float) weighted;
    }

    private float percentileCost(List<CellScenarioSample> samples, double percentile) {
        ArrayList<CellScenarioSample> sorted = new ArrayList<>(samples);
        sorted.sort(Comparator.comparingDouble(CellScenarioSample::cost));

        double totalProbability = 0.0d;
        for (CellScenarioSample sample : sorted) {
            totalProbability += sample.probability();
        }
        double threshold = percentile * totalProbability;
        double cumulative = 0.0d;
        for (CellScenarioSample sample : sorted) {
            cumulative += sample.probability();
            if (cumulative + FutureScenarioSupport.PROBABILITY_TOLERANCE >= threshold) {
                return sample.cost();
            }
        }
        return sorted.get(sorted.size() - 1).cost();
    }

    private float minCost(List<CellScenarioSample> samples) {
        float min = Float.POSITIVE_INFINITY;
        for (CellScenarioSample sample : samples) {
            min = Math.min(min, sample.cost());
        }
        return min;
    }

    private float maxCost(List<CellScenarioSample> samples) {
        float max = 0.0f;
        for (CellScenarioSample sample : samples) {
            if (!Float.isFinite(sample.cost())) {
                return Float.POSITIVE_INFINITY;
            }
            max = Math.max(max, sample.cost());
        }
        return max;
    }

    private long minArrival(List<CellScenarioSample> samples) {
        long min = Long.MAX_VALUE;
        for (CellScenarioSample sample : samples) {
            if (sample.reachable()) {
                min = Math.min(min, sample.arrivalTicks());
            }
        }
        return min;
    }

    private long maxArrival(List<CellScenarioSample> samples) {
        long max = 0L;
        for (CellScenarioSample sample : samples) {
            if (!sample.reachable()) {
                return Long.MAX_VALUE;
            }
            max = Math.max(max, sample.arrivalTicks());
        }
        return max;
    }

    private void validateRequest(FutureMatrixRequest request) {
        if (request.getMatrixRequest() == null) {
            throw new IllegalArgumentException("future-aware matrix request must include matrixRequest");
        }
        if (request.getHorizonTicks() <= 0L) {
            throw new IllegalArgumentException("horizonTicks must be > 0");
        }
        if (request.getResultTtl() == null || request.getResultTtl().isZero() || request.getResultTtl().isNegative()) {
            throw new IllegalArgumentException("resultTtl must be > 0");
        }
    }

    private record EvaluatedScenario(
            ScenarioDefinition scenario,
            MatrixPlan plan,
            MatrixResponse matrixResponse
    ) {
    }

    private record CellScenarioSample(
            double probability,
            float cost,
            long arrivalTicks,
            boolean reachable
    ) {
    }
}
