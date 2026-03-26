package org.Aayush.routing.core;

import org.Aayush.routing.cost.CostEngine;
import org.Aayush.routing.future.CandidateDensityCalibrationReport;
import org.Aayush.routing.future.CandidateDensityClass;
import org.Aayush.routing.future.FutureRouteRequest;
import org.Aayush.routing.future.FutureRouteResultSet;
import org.Aayush.routing.future.FutureRouteScenarioResult;
import org.Aayush.routing.future.RouteShape;
import org.Aayush.routing.future.ScenarioBundle;
import org.Aayush.routing.future.ScenarioBundleResolver;
import org.Aayush.routing.future.ScenarioDefinition;
import org.Aayush.routing.future.ScenarioRouteSelection;
import org.Aayush.routing.future.RouteSelectionProvenance;
import org.Aayush.routing.topology.FailureQuarantine;
import org.Aayush.routing.topology.TopologyRuntimeSnapshot;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * v12 multi-scenario route evaluator built on top of the deterministic {@link RouteCore}.
 */
public final class FutureRouteEvaluator {
    private static final String DENSITY_CALIBRATION_POLICY_ID = "b5-density-v2";
    private static final double PROBABILITY_TOLERANCE = 1.0e-6d;
    private static final double DISTINCT_PATH_OVERLAP_THRESHOLD = 0.85d;
    private static final float DISTINCT_EXPECTED_COST_TOLERANCE = 1.0f;
    private static final double ETA_BAND_LOWER_PERCENTILE = 0.10d;
    private static final double ETA_BAND_UPPER_PERCENTILE = 0.90d;

    private final ScenarioBundleResolver scenarioBundleResolver;
    private final Clock clock;
    private final PathEvaluator pathEvaluator;
    private final FutureRouteObjectivePlanner objectivePlanner;

    public FutureRouteEvaluator(ScenarioBundleResolver scenarioBundleResolver) {
        this(scenarioBundleResolver, Clock.systemUTC());
    }

    public FutureRouteEvaluator(ScenarioBundleResolver scenarioBundleResolver, Clock clock) {
        this.scenarioBundleResolver = Objects.requireNonNull(scenarioBundleResolver, "scenarioBundleResolver");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.pathEvaluator = new PathEvaluator();
        this.objectivePlanner = new FutureRouteObjectivePlanner();
    }

    /**
     * Stage C4 - alternatives, explanation, and confidence.
     * Satisfies closure criterion: explanation and confidence fields remain consistent with
     * scenario probabilities and aggregate objectives.
     */
    public FutureRouteResultSet evaluate(TopologyRuntimeSnapshot snapshot, FutureRouteRequest request) {
        TopologyRuntimeSnapshot nonNullSnapshot = Objects.requireNonNull(snapshot, "snapshot");
        FutureRouteRequest nonNullRequest = Objects.requireNonNull(request, "request");
        validateRequest(nonNullRequest);

        ResolvedRouteScenarioRequest resolved = resolveScenarioRequest(nonNullSnapshot, nonNullRequest);
        RouteScenarioExecution scenarioExecution = executeScenarios(
                resolved.routeCore(),
                resolved.normalized(),
                resolved.scenarioBundle()
        );

        Instant createdAt = clock.instant();
        Instant expiresAt = createdAt.plus(resolved.request().getResultTtl());
        ArrayList<CandidateRoute> candidates = new ArrayList<>(scenarioExecution.scenarioOptimalCandidates());
        Set<String> scenarioOptimalSignatures = new HashSet<>(scenarioExecution.scenarioOptimalSignatures());
        List<EvaluatedScenario> evaluatedScenarios = scenarioExecution.evaluatedScenarios();
        if (candidates.isEmpty()) {
            ScenarioRouteSelection unreachable = buildUnreachableSelection(resolved.normalized());
            CandidateDensityCalibrationReport densityCalibrationReport = buildDensityCalibrationReport(
                    resolved.scenarioBundle().getScenarios().size(),
                    scenarioExecution.scenarioOptimalRouteCount(),
                    scenarioOptimalSignatures.size(),
                    0,
                    false,
                    false,
                    0
            );
            return buildResultSet(
                    resolved.request(),
                    resolved.snapshot(),
                    resolved.quarantineSnapshot(),
                    resolved.scenarioBundle(),
                    createdAt,
                    expiresAt,
                    densityCalibrationReport,
                    unreachable,
                    unreachable,
                    List.of(),
                    toScenarioResults(evaluatedScenarios)
            );
        }

        InternalRoutePlan expectedPlan = objectivePlanner.compute(
                resolved.routeCore(),
                resolved.normalized().getInternalRequest(),
                scenarioExecution.scenarioCostSurfaces(),
                FutureRouteObjectivePlanner.ObjectiveMode.EXPECTED_ETA
        );
        InternalRoutePlan robustPlan = objectivePlanner.compute(
                resolved.routeCore(),
                resolved.normalized().getInternalRequest(),
                scenarioExecution.scenarioCostSurfaces(),
                FutureRouteObjectivePlanner.ObjectiveMode.ROBUST_P90
        );
        addAggregateCandidate(candidates, resolved.routeCore(), resolved.normalized(), expectedPlan);
        addAggregateCandidate(candidates, resolved.routeCore(), resolved.normalized(), robustPlan);

        ArrayList<ScoredCandidate> scoredCandidates = new ArrayList<>(candidates.size());
        for (CandidateRoute candidate : candidates) {
            scoredCandidates.add(scoreCandidate(candidate, evaluatedScenarios, resolved.normalized(), scenarioOptimalSignatures));
        }

        ScoredCandidate expectedRoute = resolveWinner(
                scoredCandidates,
                expectedPlan,
                Comparator
                        .comparingDouble((ScoredCandidate candidate) -> candidate.selection().getExpectedCost())
                        .thenComparingDouble(candidate -> candidate.selection().getP90Cost())
                        .thenComparing(ScoredCandidate::signature)
        );
        ScoredCandidate robustRoute = resolveWinner(
                scoredCandidates,
                robustPlan,
                Comparator
                        .comparingDouble((ScoredCandidate candidate) -> candidate.selection().getP90Cost())
                        .thenComparingDouble(candidate -> candidate.selection().getExpectedCost())
                        .thenComparing(ScoredCandidate::signature)
        );
        List<ScenarioRouteSelection> alternatives = selectAlternatives(
                scoredCandidates,
                resolved.request().getTopKAlternatives(),
                List.of(expectedRoute, robustRoute)
        );
        CandidateDensityCalibrationReport densityCalibrationReport = buildDensityCalibrationReport(
                resolved.scenarioBundle().getScenarios().size(),
                scenarioExecution.scenarioOptimalRouteCount(),
                scenarioOptimalSignatures.size(),
                candidates.size(),
                isAggregateOnlyWinner(expectedPlan, scenarioOptimalSignatures),
                isAggregateOnlyWinner(robustPlan, scenarioOptimalSignatures),
                alternatives.size()
        );

        return buildResultSet(
                resolved.request(),
                resolved.snapshot(),
                resolved.quarantineSnapshot(),
                resolved.scenarioBundle(),
                createdAt,
                expiresAt,
                densityCalibrationReport,
                expectedRoute.selection(),
                robustRoute.selection(),
                alternatives,
                toScenarioResults(evaluatedScenarios)
        );
    }

    private ResolvedRouteScenarioRequest resolveScenarioRequest(
            TopologyRuntimeSnapshot snapshot,
            FutureRouteRequest request
    ) {
        RouteCore routeCore = snapshot.getRouteCore();
        RequestNormalizer.NormalizedRouteRequest normalized = routeCore.normalizeRouteRequest(request.getRouteRequest());
        long departureTicks = normalized.getInternalRequest().departureTicks();
        FailureQuarantine.Snapshot quarantineSnapshot = snapshot.getFailureQuarantine().snapshot(departureTicks);
        ScenarioBundle scenarioBundle = scenarioBundleResolver.resolve(
                request,
                routeCore.costEngineContract(),
                routeCore.temporalContextContract(),
                snapshot.getTopologyVersion(),
                quarantineSnapshot,
                clock
        );
        validateScenarioBundle(scenarioBundle, snapshot, quarantineSnapshot);
        return new ResolvedRouteScenarioRequest(
                snapshot,
                request,
                routeCore,
                normalized,
                quarantineSnapshot,
                scenarioBundle
        );
    }

    private RouteScenarioExecution executeScenarios(
            RouteCore routeCore,
            RequestNormalizer.NormalizedRouteRequest normalized,
            ScenarioBundle scenarioBundle
    ) {
        long departureTicks = normalized.getInternalRequest().departureTicks();
        ArrayList<EvaluatedScenario> evaluatedScenarios = new ArrayList<>(scenarioBundle.getScenarios().size());
        ArrayList<FutureRouteObjectivePlanner.ScenarioCostSurface> scenarioCostSurfaces =
                new ArrayList<>(scenarioBundle.getScenarios().size());
        ArrayList<CandidateRoute> scenarioOptimalCandidates = new ArrayList<>();
        Set<String> scenarioOptimalSignatures = new HashSet<>();
        int scenarioOptimalRouteCount = 0;

        for (ScenarioDefinition scenario : scenarioBundle.getScenarios()) {
            CostEngine scenarioCostEngine = FutureScenarioSupport.buildScenarioCostEngine(
                    routeCore.costEngineContract(),
                    departureTicks,
                    scenario.getLiveUpdates()
            );
            InternalRoutePlan plan = routeCore.computeInternal(normalized.getInternalRequest(), scenarioCostEngine);
            RouteResponse response = routeCore.buildRouteResponse(normalized, plan);
            evaluatedScenarios.add(new EvaluatedScenario(scenario, scenarioCostEngine, plan, response));
            scenarioCostSurfaces.add(new FutureRouteObjectivePlanner.ScenarioCostSurface(
                    scenario.getProbability(),
                    scenarioCostEngine
            ));
            if (!plan.reachable()) {
                continue;
            }

            scenarioOptimalRouteCount++;
            RouteCandidateKey key = RouteCandidateKey.fromPlan(plan);
            scenarioOptimalSignatures.add(key.signature());
            if (findCandidate(scenarioOptimalCandidates, key) == null) {
                scenarioOptimalCandidates.add(new CandidateRoute(
                        key,
                        plan.edgePath(),
                        response,
                        scenario.getScenarioId(),
                        scenario.getLabel(),
                        scenario.getExplanationTags()
                ));
            }
        }

        return new RouteScenarioExecution(
                List.copyOf(evaluatedScenarios),
                List.copyOf(scenarioCostSurfaces),
                List.copyOf(scenarioOptimalCandidates),
                Set.copyOf(scenarioOptimalSignatures),
                scenarioOptimalRouteCount
        );
    }

    private ScenarioRouteSelection buildUnreachableSelection(RequestNormalizer.NormalizedRouteRequest normalized) {
        RouteResponse response = RouteResponse.builder()
                .reachable(false)
                .departureTicks(normalized.getInternalRequest().departureTicks())
                .arrivalTicks(normalized.getInternalRequest().departureTicks())
                .totalCost(Float.POSITIVE_INFINITY)
                .settledStates(0)
                .algorithm(normalized.getInternalRequest().algorithm())
                .heuristicType(normalized.getInternalRequest().heuristicType())
                .sourceResolvedAddress(normalized.getSourceResolvedAddress())
                .targetResolvedAddress(normalized.getTargetResolvedAddress())
                .build();
        return ScenarioRouteSelection.builder()
                .route(RouteShape.fromRouteResponse(response))
                .expectedCost(Float.POSITIVE_INFINITY)
                .p50Cost(Float.POSITIVE_INFINITY)
                .p90Cost(Float.POSITIVE_INFINITY)
                .minCost(Float.POSITIVE_INFINITY)
                .maxCost(Float.POSITIVE_INFINITY)
                .minArrivalTicks(Long.MAX_VALUE)
                .maxArrivalTicks(Long.MAX_VALUE)
                .optimalityProbability(0.0d)
                .expectedRegret(Float.POSITIVE_INFINITY)
                .etaBandLowerArrivalTicks(Long.MAX_VALUE)
                .etaBandUpperArrivalTicks(Long.MAX_VALUE)
                .dominantScenarioId("unreachable")
                .dominantScenarioProbability(0.0d)
                .dominantScenarioLabel("unreachable")
                .routeSelectionProvenance(RouteSelectionProvenance.UNREACHABLE)
                .build();
    }

    private List<FutureRouteScenarioResult> toScenarioResults(List<EvaluatedScenario> evaluatedScenarios) {
        ArrayList<FutureRouteScenarioResult> results = new ArrayList<>(evaluatedScenarios.size());
        for (EvaluatedScenario evaluatedScenario : evaluatedScenarios) {
            results.add(FutureRouteScenarioResult.builder()
                    .scenarioId(evaluatedScenario.scenario().getScenarioId())
                    .label(evaluatedScenario.scenario().getLabel())
                    .probability(evaluatedScenario.scenario().getProbability())
                    .route(evaluatedScenario.routeResponse())
                    .explanationTags(evaluatedScenario.scenario().getExplanationTags())
                    .build());
        }
        return List.copyOf(results);
    }

    private FutureRouteResultSet buildResultSet(
            FutureRouteRequest request,
            TopologyRuntimeSnapshot snapshot,
            FailureQuarantine.Snapshot quarantineSnapshot,
            ScenarioBundle scenarioBundle,
            Instant createdAt,
            Instant expiresAt,
            CandidateDensityCalibrationReport densityCalibrationReport,
            ScenarioRouteSelection expectedRoute,
            ScenarioRouteSelection robustRoute,
            List<ScenarioRouteSelection> alternatives,
            List<FutureRouteScenarioResult> scenarioResults
    ) {
        return FutureRouteResultSet.builder()
                .resultSetId(UUID.randomUUID().toString())
                .createdAt(createdAt)
                .expiresAt(expiresAt)
                .request(request)
                .topologyVersion(snapshot.getTopologyVersion())
                .quarantineSnapshotId(quarantineSnapshot.snapshotId())
                .scenarioBundle(scenarioBundle)
                .candidateDensityCalibrationReport(densityCalibrationReport)
                .expectedRoute(expectedRoute)
                .robustRoute(robustRoute)
                .alternatives(alternatives)
                .scenarioResults(scenarioResults)
                .build();
    }

    private CandidateDensityCalibrationReport buildDensityCalibrationReport(
            int scenarioCount,
            int scenarioOptimalRouteCount,
            int uniqueScenarioOptimalRouteCount,
            int uniqueCandidateRouteCount,
            boolean expectedRouteAggregateOnly,
            boolean robustRouteAggregateOnly,
            int selectedAlternativeCount
    ) {
        int aggregateAddedCandidateCount = Math.max(0, uniqueCandidateRouteCount - uniqueScenarioOptimalRouteCount);
        double scenarioCoverageRatio = scenarioOptimalRouteCount <= 0
                ? 0.0d
                : (double) uniqueScenarioOptimalRouteCount / (double) scenarioOptimalRouteCount;
        double candidateCoverageRatio = uniqueScenarioOptimalRouteCount <= 0
                ? (uniqueCandidateRouteCount == 0 ? 0.0d : Double.POSITIVE_INFINITY)
                : (double) uniqueCandidateRouteCount / (double) uniqueScenarioOptimalRouteCount;
        double aggregateExpansionRatio = uniqueScenarioOptimalRouteCount <= 0
                ? (aggregateAddedCandidateCount == 0 ? 0.0d : Double.POSITIVE_INFINITY)
                : (double) aggregateAddedCandidateCount / (double) uniqueScenarioOptimalRouteCount;
        if (!Double.isFinite(candidateCoverageRatio)) {
            candidateCoverageRatio = 0.0d;
        }
        if (!Double.isFinite(aggregateExpansionRatio)) {
            aggregateExpansionRatio = 0.0d;
        }
        return CandidateDensityCalibrationReport.builder()
                .policyId(DENSITY_CALIBRATION_POLICY_ID)
                .scenarioCount(scenarioCount)
                .scenarioOptimalRouteCount(scenarioOptimalRouteCount)
                .uniqueScenarioOptimalRouteCount(uniqueScenarioOptimalRouteCount)
                .uniqueCandidateRouteCount(uniqueCandidateRouteCount)
                .aggregateAddedCandidateCount(aggregateAddedCandidateCount)
                .expectedRouteAggregateOnly(expectedRouteAggregateOnly)
                .robustRouteAggregateOnly(robustRouteAggregateOnly)
                .selectedAlternativeCount(selectedAlternativeCount)
                .scenarioCoverageRatio(scenarioCoverageRatio)
                .candidateCoverageRatio(candidateCoverageRatio)
                .aggregateExpansionRatio(aggregateExpansionRatio)
                .densityClass(densityClass(
                        uniqueCandidateRouteCount,
                        aggregateAddedCandidateCount,
                        scenarioCoverageRatio
                ))
                .build();
    }

    private static CandidateDensityClass densityClass(
            int uniqueCandidateRouteCount,
            int aggregateAddedCandidateCount,
            double scenarioCoverageRatio
    ) {
        if (uniqueCandidateRouteCount <= 1) {
            return CandidateDensityClass.LOW_DENSITY;
        }
        if (aggregateAddedCandidateCount > 0) {
            return CandidateDensityClass.HIGH_DENSITY;
        }
        return scenarioCoverageRatio >= 0.75d
                ? CandidateDensityClass.HIGH_DENSITY
                : CandidateDensityClass.LOW_DENSITY;
    }

    private static boolean isAggregateOnlyWinner(InternalRoutePlan plan, Set<String> scenarioOptimalSignatures) {
        return plan.reachable() && !scenarioOptimalSignatures.contains(RouteCandidateKey.fromPlan(plan).signature());
    }

    private List<ScenarioRouteSelection> selectAlternatives(
            List<ScoredCandidate> scoredCandidates,
            int topKAlternatives,
            List<ScoredCandidate> seededCandidates
    ) {
        ArrayList<ScoredCandidate> ranked = new ArrayList<>(scoredCandidates);
        ranked.sort(Comparator
                .comparingDouble((ScoredCandidate candidate) -> -candidate.selection().getOptimalityProbability())
                .thenComparingDouble(candidate -> candidate.selection().getExpectedRegret())
                .thenComparingDouble(candidate -> candidate.selection().getExpectedCost())
                .thenComparingDouble(candidate -> candidate.selection().getP90Cost())
                .thenComparing(ScoredCandidate::signature));

        ArrayList<ScoredCandidate> chosen = new ArrayList<>();
        Set<String> seededSignatures = new HashSet<>();
        for (ScoredCandidate seeded : seededCandidates) {
            if (seeded == null || !seededSignatures.add(seeded.signature())) {
                continue;
            }
            if (chosen.size() >= topKAlternatives) {
                break;
            }
            if (isMateriallyDistinct(seeded, chosen)) {
                chosen.add(seeded);
            }
        }
        for (ScoredCandidate candidate : ranked) {
            if (chosen.size() >= topKAlternatives) {
                break;
            }
            if (isMateriallyDistinct(candidate, chosen)) {
                chosen.add(candidate);
            }
        }

        ArrayList<ScenarioRouteSelection> alternatives = new ArrayList<>(chosen.size());
        for (ScoredCandidate candidate : chosen) {
            alternatives.add(candidate.selection());
        }
        return List.copyOf(alternatives);
    }

    private boolean isMateriallyDistinct(ScoredCandidate candidate, List<ScoredCandidate> chosen) {
        for (ScoredCandidate existing : chosen) {
            double overlap = pathOverlap(candidate.edgePath(), existing.edgePath());
            float expectedCostDelta = Math.abs(candidate.selection().getExpectedCost() - existing.selection().getExpectedCost());
            if (overlap >= DISTINCT_PATH_OVERLAP_THRESHOLD
                    && expectedCostDelta <= DISTINCT_EXPECTED_COST_TOLERANCE) {
                return false;
            }
        }
        return true;
    }

    private double pathOverlap(int[] lhs, int[] rhs) {
        if (lhs.length == 0 && rhs.length == 0) {
            return 1.0d;
        }
        int intersection = 0;
        for (int lhsEdge : lhs) {
            for (int rhsEdge : rhs) {
                if (lhsEdge == rhsEdge) {
                    intersection++;
                    break;
                }
            }
        }
        int union = lhs.length + rhs.length - intersection;
        return union == 0 ? 1.0d : (double) intersection / union;
    }

    private ScoredCandidate scoreCandidate(
            CandidateRoute candidate,
            List<EvaluatedScenario> evaluatedScenarios,
            RequestNormalizer.NormalizedRouteRequest normalized,
            Set<String> scenarioOptimalSignatures
    ) {
        ArrayList<CandidateScenarioSample> samples = new ArrayList<>(evaluatedScenarios.size());
        String dominantScenarioId = candidate.dominantScenarioId();
        String dominantScenarioLabel = candidate.dominantScenarioLabel();
        double dominantProbability = -1.0d;
        double dominantScenarioProbability = 0.0d;
        double optimalityProbability = 0.0d;
        double weightedRegret = 0.0d;
        List<String> selectedExplanationTags = candidate.explanationTags();
        double bestFallbackProbability = -1.0d;
        double bestFallbackRegret = Double.POSITIVE_INFINITY;

        for (EvaluatedScenario evaluatedScenario : evaluatedScenarios) {
            boolean optimalForScenario = candidate.key().equals(RouteCandidateKey.fromPlan(evaluatedScenario.plan()));
            CandidateScenarioSample sample = evaluateCandidateInScenario(candidate, evaluatedScenario, normalized);
            samples.add(sample);
            double regret = scenarioRegret(sample, evaluatedScenario.plan());
            weightedRegret = weightedRegret(weightedRegret, sample.probability(), regret);
            if (optimalForScenario) {
                if (optimalityProbability == 0.0d) {
                    dominantProbability = -1.0d;
                }
                optimalityProbability += evaluatedScenario.scenario().getProbability();
                if (evaluatedScenario.scenario().getProbability() > dominantProbability) {
                    dominantProbability = evaluatedScenario.scenario().getProbability();
                    dominantScenarioProbability = evaluatedScenario.scenario().getProbability();
                    dominantScenarioId = evaluatedScenario.scenario().getScenarioId();
                    dominantScenarioLabel = evaluatedScenario.scenario().getLabel();
                    selectedExplanationTags = evaluatedScenario.scenario().getExplanationTags();
                }
            } else if (optimalityProbability == 0.0d) {
                double scenarioProbability = evaluatedScenario.scenario().getProbability();
                if (scenarioProbability > bestFallbackProbability + PROBABILITY_TOLERANCE) {
                    bestFallbackProbability = scenarioProbability;
                    bestFallbackRegret = regret;
                    dominantScenarioProbability = scenarioProbability;
                    dominantScenarioId = evaluatedScenario.scenario().getScenarioId();
                    dominantScenarioLabel = evaluatedScenario.scenario().getLabel();
                    selectedExplanationTags = evaluatedScenario.scenario().getExplanationTags();
                } else if (Math.abs(scenarioProbability - bestFallbackProbability) <= PROBABILITY_TOLERANCE
                        && regret < bestFallbackRegret) {
                    bestFallbackRegret = regret;
                    dominantScenarioProbability = scenarioProbability;
                    dominantScenarioId = evaluatedScenario.scenario().getScenarioId();
                    dominantScenarioLabel = evaluatedScenario.scenario().getLabel();
                    selectedExplanationTags = evaluatedScenario.scenario().getExplanationTags();
                }
            }
        }

        float expectedCost = expectedCost(samples);
        float p50Cost = percentileCost(samples, 0.50d);
        float p90Cost = percentileCost(samples, 0.90d);
        float minCost = minCost(samples);
        float maxCost = maxCost(samples);
        long minArrivalTicks = minArrival(samples);
        long maxArrivalTicks = maxArrival(samples);
        float expectedRegret = weightedRegret(weightedRegret);
        long etaBandLowerArrivalTicks = percentileArrival(samples, ETA_BAND_LOWER_PERCENTILE);
        long etaBandUpperArrivalTicks = percentileArrival(samples, ETA_BAND_UPPER_PERCENTILE);
        RouteSelectionProvenance routeSelectionProvenance = scenarioOptimalSignatures.contains(candidate.key().signature())
                ? RouteSelectionProvenance.SCENARIO_OPTIMAL
                : RouteSelectionProvenance.AGGREGATE_OBJECTIVE;

        ScenarioRouteSelection selection = ScenarioRouteSelection.builder()
                .route(RouteShape.fromRouteResponse(candidate.representativeResponse()))
                .expectedCost(expectedCost)
                .p50Cost(p50Cost)
                .p90Cost(p90Cost)
                .minCost(minCost)
                .maxCost(maxCost)
                .minArrivalTicks(minArrivalTicks)
                .maxArrivalTicks(maxArrivalTicks)
                .optimalityProbability(optimalityProbability)
                .expectedRegret(expectedRegret)
                .etaBandLowerArrivalTicks(etaBandLowerArrivalTicks)
                .etaBandUpperArrivalTicks(etaBandUpperArrivalTicks)
                .dominantScenarioId(dominantScenarioId)
                .dominantScenarioProbability(dominantScenarioProbability)
                .dominantScenarioLabel(dominantScenarioLabel)
                .routeSelectionProvenance(routeSelectionProvenance)
                .explanationTags(selectedExplanationTags)
                .build();
        return new ScoredCandidate(candidate.key().signature(), candidate.edgePath(), selection);
    }

    private double scenarioRegret(CandidateScenarioSample sample, InternalRoutePlan optimalPlan) {
        if (!sample.reachable()) {
            return Double.POSITIVE_INFINITY;
        }
        if (!optimalPlan.reachable()) {
            return sample.cost();
        }
        return Math.max(0.0d, sample.cost() - optimalPlan.totalCost());
    }

    private CandidateScenarioSample evaluateCandidateInScenario(
            CandidateRoute candidate,
            EvaluatedScenario evaluatedScenario,
            RequestNormalizer.NormalizedRouteRequest normalized
    ) {
        if (candidate.key().equals(RouteCandidateKey.fromPlan(evaluatedScenario.plan()))) {
            return new CandidateScenarioSample(
                    evaluatedScenario.scenario().getProbability(),
                    evaluatedScenario.plan().totalCost(),
                    evaluatedScenario.plan().arrivalTicks(),
                    true
            );
        }

        try {
            PathEvaluator.Evaluation evaluation = pathEvaluator.evaluateEdgePath(
                    evaluatedScenario.costEngine(),
                    candidate.edgePath(),
                    normalized.getInternalRequest().departureTicks(),
                    normalized.getInternalRequest().temporalContext(),
                    normalized.getInternalRequest().transitionContext()
            );
            return new CandidateScenarioSample(
                    evaluatedScenario.scenario().getProbability(),
                    evaluation.totalCost(),
                    evaluation.arrivalTicks(),
                    true
            );
        } catch (PathEvaluator.PathEvaluationException ex) {
            return new CandidateScenarioSample(
                    evaluatedScenario.scenario().getProbability(),
                    Float.POSITIVE_INFINITY,
                    Long.MAX_VALUE,
                    false
            );
        }
    }

    private float expectedCost(List<CandidateScenarioSample> samples) {
        double weighted = 0.0d;
        for (CandidateScenarioSample sample : samples) {
            if (!Float.isFinite(sample.cost()) && sample.probability() > 0.0d) {
                return Float.POSITIVE_INFINITY;
            }
            weighted += sample.probability() * sample.cost();
        }
        return weightedRegret(weighted);
    }

    private float percentileCost(List<CandidateScenarioSample> samples, double percentile) {
        ArrayList<CandidateScenarioSample> sorted = new ArrayList<>(samples);
        sorted.sort(Comparator.comparingDouble(CandidateScenarioSample::cost));

        double totalProbability = 0.0d;
        for (CandidateScenarioSample sample : sorted) {
            totalProbability += sample.probability();
        }
        double threshold = percentile * totalProbability;
        double cumulative = 0.0d;
        for (CandidateScenarioSample sample : sorted) {
            cumulative += sample.probability();
            if (cumulative + PROBABILITY_TOLERANCE >= threshold) {
                return sample.cost();
            }
        }
        return sorted.get(sorted.size() - 1).cost();
    }

    private long percentileArrival(List<CandidateScenarioSample> samples, double percentile) {
        ArrayList<CandidateScenarioSample> sorted = new ArrayList<>(samples);
        sorted.sort(Comparator.comparingLong(CandidateScenarioSample::arrivalTicks));

        double totalProbability = 0.0d;
        for (CandidateScenarioSample sample : sorted) {
            totalProbability += sample.probability();
        }
        double threshold = percentile * totalProbability;
        double cumulative = 0.0d;
        for (CandidateScenarioSample sample : sorted) {
            cumulative += sample.probability();
            if (cumulative + PROBABILITY_TOLERANCE >= threshold) {
                return sample.arrivalTicks();
            }
        }
        return sorted.get(sorted.size() - 1).arrivalTicks();
    }

    private float minCost(List<CandidateScenarioSample> samples) {
        float min = Float.POSITIVE_INFINITY;
        for (CandidateScenarioSample sample : samples) {
            min = Math.min(min, sample.cost());
        }
        return min;
    }

    private float maxCost(List<CandidateScenarioSample> samples) {
        float max = 0.0f;
        for (CandidateScenarioSample sample : samples) {
            if (!Float.isFinite(sample.cost())) {
                return Float.POSITIVE_INFINITY;
            }
            max = Math.max(max, sample.cost());
        }
        return max;
    }

    private long minArrival(List<CandidateScenarioSample> samples) {
        long min = Long.MAX_VALUE;
        for (CandidateScenarioSample sample : samples) {
            if (sample.reachable()) {
                min = Math.min(min, sample.arrivalTicks());
            }
        }
        return min;
    }

    private long maxArrival(List<CandidateScenarioSample> samples) {
        long max = 0L;
        for (CandidateScenarioSample sample : samples) {
            if (!sample.reachable()) {
                return Long.MAX_VALUE;
            }
            max = Math.max(max, sample.arrivalTicks());
        }
        return max;
    }

    private double weightedRegret(double currentWeightedRegret, double probability, double regret) {
        if (probability <= 0.0d) {
            return currentWeightedRegret;
        }
        if (!Double.isFinite(regret)) {
            return Double.POSITIVE_INFINITY;
        }
        return currentWeightedRegret + (probability * regret);
    }

    private float weightedRegret(double weightedRegret) {
        if (!Double.isFinite(weightedRegret) || weightedRegret > Float.MAX_VALUE) {
            return Float.POSITIVE_INFINITY;
        }
        return (float) weightedRegret;
    }

    private void validateRequest(FutureRouteRequest request) {
        if (request.getRouteRequest() == null) {
            throw new IllegalArgumentException("future-aware route request must include routeRequest");
        }
        if (request.getHorizonTicks() <= 0L) {
            throw new IllegalArgumentException("horizonTicks must be > 0");
        }
        if (request.getTopKAlternatives() <= 0) {
            throw new IllegalArgumentException("topKAlternatives must be > 0");
        }
        if (request.getResultTtl() == null || request.getResultTtl().isZero() || request.getResultTtl().isNegative()) {
            throw new IllegalArgumentException("resultTtl must be > 0");
        }
    }

    private void validateScenarioBundle(
            ScenarioBundle bundle,
            TopologyRuntimeSnapshot snapshot,
            FailureQuarantine.Snapshot quarantineSnapshot
    ) {
        FutureScenarioSupport.validateScenarioBundle(bundle, snapshot, quarantineSnapshot);
    }

    private CandidateRoute findCandidate(List<CandidateRoute> candidates, RouteCandidateKey key) {
        for (CandidateRoute candidate : candidates) {
            if (candidate.key().equals(key)) {
                return candidate;
            }
        }
        return null;
    }

    private void addAggregateCandidate(
            List<CandidateRoute> candidates,
            RouteCore routeCore,
            RequestNormalizer.NormalizedRouteRequest normalized,
            InternalRoutePlan plan
    ) {
        if (!plan.reachable()) {
            return;
        }
        RouteCandidateKey key = RouteCandidateKey.fromPlan(plan);
        if (findCandidate(candidates, key) != null) {
            return;
        }
        RouteResponse response = routeCore.buildRouteResponse(normalized, plan);
        candidates.add(new CandidateRoute(
                key,
                plan.edgePath(),
                response,
                "",
                "",
                List.of()
        ));
    }

    private ScoredCandidate resolveWinner(
            List<ScoredCandidate> scoredCandidates,
            InternalRoutePlan plan,
            Comparator<ScoredCandidate> fallbackComparator
    ) {
        if (plan.reachable()) {
            RouteCandidateKey key = RouteCandidateKey.fromPlan(plan);
            for (ScoredCandidate scoredCandidate : scoredCandidates) {
                if (Objects.equals(scoredCandidate.signature(), key.signature())) {
                    return scoredCandidate;
                }
            }
        }
        return scoredCandidates.stream().min(fallbackComparator).orElseThrow();
    }

    private record EvaluatedScenario(
            ScenarioDefinition scenario,
            CostEngine costEngine,
            InternalRoutePlan plan,
            RouteResponse routeResponse
    ) {
    }

    private record ResolvedRouteScenarioRequest(
            TopologyRuntimeSnapshot snapshot,
            FutureRouteRequest request,
            RouteCore routeCore,
            RequestNormalizer.NormalizedRouteRequest normalized,
            FailureQuarantine.Snapshot quarantineSnapshot,
            ScenarioBundle scenarioBundle
    ) {
    }

    private record RouteScenarioExecution(
            List<EvaluatedScenario> evaluatedScenarios,
            List<FutureRouteObjectivePlanner.ScenarioCostSurface> scenarioCostSurfaces,
            List<CandidateRoute> scenarioOptimalCandidates,
            Set<String> scenarioOptimalSignatures,
            int scenarioOptimalRouteCount
    ) {
    }

    private record CandidateRoute(
            RouteCandidateKey key,
            int[] edgePath,
            RouteResponse representativeResponse,
            String dominantScenarioId,
            String dominantScenarioLabel,
            List<String> explanationTags
    ) {
    }

    private record CandidateScenarioSample(
            double probability,
            float cost,
            long arrivalTicks,
            boolean reachable
    ) {
    }

    private record ScoredCandidate(
            String signature,
            int[] edgePath,
            ScenarioRouteSelection selection
    ) {
    }

    private record RouteCandidateKey(String signature) {
        static RouteCandidateKey fromPlan(InternalRoutePlan plan) {
            int[] primaryPath = plan.edgePath().length == 0 ? plan.nodePath() : plan.edgePath();
            String prefix = plan.edgePath().length == 0 ? "N" : "E";
            return new RouteCandidateKey(prefix + ":" + Arrays.toString(primaryPath));
        }
    }
}
