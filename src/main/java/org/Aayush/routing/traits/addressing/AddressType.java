package org.Aayush.routing.traits.addressing;

/**
 * Typed request address payload discriminator.
 */
public enum AddressType {
    /**
     * External id lookup through {@code IDMapper}.
     */
    EXTERNAL_ID,

    /**
     * Coordinate-based lookup through {@code SpatialRuntime}.
     */
    COORDINATES
}
