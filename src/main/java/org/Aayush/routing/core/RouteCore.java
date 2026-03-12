package org.Aayush.routing.core;

import lombok.Builder;
import org.Aayush.core.id.IDMapper;
import org.Aayush.routing.cost.CostEngine;
import org.Aayush.routing.execution.DefaultExecutionRuntimeBinder;
import org.Aayush.routing.execution.ExecutionProfileAwareRouter;
import org.Aayush.routing.execution.ExecutionProfileRegistry;
import org.Aayush.routing.execution.ExecutionRuntimeBinder;
import org.Aayush.routing.execution.ExecutionRuntimeConfig;
import org.Aayush.routing.execution.ResolvedExecutionProfileContext;
import org.Aayush.routing.graph.EdgeGraph;
import org.Aayush.routing.heuristic.GoalBoundHeuristic;
import org.Aayush.routing.heuristic.HeuristicProvider;
import org.Aayush.routing.heuristic.HeuristicProviderFactory;
import org.Aayush.routing.heuristic.HeuristicType;
import org.Aayush.routing.heuristic.LandmarkStore;
import org.Aayush.routing.profile.ProfileStore;
import org.Aayush.routing.spatial.SpatialRuntime;
import org.Aayush.routing.traits.addressing.AddressingPolicy;
import org.Aayush.routing.traits.addressing.AddressingRuntimeBinder;
import org.Aayush.routing.traits.addressing.AddressingRuntimeConfig;
import org.Aayush.routing.traits.addressing.AddressingTelemetry;
import org.Aayush.routing.traits.addressing.AddressingTraitCatalog;
import org.Aayush.routing.traits.addressing.AddressingTraitEngine;
import org.Aayush.routing.traits.addressing.CoordinateStrategyRegistry;
import org.Aayush.routing.traits.registry.ResolvedTraitBundleContext;
import org.Aayush.routing.traits.registry.TraitBundleCompatibilityPolicy;
import org.Aayush.routing.traits.registry.TraitBundleHasher;
import org.Aayush.routing.traits.registry.TraitBundleRegistry;
import org.Aayush.routing.traits.registry.TraitBundleRuntimeBinder;
import org.Aayush.routing.traits.registry.TraitBundleRuntimeConfig;
import org.Aayush.routing.traits.registry.TraitBundleTelemetry;
import org.Aayush.routing.traits.temporal.ResolvedTemporalContext;
import org.Aayush.routing.traits.temporal.TemporalContextResolver;
import org.Aayush.routing.traits.temporal.TemporalPolicy;
import org.Aayush.routing.traits.temporal.TemporalRuntimeBinder;
import org.Aayush.routing.traits.temporal.TemporalRuntimeConfig;
import org.Aayush.routing.traits.temporal.TemporalStrategyRegistry;
import org.Aayush.routing.traits.temporal.TemporalTelemetry;
import org.Aayush.routing.traits.temporal.TemporalTimezonePolicyRegistry;
import org.Aayush.routing.traits.temporal.TemporalTraitCatalog;
import org.Aayush.routing.traits.transition.ResolvedTransitionContext;
import org.Aayush.routing.traits.transition.TransitionCostStrategy;
import org.Aayush.routing.traits.transition.TransitionPolicy;
import org.Aayush.routing.traits.transition.TransitionRuntimeBinder;
import org.Aayush.routing.traits.transition.TransitionRuntimeConfig;
import org.Aayush.routing.traits.transition.TransitionStrategyRegistry;
import org.Aayush.routing.traits.transition.TransitionTelemetry;
import org.Aayush.routing.traits.transition.TransitionTraitCatalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
public final class RouteCore implements ExecutionProfileAwareRouter {
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
    public static final String REASON_ADDRESSING_CONFIG_REQUIRED = "H15_ADDRESSING_CONFIG_REQUIRED";
    public static final String REASON_ADDRESSING_CONFIG_INCOMPATIBLE = "H15_ADDRESSING_CONFIG_INCOMPATIBLE";
    public static final String REASON_ADDRESSING_RUNTIME_MISMATCH = "H15_ADDRESSING_RUNTIME_MISMATCH";

    public static final String REASON_TEMPORAL_CONFIG_REQUIRED = "H16_TEMPORAL_CONFIG_REQUIRED";
    public static final String REASON_UNKNOWN_TEMPORAL_TRAIT = "H16_UNKNOWN_TEMPORAL_TRAIT";
    public static final String REASON_UNKNOWN_TEMPORAL_STRATEGY = "H16_UNKNOWN_TEMPORAL_STRATEGY";
    public static final String REASON_TIMEZONE_POLICY_REQUIRED = "H16_TIMEZONE_POLICY_REQUIRED";
    public static final String REASON_UNKNOWN_TIMEZONE_POLICY = "H16_UNKNOWN_TIMEZONE_POLICY";
    public static final String REASON_TIMEZONE_POLICY_NOT_APPLICABLE = "H16_TIMEZONE_POLICY_NOT_APPLICABLE";
    public static final String REASON_MODEL_TIMEZONE_REQUIRED = "H16_MODEL_TIMEZONE_REQUIRED";
    public static final String REASON_INVALID_MODEL_TIMEZONE = "H16_INVALID_MODEL_TIMEZONE";
    public static final String REASON_TEMPORAL_CONFIG_INCOMPATIBLE = "H16_TEMPORAL_CONFIG_INCOMPATIBLE";
    public static final String REASON_TEMPORAL_RESOLUTION_FAILURE = "H16_TEMPORAL_RESOLUTION_FAILURE";
    public static final String REASON_TRANSITION_CONFIG_REQUIRED = "H17_TRANSITION_CONFIG_REQUIRED";
    public static final String REASON_UNKNOWN_TRANSITION_TRAIT = "H17_UNKNOWN_TRANSITION_TRAIT";
    public static final String REASON_UNKNOWN_TRANSITION_STRATEGY = "H17_UNKNOWN_TRANSITION_STRATEGY";
    public static final String REASON_TRANSITION_CONFIG_INCOMPATIBLE = "H17_TRANSITION_CONFIG_INCOMPATIBLE";
    public static final String REASON_TRANSITION_RESOLUTION_FAILURE = "H17_TRANSITION_RESOLUTION_FAILURE";
    public static final String REASON_TRAIT_BUNDLE_CONFIG_REQUIRED = "H18_TRAIT_BUNDLE_CONFIG_REQUIRED";
    public static final String REASON_UNKNOWN_TRAIT_BUNDLE = "H18_UNKNOWN_TRAIT_BUNDLE";
    public static final String REASON_TRAIT_BUNDLE_CONFIG_CONFLICT = "H18_TRAIT_BUNDLE_CONFIG_CONFLICT";
    public static final String REASON_TRAIT_BUNDLE_INCOMPATIBLE = "H18_TRAIT_BUNDLE_INCOMPATIBLE";
    public static final String REASON_MISSING_TRAIT_DEPENDENCY = "H18_MISSING_TRAIT_DEPENDENCY";
    public static final String REASON_TRAIT_HASH_GENERATION_FAILED = "H18_TRAIT_HASH_GENERATION_FAILED";
    public static final String REASON_REQUEST_TRAIT_SELECTOR_MISMATCH = "H18_REQUEST_TRAIT_SELECTOR_MISMATCH";
    public static final String REASON_EXECUTION_CONFIG_REQUIRED = "HEX_EXECUTION_CONFIG_REQUIRED";
    public static final String REASON_UNKNOWN_EXECUTION_PROFILE = "HEX_UNKNOWN_EXECUTION_PROFILE";
    public static final String REASON_EXECUTION_CONFIG_CONFLICT = "HEX_EXECUTION_CONFIG_CONFLICT";
    public static final String REASON_EXECUTION_PROFILE_INCOMPATIBLE = "HEX_EXECUTION_PROFILE_INCOMPATIBLE";
    public static final String REASON_REQUEST_EXECUTION_SELECTOR_MISMATCH = "HEX_REQUEST_EXECUTION_SELECTOR_MISMATCH";

    private static final GoalBoundHeuristic ZERO_HEURISTIC = nodeId -> 0.0d;

    private final EdgeGraph edgeGraph;
    private final ProfileStore profileStore;
    private final CostEngine costEngine;
    private final IDMapper nodeIdMapper;
    private final LandmarkStore landmarkStore;

    private final RoutePlanner dijkstraPlanner;
    private final RoutePlanner aStarPlanner;
    private final MatrixPlanner matrixPlanner;
    private final RequestNormalizer requestNormalizer;
    private final HeuristicProviderFactory heuristicProviderFactory;

    private final SpatialRuntime spatialRuntime;
    private final AddressingTraitEngine addressingTraitEngine;
    private final AddressingTraitCatalog addressingTraitCatalog;
    private final CoordinateStrategyRegistry coordinateStrategyRegistry;
    private final AddressingPolicy addressingPolicy;
    private final AddressingRuntimeBinder.Binding addressingRuntimeBinding;
    private final TemporalContextResolver temporalContextResolver;
    private final ResolvedTemporalContext resolvedTemporalContext;
    private final TemporalTelemetry temporalTelemetry;
    private final ResolvedTransitionContext resolvedTransitionContext;
    private final TransitionTelemetry transitionTelemetry;
    private final ResolvedTraitBundleContext resolvedTraitBundleContext;
    private final TraitBundleTelemetry traitBundleTelemetry;
    private final ResolvedExecutionProfileContext resolvedExecutionProfileContext;
    private final HeuristicProvider boundHeuristicProvider;

    private final ThreadLocal<MatrixExecutionStats> matrixExecutionStats =
            ThreadLocal.withInitial(() -> MatrixExecutionStats.empty(0));
    private final ThreadLocal<AddressingTelemetry> addressingTelemetry =
            ThreadLocal.withInitial(AddressingTelemetry::empty);

    /**
     * Creates the route-core facade with strict runtime contract validation.
     *
     * @param edgeGraph edge-based graph runtime.
     * @param profileStore temporal profile runtime.
     * @param costEngine time-dependent cost engine bound to graph/profile.
     * @param nodeIdMapper external-to-internal node id mapper.
     * @param landmarkStore optional landmark runtime (required for LANDMARK heuristic).
     * @param matrixPlanner optional matrix planner override.
     * @param traitBundleRuntimeConfig optional Stage 18 bundle config; when absent legacy per-axis configs are synthesized.
     * @param addressingRuntimeConfig locked addressing runtime configuration (required).
     * @param temporalRuntimeConfig locked temporal runtime configuration (required).
     * @param transitionRuntimeConfig locked transition runtime configuration (required).
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
            RequestNormalizer requestNormalizer,
            HeuristicProviderFactory heuristicProviderFactory,
            SpatialRuntime spatialRuntime,
            ExecutionRuntimeConfig executionRuntimeConfig,
            ExecutionProfileRegistry executionProfileRegistry,
            ExecutionRuntimeBinder executionRuntimeBinder,
            AddressingTraitEngine addressingTraitEngine,
            AddressingTraitCatalog addressingTraitCatalog,
            CoordinateStrategyRegistry coordinateStrategyRegistry,
            AddressingPolicy addressingPolicy,
            TraitBundleRuntimeConfig traitBundleRuntimeConfig,
            TraitBundleRegistry traitBundleRegistry,
            TraitBundleCompatibilityPolicy traitBundleCompatibilityPolicy,
            TraitBundleHasher traitBundleHasher,
            TraitBundleRuntimeBinder traitBundleRuntimeBinder,
            AddressingRuntimeConfig addressingRuntimeConfig,
            AddressingRuntimeBinder addressingRuntimeBinder,
            TemporalRuntimeConfig temporalRuntimeConfig,
            TemporalTraitCatalog temporalTraitCatalog,
            TemporalStrategyRegistry temporalStrategyRegistry,
            TemporalTimezonePolicyRegistry temporalTimezonePolicyRegistry,
            TemporalPolicy temporalPolicy,
            TemporalRuntimeBinder temporalRuntimeBinder,
            TransitionRuntimeConfig transitionRuntimeConfig,
            TransitionTraitCatalog transitionTraitCatalog,
            TransitionStrategyRegistry transitionStrategyRegistry,
            TransitionPolicy transitionPolicy,
            TransitionRuntimeBinder transitionRuntimeBinder
    ) {
        this.edgeGraph = Objects.requireNonNull(edgeGraph, "edgeGraph");
        this.profileStore = Objects.requireNonNull(profileStore, "profileStore");
        this.costEngine = Objects.requireNonNull(costEngine, "costEngine");
        this.nodeIdMapper = Objects.requireNonNull(nodeIdMapper, "nodeIdMapper");
        this.landmarkStore = landmarkStore;
        this.matrixPlanner = matrixPlanner == null ? new NativeOneToManyMatrixPlanner() : matrixPlanner;
        this.dijkstraPlanner = new EdgeBasedRoutePlanner(false);
        this.requestNormalizer = requestNormalizer == null ? new DefaultRequestNormalizer() : requestNormalizer;
        this.heuristicProviderFactory = heuristicProviderFactory == null
                ? new org.Aayush.routing.heuristic.DefaultHeuristicProviderFactory()
                : heuristicProviderFactory;

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
        TraitBundleRuntimeBinder activeTraitBundleRuntimeBinder = traitBundleRuntimeBinder == null
                ? new TraitBundleRuntimeBinder()
                : traitBundleRuntimeBinder;
        TraitBundleRuntimeBinder.Binding traitBundleBinding;
        try {
            traitBundleBinding = activeTraitBundleRuntimeBinder.bind(TraitBundleRuntimeBinder.BindInput.builder()
                    .traitBundleRuntimeConfig(traitBundleRuntimeConfig)
                    .traitBundleRegistry(traitBundleRegistry)
                    .addressingRuntimeConfig(addressingRuntimeConfig)
                    .temporalRuntimeConfig(temporalRuntimeConfig)
                    .transitionRuntimeConfig(transitionRuntimeConfig)
                    .addressingTraitCatalog(this.addressingTraitCatalog)
                    .coordinateStrategyRegistry(this.coordinateStrategyRegistry)
                    .addressingRuntimeBinder(addressingRuntimeBinder)
                    .temporalTraitCatalog(temporalTraitCatalog)
                    .temporalStrategyRegistry(temporalStrategyRegistry)
                    .temporalTimezonePolicyRegistry(temporalTimezonePolicyRegistry)
                    .temporalPolicy(temporalPolicy)
                    .temporalRuntimeBinder(temporalRuntimeBinder)
                    .transitionTraitCatalog(transitionTraitCatalog)
                    .transitionStrategyRegistry(transitionStrategyRegistry)
                    .transitionPolicy(transitionPolicy)
                    .transitionRuntimeBinder(transitionRuntimeBinder)
                    .traitBundleCompatibilityPolicy(traitBundleCompatibilityPolicy)
                    .traitBundleHasher(traitBundleHasher)
                    .build());
        } catch (RouteCoreException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new RouteCoreException(
                    REASON_TRAIT_BUNDLE_INCOMPATIBLE,
                    "failed to bind trait bundle runtime config: " + ex.getMessage(),
                    ex
            );
        }
        this.addressingRuntimeBinding = Objects.requireNonNull(
                traitBundleBinding.getAddressingRuntimeBinding(),
                "traitBundleBinding.addressingRuntimeBinding"
        );
        this.temporalContextResolver = Objects.requireNonNull(
                traitBundleBinding.getTemporalContextResolver(),
                "traitBundleBinding.temporalContextResolver"
        );
        this.resolvedTemporalContext = Objects.requireNonNull(
                traitBundleBinding.getResolvedTemporalContext(),
                "traitBundleBinding.resolvedTemporalContext"
        );
        this.temporalTelemetry = Objects.requireNonNull(
                traitBundleBinding.getTemporalTelemetry(),
                "traitBundleBinding.temporalTelemetry"
        );
        this.resolvedTransitionContext = Objects.requireNonNull(
                traitBundleBinding.getResolvedTransitionContext(),
                "traitBundleBinding.resolvedTransitionContext"
        );
        this.transitionTelemetry = Objects.requireNonNull(
                traitBundleBinding.getTransitionTelemetry(),
                "traitBundleBinding.transitionTelemetry"
        );
        this.resolvedTraitBundleContext = Objects.requireNonNull(
                traitBundleBinding.getResolvedTraitBundleContext(),
                "traitBundleBinding.resolvedTraitBundleContext"
        );
        this.traitBundleTelemetry = Objects.requireNonNull(
                traitBundleBinding.getTraitBundleTelemetry(),
                "traitBundleBinding.traitBundleTelemetry"
        );
        ExecutionRuntimeBinder activeExecutionRuntimeBinder = executionRuntimeBinder == null
                ? new DefaultExecutionRuntimeBinder()
                : executionRuntimeBinder;
        ExecutionRuntimeBinder.Binding executionBinding;
        try {
            executionBinding = activeExecutionRuntimeBinder.bind(ExecutionRuntimeBinder.BindInput.builder()
                    .executionRuntimeConfig(executionRuntimeConfig)
                    .executionProfileRegistry(executionProfileRegistry)
                    .edgeGraph(this.edgeGraph)
                    .profileStore(this.profileStore)
                    .costEngine(this.costEngine)
                    .landmarkStore(this.landmarkStore)
                    .heuristicProviderFactory(this.heuristicProviderFactory)
                    .build());
        } catch (RouteCoreException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new RouteCoreException(
                    REASON_EXECUTION_PROFILE_INCOMPATIBLE,
                    "failed to bind execution runtime config: " + ex.getMessage(),
                    ex
            );
        }
        this.resolvedExecutionProfileContext = Objects.requireNonNull(
                executionBinding.getResolvedExecutionProfileContext(),
                "executionBinding.resolvedExecutionProfileContext"
        );
        this.boundHeuristicProvider = Objects.requireNonNull(
                executionBinding.getHeuristicProvider(),
                "executionBinding.heuristicProvider"
        );

        this.aStarPlanner = aStarPlanner == null
                ? new BidirectionalTdAStarPlanner(edgeGraph, costEngine)
                : aStarPlanner;
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
        RequestNormalizer.NormalizedRouteRequest normalized = requestNormalizer.normalizeRoute(
                request,
                normalizationContext()
        );
        addressingTelemetry.set(normalized.getAddressingTelemetry());

        InternalRouteRequest internalRequest = normalized.getInternalRequest();
        InternalRoutePlan plan = computeInternal(internalRequest);

        RouteResponse.RouteResponseBuilder builder = RouteResponse.builder()
                .reachable(plan.reachable())
                .departureTicks(internalRequest.departureTicks())
                .arrivalTicks(plan.arrivalTicks())
                .totalCost(plan.totalCost())
                .settledStates(plan.settledStates())
                .algorithm(internalRequest.algorithm())
                .heuristicType(internalRequest.heuristicType())
                .sourceResolvedAddress(normalized.getSourceResolvedAddress())
                .targetResolvedAddress(normalized.getTargetResolvedAddress());

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
        matrixExecutionStats.set(MatrixExecutionStats.empty(0));
        addressingTelemetry.set(AddressingTelemetry.empty());
        RequestNormalizer.NormalizedMatrixRequest normalized = requestNormalizer.normalizeMatrix(
                request,
                normalizationContext()
        );
        addressingTelemetry.set(normalized.getAddressingTelemetry());

        InternalMatrixRequest internalRequest = normalized.getInternalRequest();
        try {
            MatrixPlan matrixPlan = matrixPlanner.compute(this, internalRequest);
            matrixExecutionStats.set(matrixPlan.executionStats());
            return MatrixResponse.builder()
                    .sourceExternalIds(normalized.getSourceExternalIds())
                    .targetExternalIds(normalized.getTargetExternalIds())
                    .reachable(matrixPlan.reachable())
                    .totalCosts(matrixPlan.totalCosts())
                    .arrivalTicks(matrixPlan.arrivalTicks())
                    .algorithm(internalRequest.algorithm())
                    .heuristicType(internalRequest.heuristicType())
                    .implementationNote(matrixPlan.implementationNote())
                    .build();
        } catch (TemporalContextResolver.TemporalResolutionException ex) {
            throw new RouteCoreException(
                    REASON_TEMPORAL_RESOLUTION_FAILURE,
                    ex.reasonCode() + ": " + ex.getMessage(),
                    ex
            );
        } catch (TransitionCostStrategy.TransitionComputationException ex) {
            throw new RouteCoreException(
                    REASON_TRANSITION_RESOLUTION_FAILURE,
                    ex.reasonCode() + ": " + ex.getMessage(),
                    ex
            );
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
        } catch (RouteCoreException ex) {
            throw remapMatrixCompatibilityException(ex);
        }
    }

    private RouteCoreException remapMatrixCompatibilityException(RouteCoreException ex) {
        if (REASON_SEARCH_BUDGET_EXCEEDED.equals(ex.getReasonCode())) {
            return new RouteCoreException(
                    REASON_MATRIX_SEARCH_BUDGET_EXCEEDED,
                    ex.getMessage(),
                    ex
            );
        }
        if (REASON_NUMERIC_SAFETY_BREACH.equals(ex.getReasonCode())) {
            return new RouteCoreException(
                    REASON_MATRIX_NUMERIC_SAFETY_BREACH,
                    ex.getMessage(),
                    ex
            );
        }
        return ex;
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
     * Returns startup-bound temporal telemetry.
     */
    TemporalTelemetry temporalTelemetryContract() {
        return temporalTelemetry;
    }

    /**
     * Returns startup-bound transition telemetry.
     */
    TransitionTelemetry transitionTelemetryContract() {
        return transitionTelemetry;
    }

    /**
     * Returns startup-bound Stage 18 trait-bundle context.
     */
    ResolvedTraitBundleContext traitBundleContextContract() {
        return resolvedTraitBundleContext;
    }

    /**
     * Returns startup-bound Stage 18 trait-bundle telemetry.
     */
    TraitBundleTelemetry traitBundleTelemetryContract() {
        return traitBundleTelemetry;
    }

    /**
     * Returns the startup-bound execution profile.
     */
    @Override
    public ResolvedExecutionProfileContext executionProfileContext() {
        return resolvedExecutionProfileContext;
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
        } catch (TemporalContextResolver.TemporalResolutionException ex) {
            throw new RouteCoreException(
                    REASON_TEMPORAL_RESOLUTION_FAILURE,
                    ex.reasonCode() + ": " + ex.getMessage(),
                    ex
            );
        } catch (TransitionCostStrategy.TransitionComputationException ex) {
            throw new RouteCoreException(
                    REASON_TRANSITION_RESOLUTION_FAILURE,
                    ex.reasonCode() + ": " + ex.getMessage(),
                    ex
            );
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
        return bindGoalHeuristicContract(request.heuristicType(), request.targetNodeId());
    }

    /**
     * Binds one goal-specific heuristic estimator for internal planner use.
     */
    GoalBoundHeuristic bindGoalHeuristicContract(HeuristicType heuristicType, int targetNodeId) {
        if (heuristicType == HeuristicType.NONE) {
            return ZERO_HEURISTIC;
        }
        if (heuristicType != resolvedExecutionProfileContext.getHeuristicType()) {
            throw new RouteCoreException(
                    REASON_EXECUTION_PROFILE_INCOMPATIBLE,
                    "requested internal heuristic " + heuristicType
                            + " does not match startup execution profile "
                            + resolvedExecutionProfileContext.getHeuristicType()
            );
        }
        try {
            return boundHeuristicProvider.bindGoal(targetNodeId);
        } catch (RuntimeException ex) {
            throw new RouteCoreException(
                    REASON_HEURISTIC_BIND_FAILED,
                    "failed to bind heuristic " + heuristicType + " to goal node " + targetNodeId,
                    ex
            );
        }
    }

    private RequestNormalizer.Context normalizationContext() {
        return RequestNormalizer.Context.builder()
                .edgeGraph(edgeGraph)
                .nodeIdMapper(nodeIdMapper)
                .spatialRuntime(spatialRuntime)
                .addressingTraitEngine(addressingTraitEngine)
                .addressingTraitCatalog(addressingTraitCatalog)
                .coordinateStrategyRegistry(coordinateStrategyRegistry)
                .addressingPolicy(addressingPolicy)
                .addressingRuntimeBinding(addressingRuntimeBinding)
                .resolvedTemporalContext(resolvedTemporalContext)
                .resolvedTransitionContext(resolvedTransitionContext)
                .resolvedExecutionProfileContext(resolvedExecutionProfileContext)
                .build();
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

}
