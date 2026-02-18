package org.Aayush.routing.core;

import lombok.Builder;
import org.Aayush.core.id.IDMapper;
import org.Aayush.routing.cost.CostEngine;
import org.Aayush.routing.graph.EdgeGraph;
import org.Aayush.routing.heuristic.GoalBoundHeuristic;
import org.Aayush.routing.heuristic.HeuristicConfigurationException;
import org.Aayush.routing.heuristic.HeuristicFactory;
import org.Aayush.routing.heuristic.HeuristicProvider;
import org.Aayush.routing.heuristic.HeuristicType;
import org.Aayush.routing.heuristic.LandmarkStore;
import org.Aayush.routing.profile.ProfileStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Main routing orchestration entry point.
 *
 * <p>The facade owns runtime contracts for graph/profile/cost-engine identity and
 * applies deterministic request validation before any search starts. Execution flow:</p>
 * <ul>
 * <li>Normalize client payloads from external node ids to internal node ids.</li>
 * <li>Validate algorithm/heuristic compatibility and required fields.</li>
 * <li>Resolve or lazily build heuristic providers with strict contract checks.</li>
 * <li>Delegate to point-to-point or matrix planners.</li>
 * <li>Wrap planner guardrail exceptions into {@link RouteCoreException} with stable reason codes.</li>
 * <li>Map internal node paths and matrices back to external response payloads.</li>
 * </ul>
 */
public final class RouteCore implements RouterService {
    public static final String REASON_ROUTE_REQUEST_REQUIRED = "H12_ROUTE_REQUEST_REQUIRED";
    public static final String REASON_MATRIX_REQUEST_REQUIRED = "H12_MATRIX_REQUEST_REQUIRED";
    public static final String REASON_SOURCE_EXTERNAL_ID_REQUIRED = "H12_SOURCE_EXTERNAL_ID_REQUIRED";
    public static final String REASON_TARGET_EXTERNAL_ID_REQUIRED = "H12_TARGET_EXTERNAL_ID_REQUIRED";
    public static final String REASON_ALGORITHM_REQUIRED = "H12_ALGORITHM_REQUIRED";
    public static final String REASON_HEURISTIC_REQUIRED = "H12_HEURISTIC_REQUIRED";
    public static final String REASON_UNKNOWN_EXTERNAL_NODE = "H12_UNKNOWN_EXTERNAL_NODE";
    public static final String REASON_INTERNAL_NODE_OUT_OF_BOUNDS = "H12_INTERNAL_NODE_OUT_OF_BOUNDS";
    public static final String REASON_DIJKSTRA_HEURISTIC_MISMATCH = "H12_DIJKSTRA_HEURISTIC_MISMATCH";
    public static final String REASON_HEURISTIC_CONFIGURATION_FAILED = "H12_HEURISTIC_CONFIGURATION_FAILED";
    public static final String REASON_HEURISTIC_BIND_FAILED = "H12_HEURISTIC_BIND_FAILED";
    public static final String REASON_SOURCE_LIST_REQUIRED = "H12_SOURCE_LIST_REQUIRED";
    public static final String REASON_TARGET_LIST_REQUIRED = "H12_TARGET_LIST_REQUIRED";
    public static final String REASON_COST_ENGINE_GRAPH_MISMATCH = "H12_COST_ENGINE_GRAPH_MISMATCH";
    public static final String REASON_COST_ENGINE_PROFILE_MISMATCH = "H12_COST_ENGINE_PROFILE_MISMATCH";
    public static final String REASON_EXTERNAL_MAPPING_FAILED = "H12_EXTERNAL_MAPPING_FAILED";
    public static final String REASON_SEARCH_BUDGET_EXCEEDED = "H13_SEARCH_BUDGET_EXCEEDED";
    public static final String REASON_NUMERIC_SAFETY_BREACH = "H13_NUMERIC_SAFETY_BREACH";
    public static final String REASON_PATH_EVALUATION_FAILED = "H13_PATH_EVALUATION_FAILED";
    public static final String REASON_MATRIX_SEARCH_BUDGET_EXCEEDED = "H14_MATRIX_SEARCH_BUDGET_EXCEEDED";
    public static final String REASON_MATRIX_NUMERIC_SAFETY_BREACH = "H14_MATRIX_NUMERIC_SAFETY_BREACH";

    private static final GoalBoundHeuristic ZERO_HEURISTIC = nodeId -> 0.0d;

    private final EdgeGraph edgeGraph;
    private final ProfileStore profileStore;
    private final CostEngine costEngine;
    private final IDMapper nodeIdMapper;
    private final LandmarkStore landmarkStore;

    private final RoutePlanner dijkstraPlanner;
    private final RoutePlanner aStarPlanner;
    private final MatrixPlanner matrixPlanner;
    private final ThreadLocal<MatrixExecutionStats> matrixExecutionStats =
            ThreadLocal.withInitial(() -> MatrixExecutionStats.empty(0));
    private final ConcurrentMap<HeuristicType, HeuristicProvider> heuristicProviders = new ConcurrentHashMap<>();

    /**
     * Creates the route-core facade with strict runtime contract validation.
     *
     * @param edgeGraph edge-based graph runtime.
     * @param profileStore temporal profile runtime.
     * @param costEngine time-dependent cost engine bound to graph/profile.
     * @param nodeIdMapper external-to-internal node id mapper.
     * @param landmarkStore optional landmark runtime (required for LANDMARK heuristic).
     * @param matrixPlanner optional matrix planner override.
     */
    @Builder
    public RouteCore(
            EdgeGraph edgeGraph,
            ProfileStore profileStore,
            CostEngine costEngine,
            IDMapper nodeIdMapper,
            LandmarkStore landmarkStore,
            MatrixPlanner matrixPlanner,
            RoutePlanner aStarPlanner
    ) {
        this.edgeGraph = Objects.requireNonNull(edgeGraph, "edgeGraph");
        this.profileStore = Objects.requireNonNull(profileStore, "profileStore");
        this.costEngine = Objects.requireNonNull(costEngine, "costEngine");
        this.nodeIdMapper = Objects.requireNonNull(nodeIdMapper, "nodeIdMapper");
        this.landmarkStore = landmarkStore;
        this.matrixPlanner = matrixPlanner == null ? new OneToManyDijkstraMatrixPlanner() : matrixPlanner;
        this.dijkstraPlanner = new EdgeBasedRoutePlanner(false);

        ensureCostEngineContracts();
        this.aStarPlanner = aStarPlanner == null
                ? new BidirectionalTdAStarPlanner(edgeGraph, costEngine)
                : aStarPlanner;
        heuristicProviders.put(
                HeuristicType.NONE,
                HeuristicFactory.create(HeuristicType.NONE, edgeGraph, profileStore, costEngine, landmarkStore)
        );
    }

    /**
     * Executes one client point-to-point request.
     *
     * @param request route request in external-id space.
     * @return response containing reachability, path, cost, and planner metadata.
     * @throws RouteCoreException when request contracts or planner guardrails fail.
     */
    @Override
    public RouteResponse route(RouteRequest request) {
        InternalRouteRequest internalRequest = toInternalRouteRequest(request);
        InternalRoutePlan plan = computeInternal(internalRequest);

        RouteResponse.RouteResponseBuilder builder = RouteResponse.builder()
                .reachable(plan.reachable())
                .departureTicks(internalRequest.departureTicks())
                .arrivalTicks(plan.arrivalTicks())
                .totalCost(plan.totalCost())
                .settledStates(plan.settledStates())
                .algorithm(internalRequest.algorithm())
                .heuristicType(internalRequest.heuristicType());

        if (plan.reachable()) {
            for (String node : toExternalNodePath(plan.nodePath())) {
                builder.pathNode(node);
            }
        }

        return builder.build();
    }

    /**
     * Executes one client many-to-many matrix request.
     *
     * @param request matrix request in external-id space.
     * @return matrix response with row/column mapping, costs, arrivals, and execution note.
     * @throws RouteCoreException when request contracts or matrix guardrails fail.
     */
    @Override
    public MatrixResponse matrix(MatrixRequest request) {
        InternalMatrixRequest internalRequest = toInternalMatrixRequest(request);
        try {
            MatrixPlan matrixPlan = matrixPlanner.compute(this, internalRequest);
            matrixExecutionStats.set(matrixPlan.executionStats());
            return MatrixResponse.builder()
                    .sourceExternalIds(List.copyOf(request.getSourceExternalIds()))
                    .targetExternalIds(List.copyOf(request.getTargetExternalIds()))
                    .reachable(copy(matrixPlan.reachable()))
                    .totalCosts(copy(matrixPlan.totalCosts()))
                    .arrivalTicks(copy(matrixPlan.arrivalTicks()))
                    .algorithm(internalRequest.algorithm())
                    .heuristicType(internalRequest.heuristicType())
                    .implementationNote(matrixPlan.implementationNote())
                    .build();
        } catch (MatrixSearchBudget.BudgetExceededException ex) {
            throw new RouteCoreException(
                    REASON_MATRIX_SEARCH_BUDGET_EXCEEDED,
                    ex.reasonCode() + ": " + ex.getMessage(),
                    ex
            );
        } catch (TerminationPolicy.NumericSafetyException ex) {
            throw new RouteCoreException(
                    REASON_MATRIX_NUMERIC_SAFETY_BREACH,
                    ex.reasonCode() + ": " + ex.getMessage(),
                    ex
            );
        }
    }

    /**
     * Returns telemetry from the most recent matrix call on the current thread.
     *
     * <p>Matrix execution is thread-confined through thread-local planner contexts, so
     * telemetry is also exposed per-thread to avoid cross-request contamination.</p>
     */
    MatrixExecutionStats matrixExecutionStatsContract() {
        return matrixExecutionStats.get();
    }

    /**
     * Computes one normalized internal request using the algorithm selected in the request.
     *
     * <p>All planner-specific guardrail exceptions are normalized to route-core reason codes
     * to keep caller error handling stable even when planner internals evolve.</p>
     */
    InternalRoutePlan computeInternal(InternalRouteRequest request) {
        GoalBoundHeuristic heuristic = resolveGoalBoundHeuristic(request);
        RoutePlanner planner = switch (request.algorithm()) {
            case DIJKSTRA -> dijkstraPlanner;
            case A_STAR -> aStarPlanner;
        };
        try {
            return planner.compute(edgeGraph, costEngine, heuristic, request);
        } catch (SearchBudget.BudgetExceededException ex) {
            throw new RouteCoreException(
                    REASON_SEARCH_BUDGET_EXCEEDED,
                    ex.reasonCode() + ": " + ex.getMessage(),
                    ex
            );
        } catch (TerminationPolicy.NumericSafetyException ex) {
            throw new RouteCoreException(
                    REASON_NUMERIC_SAFETY_BREACH,
                    ex.reasonCode() + ": " + ex.getMessage(),
                    ex
            );
        } catch (PathEvaluator.PathEvaluationException ex) {
            throw new RouteCoreException(
                    REASON_PATH_EVALUATION_FAILED,
                    ex.reasonCode() + ": " + ex.getMessage(),
                    ex
            );
        }
    }

    /**
     * Exposes the graph runtime contract used by planners.
     */
    EdgeGraph edgeGraphContract() {
        return edgeGraph;
    }

    /**
     * Exposes the cost-engine runtime contract used by planners.
     */
    CostEngine costEngineContract() {
        return costEngine;
    }

    /**
     * Resolves a goal-bound heuristic for the current request.
     *
     * <p>Dijkstra bypasses provider creation and always uses a zero estimate, guaranteeing
     * exact uniform-cost semantics regardless of requested heuristic type.</p>
     */
    private GoalBoundHeuristic resolveGoalBoundHeuristic(InternalRouteRequest request) {
        if (request.algorithm() == RoutingAlgorithm.DIJKSTRA) {
            return ZERO_HEURISTIC;
        }
        HeuristicProvider provider = heuristicProviders.computeIfAbsent(request.heuristicType(), this::buildHeuristicProvider);
        try {
            return provider.bindGoal(request.targetNodeId());
        } catch (RuntimeException ex) {
            throw new RouteCoreException(
                    REASON_HEURISTIC_BIND_FAILED,
                    "failed to bind heuristic " + request.heuristicType() + " to goal node " + request.targetNodeId(),
                    ex
            );
        }
    }

    /**
     * Builds and validates a heuristic provider for one heuristic mode.
     *
     * <p>Provider construction is centralized so all planner calls share identical
     * graph/profile/cost contracts.</p>
     */
    private HeuristicProvider buildHeuristicProvider(HeuristicType heuristicType) {
        try {
            return HeuristicFactory.create(heuristicType, edgeGraph, profileStore, costEngine, landmarkStore);
        } catch (HeuristicConfigurationException ex) {
            throw new RouteCoreException(
                    REASON_HEURISTIC_CONFIGURATION_FAILED,
                    "failed to initialize heuristic " + heuristicType + ": " + ex.getMessage(),
                    ex
            );
        }
    }

    /**
     * Normalizes and validates a route request into internal-node form.
     *
     * <p>This conversion enforces all external request invariants before planner invocation.</p>
     */
    private InternalRouteRequest toInternalRouteRequest(RouteRequest request) {
        if (request == null) {
            throw new RouteCoreException(REASON_ROUTE_REQUEST_REQUIRED, "route request must be provided");
        }
        RoutingAlgorithm algorithm = requireAlgorithm(request.getAlgorithm());
        HeuristicType heuristicType = requireHeuristicType(request.getHeuristicType());
        validateAlgorithmHeuristicPair(algorithm, heuristicType);

        int sourceNodeId = toInternalNodeId(request.getSourceExternalId(), REASON_SOURCE_EXTERNAL_ID_REQUIRED, "sourceExternalId");
        int targetNodeId = toInternalNodeId(request.getTargetExternalId(), REASON_TARGET_EXTERNAL_ID_REQUIRED, "targetExternalId");
        return new InternalRouteRequest(
                sourceNodeId,
                targetNodeId,
                request.getDepartureTicks(),
                algorithm,
                heuristicType
        );
    }

    /**
     * Normalizes and validates a matrix request into internal-node form.
     *
     * <p>Both source and target lists must be non-empty and every external id must map to
     * an in-range internal node id.</p>
     */
    private InternalMatrixRequest toInternalMatrixRequest(MatrixRequest request) {
        if (request == null) {
            throw new RouteCoreException(REASON_MATRIX_REQUEST_REQUIRED, "matrix request must be provided");
        }
        RoutingAlgorithm algorithm = requireAlgorithm(request.getAlgorithm());
        HeuristicType heuristicType = requireHeuristicType(request.getHeuristicType());
        validateAlgorithmHeuristicPair(algorithm, heuristicType);

        List<String> sourceIds = request.getSourceExternalIds();
        if (sourceIds == null || sourceIds.isEmpty()) {
            throw new RouteCoreException(REASON_SOURCE_LIST_REQUIRED, "sourceExternalIds must be non-empty");
        }
        List<String> targetIds = request.getTargetExternalIds();
        if (targetIds == null || targetIds.isEmpty()) {
            throw new RouteCoreException(REASON_TARGET_LIST_REQUIRED, "targetExternalIds must be non-empty");
        }

        int[] sourceNodeIds = new int[sourceIds.size()];
        for (int i = 0; i < sourceIds.size(); i++) {
            sourceNodeIds[i] = toInternalNodeId(sourceIds.get(i), REASON_SOURCE_EXTERNAL_ID_REQUIRED, "sourceExternalIds[" + i + "]");
        }

        int[] targetNodeIds = new int[targetIds.size()];
        for (int i = 0; i < targetIds.size(); i++) {
            targetNodeIds[i] = toInternalNodeId(targetIds.get(i), REASON_TARGET_EXTERNAL_ID_REQUIRED, "targetExternalIds[" + i + "]");
        }

        return new InternalMatrixRequest(sourceNodeIds, targetNodeIds, request.getDepartureTicks(), algorithm, heuristicType);
    }

    /**
     * Resolves one external node id to an internal node id with bounds validation.
     */
    private int toInternalNodeId(String externalId, String requiredReasonCode, String fieldName) {
        if (externalId == null || externalId.isBlank()) {
            throw new RouteCoreException(requiredReasonCode, fieldName + " must be non-blank");
        }
        final int internalNodeId;
        try {
            internalNodeId = nodeIdMapper.toInternal(externalId);
        } catch (IDMapper.UnknownIDException ex) {
            throw new RouteCoreException(
                    REASON_UNKNOWN_EXTERNAL_NODE,
                    "unknown external node id: " + externalId,
                    ex
            );
        }
        if (internalNodeId < 0 || internalNodeId >= edgeGraph.nodeCount()) {
            throw new RouteCoreException(
                    REASON_INTERNAL_NODE_OUT_OF_BOUNDS,
                    "mapped internal node id out of range for " + externalId + ": " + internalNodeId
            );
        }
        return internalNodeId;
    }

    /**
     * Maps an internal node path to an immutable external-id path.
     */
    private List<String> toExternalNodePath(int[] nodePath) {
        List<String> externalPath = new ArrayList<>(nodePath.length);
        for (int nodeId : nodePath) {
            try {
                externalPath.add(nodeIdMapper.toExternal(nodeId));
            } catch (RuntimeException ex) {
                throw new RouteCoreException(
                        REASON_EXTERNAL_MAPPING_FAILED,
                        "failed to map internal node " + nodeId + " to external id",
                        ex
                );
            }
        }
        return List.copyOf(externalPath);
    }

    /**
     * Validates required algorithm field.
     */
    private RoutingAlgorithm requireAlgorithm(RoutingAlgorithm algorithm) {
        if (algorithm == null) {
            throw new RouteCoreException(REASON_ALGORITHM_REQUIRED, "algorithm must be specified");
        }
        return algorithm;
    }

    /**
     * Validates required heuristic-type field.
     */
    private HeuristicType requireHeuristicType(HeuristicType heuristicType) {
        if (heuristicType == null) {
            throw new RouteCoreException(REASON_HEURISTIC_REQUIRED, "heuristicType must be specified");
        }
        return heuristicType;
    }

    /**
     * Enforces algorithm/heuristic compatibility contract.
     */
    private void validateAlgorithmHeuristicPair(RoutingAlgorithm algorithm, HeuristicType heuristicType) {
        if (algorithm == RoutingAlgorithm.DIJKSTRA && heuristicType != HeuristicType.NONE) {
            throw new RouteCoreException(
                    REASON_DIJKSTRA_HEURISTIC_MISMATCH,
                    "DIJKSTRA requires heuristicType NONE, got " + heuristicType
            );
        }
    }

    /**
     * Validates cost-engine identity contracts against this facade runtime.
     *
     * <p>Identity equality is required to prevent mixing unrelated graph/profile instances
     * in one execution context.</p>
     */
    private void ensureCostEngineContracts() {
        if (costEngine.edgeGraph() != edgeGraph) {
            throw new RouteCoreException(
                    REASON_COST_ENGINE_GRAPH_MISMATCH,
                    "costEngine.edgeGraph does not match route core edgeGraph"
            );
        }
        if (costEngine.profileStore() != profileStore) {
            throw new RouteCoreException(
                    REASON_COST_ENGINE_PROFILE_MISMATCH,
                    "costEngine.profileStore does not match route core profileStore"
            );
        }
    }

    /**
     * Deep-copies a boolean matrix.
     */
    private static boolean[][] copy(boolean[][] source) {
        boolean[][] copy = new boolean[source.length][];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i].clone();
        }
        return copy;
    }

    /**
     * Deep-copies a float matrix.
     */
    private static float[][] copy(float[][] source) {
        float[][] copy = new float[source.length][];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i].clone();
        }
        return copy;
    }

    /**
     * Deep-copies a long matrix.
     */
    private static long[][] copy(long[][] source) {
        long[][] copy = new long[source.length][];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i].clone();
        }
        return copy;
    }
}
