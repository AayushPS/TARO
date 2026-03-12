package org.Aayush.routing.traits.transition;

import com.google.flatbuffers.FlatBufferBuilder;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.Aayush.routing.core.RouteCore;
import org.Aayush.routing.graph.TurnCostMap;
import org.Aayush.serialization.flatbuffers.taro.model.Metadata;
import org.Aayush.serialization.flatbuffers.taro.model.Model;
import org.Aayush.serialization.flatbuffers.taro.model.TimeUnit;
import org.Aayush.serialization.flatbuffers.taro.model.TurnCost;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

/**
 * Stage 17 compatibility policy for trait/strategy tuples.
 */
public final class TransitionPolicy {
    private static final int PROBE_FROM_EDGE_ID = 11;
    private static final int PROBE_FINITE_TO_EDGE_ID = 12;
    private static final int PROBE_FORBIDDEN_TO_EDGE_ID = 13;
    private static final float PROBE_FINITE_TURN_PENALTY = 2.5f;
    private static final TurnCostMap PROBE_TURN_COST_MAP = buildProbeTurnCostMap();

    /**
     * Validates one transition trait configuration tuple.
     */
    public void validateCompatibility(TransitionTrait trait, TransitionCostStrategy strategy) {
        TransitionTrait nonNullTrait = Objects.requireNonNull(trait, "trait");
        TransitionCostStrategy nonNullStrategy = Objects.requireNonNull(strategy, "strategy");

        boolean nodeBasedTrait = TransitionTraitCatalog.TRAIT_NODE_BASED.equals(nonNullTrait.id());
        boolean edgeBasedTrait = TransitionTraitCatalog.TRAIT_EDGE_BASED.equals(nonNullTrait.id());

        if (!nodeBasedTrait && !edgeBasedTrait) {
            throw new CompatibilityException(
                    RouteCore.REASON_TRANSITION_CONFIG_INCOMPATIBLE,
                    "transition trait is not recognized by Stage 17 policy: " + nonNullTrait.id()
            );
        }

        if (nodeBasedTrait && nonNullStrategy.appliesFiniteTurnPenalties()) {
            throw new CompatibilityException(
                    RouteCore.REASON_TRANSITION_CONFIG_INCOMPATIBLE,
                    "NODE_BASED trait requires strategy that ignores finite turn penalties"
            );
        }

        if (edgeBasedTrait && !nonNullStrategy.appliesFiniteTurnPenalties()) {
            throw new CompatibilityException(
                    RouteCore.REASON_TRANSITION_CONFIG_INCOMPATIBLE,
                    "EDGE_BASED trait requires strategy that applies finite turn penalties"
            );
        }

        validateSemantics(nonNullTrait, nonNullStrategy, edgeBasedTrait);
    }

    /**
     * Returns default Stage 17 compatibility policy.
     */
    public static TransitionPolicy defaults() {
        return new TransitionPolicy();
    }

    private static void validateSemantics(
            TransitionTrait trait,
            TransitionCostStrategy strategy,
            boolean edgeBasedTrait
    ) {
        requireNeutralDecision(trait, strategy, null, PROBE_FINITE_TO_EDGE_ID, true, "missing turn-map fallback");
        requireNeutralDecision(trait, strategy, PROBE_TURN_COST_MAP, PROBE_FINITE_TO_EDGE_ID, false, "no-predecessor fallback");
        requireForbiddenDecision(trait, strategy);

        if (edgeBasedTrait) {
            requireFinitePenaltyDecision(trait, strategy);
        } else {
            requireNeutralDecision(
                    trait,
                    strategy,
                    PROBE_TURN_COST_MAP,
                    PROBE_FINITE_TO_EDGE_ID,
                    true,
                    "NODE_BASED finite-turn handling"
            );
        }
    }

    private static void requireNeutralDecision(
            TransitionTrait trait,
            TransitionCostStrategy strategy,
            TurnCostMap turnCostMap,
            int toEdgeId,
            boolean hasPredecessor,
            String probeLabel
    ) {
        ProbeDecision decision = evaluateProbe(trait, strategy, turnCostMap, toEdgeId, hasPredecessor, probeLabel);
        if (decision.turnPenaltyApplied() || Float.compare(decision.turnPenalty(), 0.0f) != 0) {
            throw incompatible(
                    "transition strategy " + strategy.id()
                            + " violated " + probeLabel
                            + " for trait " + trait.id()
                            + " (expected neutral decision)"
            );
        }
    }

    private static void requireForbiddenDecision(TransitionTrait trait, TransitionCostStrategy strategy) {
        ProbeDecision decision = evaluateProbe(
                trait,
                strategy,
                PROBE_TURN_COST_MAP,
                PROBE_FORBIDDEN_TO_EDGE_ID,
                true,
                "forbidden-turn handling"
        );
        if (!decision.turnPenaltyApplied() || decision.turnPenalty() != TurnCostMap.FORBIDDEN_TURN) {
            throw incompatible(
                    "transition strategy " + strategy.id()
                            + " violated forbidden-turn handling for trait " + trait.id()
            );
        }
    }

    private static void requireFinitePenaltyDecision(TransitionTrait trait, TransitionCostStrategy strategy) {
        ProbeDecision decision = evaluateProbe(
                trait,
                strategy,
                PROBE_TURN_COST_MAP,
                PROBE_FINITE_TO_EDGE_ID,
                true,
                "EDGE_BASED finite-turn handling"
        );
        if (!decision.turnPenaltyApplied()
                || Float.compare(decision.turnPenalty(), PROBE_FINITE_TURN_PENALTY) != 0) {
            throw incompatible(
                    "transition strategy " + strategy.id()
                            + " violated EDGE_BASED finite-turn handling for trait " + trait.id()
                            + " (expected penalty " + PROBE_FINITE_TURN_PENALTY + ")"
            );
        }
    }

    private static ProbeDecision evaluateProbe(
            TransitionTrait trait,
            TransitionCostStrategy strategy,
            TurnCostMap turnCostMap,
            int toEdgeId,
            boolean hasPredecessor,
            String probeLabel
    ) {
        final long packedDecision;
        try {
            packedDecision = strategy.evaluatePacked(
                    turnCostMap,
                    PROBE_FROM_EDGE_ID,
                    toEdgeId,
                    hasPredecessor
            );
        } catch (TransitionCostStrategy.TransitionComputationException ex) {
            throw incompatible(
                    "transition strategy " + strategy.id()
                            + " failed " + probeLabel
                            + " for trait " + trait.id()
                            + ": " + ex.getMessage(),
                    ex
            );
        } catch (RuntimeException ex) {
            throw incompatible(
                    "transition strategy " + strategy.id()
                            + " failed " + probeLabel
                            + " for trait " + trait.id(),
                    ex
            );
        }

        float turnPenalty = TransitionCostStrategy.TurnCostDecision.unpackTurnPenalty(packedDecision);
        boolean turnPenaltyApplied = TransitionCostStrategy.TurnCostDecision.unpackTurnPenaltyApplied(packedDecision);
        if (turnPenalty == TurnCostMap.FORBIDDEN_TURN) {
            turnPenaltyApplied = true;
        }
        if (Float.isNaN(turnPenalty) || turnPenalty < 0.0f || turnPenalty == Float.NEGATIVE_INFINITY) {
            throw incompatible(
                    "transition strategy " + strategy.id()
                            + " returned invalid turn penalty during "
                            + probeLabel + " for trait " + trait.id()
                            + ": " + turnPenalty
            );
        }
        if (!turnPenaltyApplied && Float.compare(turnPenalty, 0.0f) != 0) {
            throw incompatible(
                    "transition strategy " + strategy.id()
                            + " returned inconsistent packed turn decision during "
                            + probeLabel + " for trait " + trait.id()
            );
        }
        return new ProbeDecision(turnPenalty, turnPenaltyApplied);
    }

    private static CompatibilityException incompatible(String message) {
        return new CompatibilityException(RouteCore.REASON_TRANSITION_CONFIG_INCOMPATIBLE, message);
    }

    private static CompatibilityException incompatible(String message, Throwable cause) {
        return new CompatibilityException(RouteCore.REASON_TRANSITION_CONFIG_INCOMPATIBLE, message, cause);
    }

    private static TurnCostMap buildProbeTurnCostMap() {
        FlatBufferBuilder builder = new FlatBufferBuilder(256);
        int modelVersion = builder.createString("transition-policy-probe");
        int[] turnOffsets = new int[]{
                TurnCost.createTurnCost(builder, PROBE_FROM_EDGE_ID, PROBE_FINITE_TO_EDGE_ID, PROBE_FINITE_TURN_PENALTY),
                TurnCost.createTurnCost(builder, PROBE_FROM_EDGE_ID, PROBE_FORBIDDEN_TO_EDGE_ID, TurnCostMap.FORBIDDEN_TURN)
        };
        int turnCosts = Model.createTurnCostsVector(builder, turnOffsets);

        Metadata.startMetadata(builder);
        Metadata.addSchemaVersion(builder, 1L);
        Metadata.addModelVersion(builder, modelVersion);
        Metadata.addTimeUnit(builder, TimeUnit.SECONDS);
        Metadata.addTickDurationNs(builder, 1_000_000_000L);
        int metadata = Metadata.endMetadata(builder);

        Model.startModel(builder);
        Model.addMetadata(builder, metadata);
        Model.addTurnCosts(builder, turnCosts);
        int root = Model.endModel(builder);
        Model.finishModelBuffer(builder, root);
        return TurnCostMap.fromFlatBuffer(
                ByteBuffer.wrap(builder.sizedByteArray()).order(ByteOrder.LITTLE_ENDIAN)
        );
    }

    private record ProbeDecision(float turnPenalty, boolean turnPenaltyApplied) {
    }

    /**
     * Reason-coded compatibility validation failure.
     */
    @Getter
    @Accessors(fluent = true)
    public static final class CompatibilityException extends RuntimeException {
        private final String reasonCode;

        /**
         * Creates a reason-coded compatibility failure.
         */
        public CompatibilityException(String reasonCode, String message) {
            super(message);
            this.reasonCode = reasonCode;
        }

        /**
         * Creates a reason-coded compatibility failure with preserved root cause.
         */
        public CompatibilityException(String reasonCode, String message, Throwable cause) {
            super(message, cause);
            this.reasonCode = reasonCode;
        }
    }
}
