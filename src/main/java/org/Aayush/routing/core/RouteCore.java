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
import org.Aayush.routing.spatial.SpatialRuntime;
import org.Aayush.routing.traits.addressing.AddressingPolicy;
import org.Aayush.routing.traits.addressing.AddressingTelemetry;
import org.Aayush.routing.traits.addressing.AddressingTraitCatalog;
import org.Aayush.routing.traits.addressing.AddressingTraitEngine;
import org.Aayush.routing.traits.addressing.CoordinateStrategyRegistry;

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
 * <li>Normalize client payloads from external/typed addressing to internal node ids.</li>
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

    public static final String REASON_ADDRESSING_TRAIT_REQUIRED = "H15_ADDRESSING_TRAIT_REQUIRED";
    public static final String REASON_UNKNOWN_ADDRESSING_TRAIT = "H15_UNKNOWN_ADDRESSING_TRAIT";
    public static final String REASON_UNSUPPORTED_ADDRESS_TYPE = "H15_UNSUPPORTED_ADDRESS_TYPE";
    public static final String REASON_MALFORMED_TYPED_PAYLOAD = "H15_MALFORMED_TYPED_PAYLOAD";
    public static final String REASON_NON_FINITE_COORDINATES = "H15_NON_FINITE_COORDINATES";
    public static final String REASON_LAT_LON_RANGE = "H15_LAT_LON_RANGE";
    public static final String REASON_COORDINATE_STRATEGY_REQUIRED = "H15_COORDINATE_STRATEGY_REQUIRED";
    public static final String REASON_UNKNOWN_COORDINATE_STRATEGY = "H15_UNKNOWN_COORDINATE_STRATEGY";
    public static final String REASON_UNKNOWN_TYPED_EXTERNAL_NODE = "H15_UNKNOWN_TYPED_EXTERNAL_NODE";
    public static final String REASON_COORDINATE_STRATEGY_FAILURE = "H15_COORDINATE_STRATEGY_FAILURE";
    public static final String REASON_SPATIAL_RUNTIME_UNAVAILABLE = "H15_SPATIAL_RUNTIME_UNAVAILABLE";
    public static final String REASON_SNAP_THRESHOLD_EXCEEDED = "H15_SNAP_THRESHOLD_EXCEEDED";
    public static final String REASON_MIXED_MODE_DISABLED = "H15_MIXED_MODE_DISABLED";
    public static final String REASON_TYPED_LEGACY_AMBIGUITY = "H15_TYPED_LEGACY_AMBIGUITY";
    public static final String REASON_INVALID_MAX_SNAP_DISTANCE = "H15_INVALID_MAX_SNAP_DISTANCE";

    private static final GoalBoundHeuristic ZERO_HEURISTIC = nodeId -> 0.0d;

    private final EdgeGraph edgeGraph;
    private final ProfileStore profileStore;
    private final CostEngine costEngine;
    private final IDMapper nodeIdMapper;
    private final LandmarkStore landmarkStore;

    private final RoutePlanner dijkstraPlanner;
    private final RoutePlanner aStarPlanner;
    private final MatrixPlanner matrixPlanner;

    private final SpatialRuntime spatialRuntime;
    private final AddressingTraitEngine addressingTraitEngine;
    private final AddressingTraitCatalog addressingTraitCatalog;
    private final CoordinateStrategyRegistry coordinateStrategyRegistry;
    private final AddressingPolicy addressingPolicy;

    private final ThreadLocal<MatrixExecutionStats> matrixExecutionStats =
            ThreadLocal.withInitial(() -> MatrixExecutionStats.empty(0));
    private final ThreadLocal<AddressingTelemetry> addressingTelemetry =
            ThreadLocal.withInitial(AddressingTelemetry::empty);
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
            RoutePlanner aStarPlanner,
            SpatialRuntime spatialRuntime,
            AddressingTraitEngine addressingTraitEngine,
            AddressingTraitCatalog addressingTraitCatalog,
            CoordinateStrategyRegistry coordinateStrategyRegistry,
            AddressingPolicy addressingPolicy
    ) {
        this.edgeGraph = Objects.requireNonNull(edgeGraph, "edgeGraph");
        this.profileStore = Objects.requireNonNull(profileStore, "profileStore");
        this.costEngine = Objects.requireNonNull(costEngine, "costEngine");
        this.nodeIdMapper = Objects.requireNonNull(nodeIdMapper, "nodeIdMapper");
        this.landmarkStore = landmarkStore;
        this.matrixPlanner = matrixPlanner == null ? new OneToManyDijkstraMatrixPlanner() : matrixPlanner;
        this.dijkstraPlanner = new EdgeBasedRoutePlanner(false);

        this.spatialRuntime = spatialRuntime;
        this.addressingTraitEngine = addressingTraitEngine == null ? new AddressingTraitEngine() : addressingTraitEngine;
        this.addressingTraitCatalog = addressingTraitCatalog == null
                ? AddressingTraitCatalog.defaultCatalog()
                : addressingTraitCatalog;
        this.coordinateStrategyRegistry = coordinateStrategyRegistry == null
                ? CoordinateStrategyRegistry.defaultRegistry()
                : coordinateStrategyRegistry;
        this.addressingPolicy = addressingPolicy == null ? AddressingPolicy.defaults() : addressingPolicy;

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
     * @param request route request in external-id or typed addressing space.
     * @return response containing reachability, path, cost, planner metadata, and resolved endpoints.
     * @throws RouteCoreException when request contracts or planner guardrails fail.
     */
    @Override
    public RouteResponse route(RouteRequest request) {
        addressingTelemetry.set(AddressingTelemetry.empty());
        NormalizedRouteRequest normalized = normalizeRouteRequest(request);
        addressingTelemetry.set(normalized.addressingTelemetry());

        InternalRouteRequest internalRequest = normalized.internalRequest();
        InternalRoutePlan plan = computeInternal(internalRequest);

        RouteResponse.RouteResponseBuilder builder = RouteResponse.builder()
                .reachable(plan.reachable())
                .departureTicks(internalRequest.departureTicks())
                .arrivalTicks(plan.arrivalTicks())
                .totalCost(plan.totalCost())
                .settledStates(plan.settledStates())
                .algorithm(internalRequest.algorithm())
                .heuristicType(internalRequest.heuristicType())
                .sourceResolvedAddress(normalized.sourceResolvedAddress())
                .targetResolvedAddress(normalized.targetResolvedAddress());

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
     * @param request matrix request in external-id or typed addressing space.
     * @return matrix response with row/column mapping, costs, arrivals, and execution note.
     * @throws RouteCoreException when request contracts or matrix guardrails fail.
     */
    @Override
    public MatrixResponse matrix(MatrixRequest request) {
        addressingTelemetry.set(AddressingTelemetry.empty());
        NormalizedMatrixRequest normalized = normalizeMatrixRequest(request);
        addressingTelemetry.set(normalized.addressingTelemetry());

        InternalMatrixRequest internalRequest = normalized.internalRequest();
        try {
            MatrixPlan matrixPlan = matrixPlanner.compute(this, internalRequest);
            matrixExecutionStats.set(matrixPlan.executionStats());
            return MatrixResponse.builder()
                    .sourceExternalIds(normalized.sourceExternalIds())
                    .targetExternalIds(normalized.targetExternalIds())
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
     */
    MatrixExecutionStats matrixExecutionStatsContract() {
        return matrixExecutionStats.get();
    }

    /**
     * Returns telemetry from the most recent Stage 15 addressing normalization.
     */
    AddressingTelemetry addressingTelemetryContract() {
        return addressingTelemetry.get();
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
     */
    private NormalizedRouteRequest normalizeRouteRequest(RouteRequest request) {
        if (request == null) {
            throw new RouteCoreException(REASON_ROUTE_REQUEST_REQUIRED, "route request must be provided");
        }

        RoutingAlgorithm algorithm = requireAlgorithm(request.getAlgorithm());
        HeuristicType heuristicType = requireHeuristicType(request.getHeuristicType());
        validateAlgorithmHeuristicPair(algorithm, heuristicType);

        AddressingTraitEngine.RouteResolution addressing = addressingTraitEngine.resolveRoute(request, addressingContext());
        InternalRouteRequest internalRequest = new InternalRouteRequest(
                addressing.sourceNodeId(),
                addressing.targetNodeId(),
                request.getDepartureTicks(),
                algorithm,
                heuristicType
        );

        return new NormalizedRouteRequest(
                internalRequest,
                addressing.sourceResolvedAddress(),
                addressing.targetResolvedAddress(),
                addressing.telemetry()
        );
    }

    /**
     * Normalizes and validates a matrix request into internal-node form.
     */
    private NormalizedMatrixRequest normalizeMatrixRequest(MatrixRequest request) {
        if (request == null) {
            throw new RouteCoreException(REASON_MATRIX_REQUEST_REQUIRED, "matrix request must be provided");
        }

        RoutingAlgorithm algorithm = requireAlgorithm(request.getAlgorithm());
        HeuristicType heuristicType = requireHeuristicType(request.getHeuristicType());
        validateAlgorithmHeuristicPair(algorithm, heuristicType);

        AddressingTraitEngine.MatrixResolution addressing = addressingTraitEngine.resolveMatrix(request, addressingContext());
        InternalMatrixRequest internalRequest = new InternalMatrixRequest(
                addressing.sourceNodeIds(),
                addressing.targetNodeIds(),
                request.getDepartureTicks(),
                algorithm,
                heuristicType
        );

        return new NormalizedMatrixRequest(
                internalRequest,
                addressing.sourceExternalIds(),
                addressing.targetExternalIds(),
                addressing.telemetry()
        );
    }

    private AddressingTraitEngine.ResolveContext addressingContext() {
        return new AddressingTraitEngine.ResolveContext(
                edgeGraph,
                nodeIdMapper,
                spatialRuntime,
                addressingTraitCatalog,
                coordinateStrategyRegistry,
                addressingPolicy
        );
    }

    /**
     * Maps an internal node path to an immutable external-id path.
     */
    private List<String> toExternalNodePath(int[] nodePath) {
        List<String> externalPath = new ArrayList<>(nodePath.length);
        for (int nodeId : nodePath) {
            externalPath.add(mapInternalToExternal(nodeId));
        }
        return List.copyOf(externalPath);
    }

    private String mapInternalToExternal(int internalNodeId) {
        try {
            return nodeIdMapper.toExternal(internalNodeId);
        } catch (RuntimeException ex) {
            throw new RouteCoreException(
                    REASON_EXTERNAL_MAPPING_FAILED,
                    "failed to map internal node " + internalNodeId + " to external id",
                    ex
            );
        }
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

    private record NormalizedRouteRequest(
            InternalRouteRequest internalRequest,
            org.Aayush.routing.traits.addressing.ResolvedAddress sourceResolvedAddress,
            org.Aayush.routing.traits.addressing.ResolvedAddress targetResolvedAddress,
            AddressingTelemetry addressingTelemetry
    ) {
    }

    private record NormalizedMatrixRequest(
            InternalMatrixRequest internalRequest,
            List<String> sourceExternalIds,
            List<String> targetExternalIds,
            AddressingTelemetry addressingTelemetry
    ) {
        private NormalizedMatrixRequest {
            sourceExternalIds = List.copyOf(sourceExternalIds);
            targetExternalIds = List.copyOf(targetExternalIds);
        }
    }
}
