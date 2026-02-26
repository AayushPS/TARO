package org.Aayush.routing.cost;

import lombok.Getter;
import lombok.experimental.Accessors;
import org.Aayush.core.time.TimeUtils;
import org.Aayush.routing.graph.EdgeGraph;
import org.Aayush.routing.graph.TurnCostMap;
import org.Aayush.routing.overlay.LiveOverlay;
import org.Aayush.routing.profile.ProfileStore;
import org.Aayush.routing.traits.temporal.ResolvedTemporalContext;
import org.Aayush.routing.traits.temporal.TemporalContextResolver;
import org.Aayush.routing.traits.transition.ResolvedTransitionContext;
import org.Aayush.routing.traits.transition.TransitionCostStrategy;

import java.util.Objects;

/**
 * Time-dependent edge cost composition engine.
 * <p>
 * Canonical effective edge travel cost:
 * </p>
 * <pre>
 * edge_travel_cost = base_weight * temporal_multiplier * live_penalty
 * live_penalty = +INF if blocked else 1/speed_factor or 1.0 if missing/expired
 * effective_cost = edge_travel_cost + optional_turn_penalty
 * </pre>
 * <p>
 * Turn cost is optional by design: if no {@link TurnCostMap} is supplied, or no predecessor
 * is available for the transition, turn penalty is treated as {@code 0.0f}.
 * </p>
 */
@Accessors(fluent = true)
public final class CostEngine {
    /**
     * Sentinel predecessor when no turn transition is available.
     */
    public static final int NO_PREDECESSOR = -1;

    /**
     * Temporal profile sampling mode for cost evaluation.
     */
    public enum TemporalSamplingPolicy {
        /** Uses integer bucket index from normalized entry tick. */
        DISCRETE,
        /** Uses fractional bucket coordinate with cyclic linear interpolation. */
        INTERPOLATED
    }

    private final EdgeGraph edgeGraph;
    private final ProfileStore profileStore;
    private final LiveOverlay liveOverlay;
    private final TurnCostMap turnCostMap;
    @Getter
    private final TimeUtils.EngineTimeUnit engineTimeUnit;
    @Getter
    private final int bucketSizeSeconds;
    private final long bucketSizeTicks;
    @Getter
    private final TemporalSamplingPolicy temporalSamplingPolicy;

    /**
     * Creates a cost engine without explicit turn-cost map.
     * Temporal sampling defaults to {@link TemporalSamplingPolicy#INTERPOLATED}.
     */
    public CostEngine(
            EdgeGraph edgeGraph,
            ProfileStore profileStore,
            LiveOverlay liveOverlay,
            TimeUtils.EngineTimeUnit engineTimeUnit,
            int bucketSizeSeconds
    ) {
        this(
                edgeGraph,
                profileStore,
                liveOverlay,
                null,
                engineTimeUnit,
                bucketSizeSeconds,
                TemporalSamplingPolicy.INTERPOLATED
        );
    }

    /**
     * Creates a cost engine with optional turn-cost map and explicit temporal sampling mode.
     */
    public CostEngine(
            EdgeGraph edgeGraph,
            ProfileStore profileStore,
            LiveOverlay liveOverlay,
            TurnCostMap turnCostMap,
            TimeUtils.EngineTimeUnit engineTimeUnit,
            int bucketSizeSeconds,
            TemporalSamplingPolicy temporalSamplingPolicy
    ) {
        this.edgeGraph = Objects.requireNonNull(edgeGraph, "edgeGraph");
        this.profileStore = Objects.requireNonNull(profileStore, "profileStore");
        this.liveOverlay = Objects.requireNonNull(liveOverlay, "liveOverlay");
        this.turnCostMap = turnCostMap;
        this.engineTimeUnit = Objects.requireNonNull(engineTimeUnit, "engineTimeUnit");
        if (bucketSizeSeconds <= 0) {
            throw new IllegalArgumentException("bucketSizeSeconds must be > 0");
        }
        this.bucketSizeSeconds = bucketSizeSeconds;
        this.temporalSamplingPolicy = Objects.requireNonNull(temporalSamplingPolicy, "temporalSamplingPolicy");
        this.bucketSizeTicks = Math.multiplyExact(bucketSizeSeconds, engineTimeUnit.ticksPerSecond());
    }

    /**
     * Fast-path scalar cost computation with explicit temporal context.
     */
    public float computeEdgeCost(
            int edgeId,
            int fromEdgeId,
            long entryTicks,
            ResolvedTemporalContext temporalContext,
            ResolvedTransitionContext transitionContext
    ) {
        return computeInternal(edgeId, fromEdgeId, entryTicks, temporalContext, transitionContext, null);
    }

    /**
     * Exposes the graph contract used by this cost engine.
     */
    public EdgeGraph edgeGraph() {
        return edgeGraph;
    }

    /**
     * Exposes the profile contract used by this cost engine.
     */
    public ProfileStore profileStore() {
        return profileStore;
    }

    /**
     * Convenience overload with explicit temporal context and no predecessor transition.
     */
    public float computeEdgeCost(
            int edgeId,
            long entryTicks,
            ResolvedTemporalContext temporalContext,
            ResolvedTransitionContext transitionContext
    ) {
        return computeEdgeCost(edgeId, NO_PREDECESSOR, entryTicks, temporalContext, transitionContext);
    }

    /**
     * Explainable cost computation with explicit temporal context.
     */
    public CostBreakdown explainEdgeCost(
            int edgeId,
            int fromEdgeId,
            long entryTicks,
            ResolvedTemporalContext temporalContext,
            ResolvedTransitionContext transitionContext
    ) {
        MutableCostBreakdown breakdown = new MutableCostBreakdown();
        computeInternal(edgeId, fromEdgeId, entryTicks, temporalContext, transitionContext, breakdown);
        return breakdown.toImmutable();
    }

    /**
     * Convenience overload with explicit temporal context and no predecessor transition.
     */
    public CostBreakdown explainEdgeCost(
            int edgeId,
            long entryTicks,
            ResolvedTemporalContext temporalContext,
            ResolvedTransitionContext transitionContext
    ) {
        return explainEdgeCost(edgeId, NO_PREDECESSOR, entryTicks, temporalContext, transitionContext);
    }

    /**
     * Allocation-free explain path for callers that reuse breakdown container and pass temporal context.
     */
    public void explainEdgeCost(
            int edgeId,
            int fromEdgeId,
            long entryTicks,
            ResolvedTemporalContext temporalContext,
            ResolvedTransitionContext transitionContext,
            MutableCostBreakdown out
    ) {
        Objects.requireNonNull(out, "out");
        computeInternal(edgeId, fromEdgeId, entryTicks, temporalContext, transitionContext, out);
    }

    /**
     * Shared execution path for both scalar and explainable cost requests.
     */
    private float computeInternal(
            int edgeId,
            int fromEdgeId,
            long entryTicks,
            ResolvedTemporalContext temporalContext,
            ResolvedTransitionContext transitionContext,
            MutableCostBreakdown out
    ) {
        validateEdgeId(edgeId);
        validatePredecessor(fromEdgeId);
        ResolvedTemporalContext resolvedTemporalContext = Objects.requireNonNull(temporalContext, "temporalContext");
        ResolvedTransitionContext resolvedTransitionContext = Objects.requireNonNull(transitionContext, "transitionContext");
        TemporalContextResolver temporalResolver = Objects.requireNonNull(
                resolvedTemporalContext.getResolver(),
                "temporalContext.resolver"
        );
        TransitionCostStrategy transitionStrategy = Objects.requireNonNull(
                resolvedTransitionContext.getStrategy(),
                "transitionContext.strategy"
        );

        float baseWeight = edgeGraph.getBaseWeight(edgeId);
        ensureFiniteNonNegative(baseWeight, "baseWeight");

        int profileId = edgeGraph.getProfileId(edgeId);
        int dayOfWeek = temporalResolver.resolveDayOfWeek(entryTicks, engineTimeUnit);
        int bucketIndex = temporalResolver.resolveBucketIndex(entryTicks, bucketSizeSeconds, engineTimeUnit);
        double fractionalBucket = Double.NaN;
        float temporalMultiplier = switch (temporalSamplingPolicy) {
            case DISCRETE -> {
                if (out != null) {
                    // Expose diagnostic fractional position only for explain path in discrete mode.
                    fractionalBucket = temporalResolver.resolveFractionalBucket(entryTicks, bucketSizeTicks, engineTimeUnit);
                }
                yield temporalResolver.dayMaskAware()
                        ? profileStore.getMultiplierForDay(profileId, dayOfWeek, bucketIndex)
                        : profileStore.getMultiplier(profileId, bucketIndex);
            }
            case INTERPOLATED -> {
                fractionalBucket = temporalResolver.resolveFractionalBucket(entryTicks, bucketSizeTicks, engineTimeUnit);
                yield temporalResolver.dayMaskAware()
                        ? profileStore.interpolateForDay(profileId, dayOfWeek, fractionalBucket)
                        : profileStore.interpolate(profileId, fractionalBucket);
            }
        };
        ensureFiniteNonNegative(temporalMultiplier, "temporalMultiplier");

        LiveOverlay.LookupResult live = liveOverlay.lookup(edgeId, entryTicks);
        float livePenalty = live.livePenaltyMultiplier();
        ensureValidLivePenalty(livePenalty);

        long packedTurnDecision;
        try {
            packedTurnDecision = transitionStrategy.evaluatePacked(
                    turnCostMap,
                    fromEdgeId,
                    edgeId,
                    fromEdgeId != NO_PREDECESSOR
            );
        } catch (TransitionCostStrategy.TransitionComputationException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new TransitionCostStrategy.TransitionComputationException(
                    "H17_TRANSITION_RESOLUTION_FAILURE",
                    "transition strategy " + transitionStrategy.id()
                            + " failed for transition " + fromEdgeId + " -> " + edgeId,
                    ex
            );
        }
        final float turnPenalty;
        final boolean turnApplied;
        final float effectiveTurnPenalty;
        try {
            turnPenalty = TransitionCostStrategy.TurnCostDecision.unpackTurnPenalty(packedTurnDecision);
            boolean packedTurnApplied = TransitionCostStrategy.TurnCostDecision.unpackTurnPenaltyApplied(packedTurnDecision);
            turnApplied = packedTurnApplied || turnPenalty == TurnCostMap.FORBIDDEN_TURN;
            ensureValidTurnPenalty(turnPenalty);
            if (!turnApplied && Float.compare(turnPenalty, 0.0f) != 0) {
                throw new IllegalStateException(
                        "turnPenalty must be 0.0 when turnPenaltyApplied=false, got " + turnPenalty
                );
            }
            effectiveTurnPenalty = turnApplied ? turnPenalty : 0.0f;
        } catch (TransitionCostStrategy.TransitionComputationException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new TransitionCostStrategy.TransitionComputationException(
                    "H17_TRANSITION_RESOLUTION_FAILURE",
                    "transition strategy " + transitionStrategy.id()
                            + " produced invalid turn decision for transition " + fromEdgeId + " -> " + edgeId
                            + " (packed=0x" + Long.toHexString(packedTurnDecision) + ")",
                    ex
            );
        }

        float edgeTravelCost = computeEdgeTravelCost(baseWeight, temporalMultiplier, livePenalty);
        float effectiveCost = combineWithTurn(edgeTravelCost, effectiveTurnPenalty);

        if (out != null) {
            out.edgeId = edgeId;
            out.fromEdgeId = fromEdgeId;
            out.entryTicks = entryTicks;
            out.baseWeight = baseWeight;
            out.profileId = profileId;
            out.dayOfWeek = dayOfWeek;
            out.bucketIndex = bucketIndex;
            out.fractionalBucket = fractionalBucket;
            out.temporalSamplingPolicy = temporalSamplingPolicy;
            out.temporalMultiplier = temporalMultiplier;
            out.liveState = live.state();
            out.liveSpeedFactor = live.speedFactor();
            out.livePenaltyMultiplier = livePenalty;
            out.turnPenaltyApplied = turnApplied;
            out.turnPenalty = turnPenalty;
            out.edgeTravelCost = edgeTravelCost;
            out.effectiveCost = effectiveCost;
        }

        return effectiveCost;
    }

    /**
     * Computes base-temporal-live edge travel cost without turn penalties.
     */
    private float computeEdgeTravelCost(float baseWeight, float temporalMultiplier, float livePenalty) {
        if (livePenalty == Float.POSITIVE_INFINITY) {
            return Float.POSITIVE_INFINITY;
        }
        double baseTemporal = (double) baseWeight * temporalMultiplier;
        return toNonNegativeCost(baseTemporal * livePenalty, "edgeTravelCost");
    }

    /**
     * Combines edge travel and turn penalties with overflow-safe saturation.
     */
    private static float combineWithTurn(float edgeTravelCost, float turnPenalty) {
        if (edgeTravelCost == Float.POSITIVE_INFINITY || turnPenalty == Float.POSITIVE_INFINITY) {
            return Float.POSITIVE_INFINITY;
        }
        return toNonNegativeCost((double) edgeTravelCost + turnPenalty, "effectiveCost");
    }

    /**
     * Validates edge id against graph bounds.
     */
    private void validateEdgeId(int edgeId) {
        if (edgeId < 0 || edgeId >= edgeGraph.edgeCount()) {
            throw new IllegalArgumentException("edgeId out of range: " + edgeId + " [0, " + edgeGraph.edgeCount() + ")");
        }
    }

    /**
     * Validates predecessor edge id, allowing {@link #NO_PREDECESSOR}.
     */
    private void validatePredecessor(int fromEdgeId) {
        if (fromEdgeId == NO_PREDECESSOR) {
            return;
        }
        if (fromEdgeId < 0 || fromEdgeId >= edgeGraph.edgeCount()) {
            throw new IllegalArgumentException(
                    "fromEdgeId out of range: " + fromEdgeId + " [0, " + edgeGraph.edgeCount() + ") or NO_PREDECESSOR"
            );
        }
    }

    /**
     * Validates that a factor/cost component is finite and non-negative.
     */
    private static void ensureFiniteNonNegative(float value, String name) {
        if (!Float.isFinite(value) || value < 0.0f) {
            throw new IllegalStateException(name + " must be finite and >= 0, got " + value);
        }
    }

    /**
     * Validates overlay multiplier contract ({@code >=1.0} or {@code +INF}).
     */
    private static void ensureValidLivePenalty(float value) {
        if (Float.isNaN(value) || value < 1.0f) {
            throw new IllegalStateException("livePenaltyMultiplier must be >= 1.0 or +INF, got " + value);
        }
    }

    /**
     * Validates turn penalty contract ({@code >=0.0} or {@code +INF}).
     */
    private static void ensureValidTurnPenalty(float value) {
        if (Float.isNaN(value) || value < 0.0f || value == Float.NEGATIVE_INFINITY) {
            throw new IllegalStateException("turnPenalty must be >= 0.0 or +INF, got " + value);
        }
    }

    /**
     * Converts intermediate double cost to float with overflow saturation.
     */
    private static float toNonNegativeCost(double value, String label) {
        if (Double.isNaN(value) || value < 0.0d) {
            throw new IllegalStateException(label + " must be >= 0 and not NaN, got " + value);
        }
        if (!Double.isFinite(value) || value > Float.MAX_VALUE) {
            return Float.POSITIVE_INFINITY;
        }
        return (float) value;
    }

    /**
     * Immutable explainability payload.
     */
    public record CostBreakdown(
            int edgeId,
            int fromEdgeId,
            long entryTicks,
            float baseWeight,
            int profileId,
            int dayOfWeek,
            int bucketIndex,
            double fractionalBucket,
            TemporalSamplingPolicy temporalSamplingPolicy,
            float temporalMultiplier,
            LiveOverlay.LookupState liveState,
            float liveSpeedFactor,
            float livePenaltyMultiplier,
            boolean turnPenaltyApplied,
            float turnPenalty,
            float edgeTravelCost,
            float effectiveCost
    ) {
        /**
         * Returns whether this transition was blocked by live overlay state.
         */
        public boolean blockedByLive() {
            return liveState == LiveOverlay.LookupState.BLOCKED;
        }

        /**
         * Returns whether an explicit infinite turn penalty forbids transition.
         */
        public boolean forbiddenTurn() {
            return turnPenaltyApplied && turnPenalty == Float.POSITIVE_INFINITY;
        }
    }

    /**
     * Reusable mutable breakdown container for allocation-free explain path.
     */
    public static final class MutableCostBreakdown {
        public int edgeId;
        public int fromEdgeId;
        public long entryTicks;
        public float baseWeight;
        public int profileId;
        public int dayOfWeek;
        public int bucketIndex;
        public double fractionalBucket;
        public TemporalSamplingPolicy temporalSamplingPolicy;
        public float temporalMultiplier;
        public LiveOverlay.LookupState liveState;
        public float liveSpeedFactor;
        public float livePenaltyMultiplier;
        public boolean turnPenaltyApplied;
        public float turnPenalty;
        public float edgeTravelCost;
        public float effectiveCost;

        /**
         * Converts this mutable container into an immutable snapshot record.
         */
        public CostBreakdown toImmutable() {
            return new CostBreakdown(
                    edgeId,
                    fromEdgeId,
                    entryTicks,
                    baseWeight,
                    profileId,
                    dayOfWeek,
                    bucketIndex,
                    fractionalBucket,
                    temporalSamplingPolicy,
                    temporalMultiplier,
                    liveState,
                    liveSpeedFactor,
                    livePenaltyMultiplier,
                    turnPenaltyApplied,
                    turnPenalty,
                    edgeTravelCost,
                    effectiveCost
            );
        }
    }
}
