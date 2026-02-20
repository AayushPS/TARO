package org.Aayush.routing.traits.addressing;

import lombok.Builder;
import lombok.Value;

/**
 * Deterministic normalized endpoint metadata after Stage 15 resolution.
 */
@Value
@Builder
public class ResolvedAddress {
    /**
     * Address type consumed from request payload.
     */
    AddressType inputType;

    /**
     * Trait id that resolved this endpoint.
     */
    String addressingTraitId;

    /**
     * Internal graph node anchor used by planners.
     */
    int internalNodeId;

    /**
     * Canonical external id reverse-mapped from internal anchor.
     */
    String resolvedExternalId;

    /**
     * Original external id provided by caller when input type is external-id.
     */
    String inputExternalId;

    /**
     * Original first coordinate component when input type is coordinates.
     */
    Double inputCoordinateFirst;

    /**
     * Original second coordinate component when input type is coordinates.
     */
    Double inputCoordinateSecond;

    /**
     * Coordinate strategy id used for snapping (for coordinate inputs only).
     */
    String coordinateDistanceStrategyId;

    /**
     * Distance between query coordinate and snapped node, in strategy-relative units.
     */
    Double snapDistance;
}
