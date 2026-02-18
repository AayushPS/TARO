package org.Aayush.routing.overlay;

import lombok.Getter;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.Aayush.core.time.TimeUtils;

import java.util.Objects;

/**
 * Canonical live-update payload for runtime overlay ingestion.
 * <p>
 * This is an immutable ingest record for {@link LiveOverlay}. Expiry is always stored
 * as an absolute engine tick ({@code validUntilTicks}) so ingest/lookup logic remains
 * unit-agnostic and fast.
 * </p>
 * <p>
 * Speed factor domain:
 * </p>
 * <ul>
 * <li>{@code 0.0f}: hard block</li>
 * <li>{@code (0.0f, 1.0f]}: slowdown factor</li>
 * </ul>
 */
@Getter
@ToString
@Accessors(fluent = true)
public final class LiveUpdate {

    /** Destination edge id for this override. */
    private final int edgeId;
    /** Live speed factor in range {@code [0.0, 1.0]}. */
    private final float speedFactor;
    /** Absolute exclusive-expiry time in engine ticks. */
    private final long validUntilTicks;

    /**
     * Internal canonical constructor after input validation.
     */
    private LiveUpdate(int edgeId, float speedFactor, long validUntilTicks) {
        validate(edgeId, speedFactor);
        this.edgeId = edgeId;
        this.speedFactor = speedFactor;
        this.validUntilTicks = validUntilTicks;
    }

    /**
     * Creates a live update with absolute expiry in engine ticks.
     *
     * @param edgeId destination edge id; must be {@code >= 0}.
     * @param speedFactor live factor in {@code [0.0, 1.0]}.
     * @param validUntilTicks absolute expiry tick (exclusive).
     * @return immutable live update.
     */
    public static LiveUpdate of(int edgeId, float speedFactor, long validUntilTicks) {
        return new LiveUpdate(edgeId, speedFactor, validUntilTicks);
    }

    /**
     * Creates a live update from relative TTL already expressed in engine ticks.
     *
     * @param edgeId destination edge id; must be {@code >= 0}.
     * @param speedFactor live factor in {@code [0.0, 1.0]}.
     * @param nowTicks current engine time.
     * @param ttlTicks relative TTL in engine ticks; must be {@code >= 0}.
     * @return immutable live update with computed absolute expiry.
     * @throws ArithmeticException if {@code nowTicks + ttlTicks} overflows long.
     */
    public static LiveUpdate fromRelativeTtl(
            int edgeId,
            float speedFactor,
            long nowTicks,
            long ttlTicks
    ) {
        if (ttlTicks < 0) {
            throw new IllegalArgumentException("ttl_ticks must be >= 0");
        }
        long validUntilTicks = Math.addExact(nowTicks, ttlTicks);
        return new LiveUpdate(edgeId, speedFactor, validUntilTicks);
    }

    /**
     * Creates a live update from TTL in a caller-specified input unit.
     *
     * @param edgeId destination edge id; must be {@code >= 0}.
     * @param speedFactor live factor in {@code [0.0, 1.0]}.
     * @param nowTicks current engine time expressed in {@code engineUnit}.
     * @param ttlValue relative TTL value; must be {@code >= 0}.
     * @param inputUnit unit of {@code ttlValue}.
     * @param engineUnit runtime engine tick unit.
     * @return immutable live update with TTL normalized into engine ticks.
     */
    public static LiveUpdate fromRelativeTtl(
            int edgeId,
            float speedFactor,
            long nowTicks,
            long ttlValue,
            TimeUtils.EngineTimeUnit inputUnit,
            TimeUtils.EngineTimeUnit engineUnit
    ) {
        if (ttlValue < 0) {
            throw new IllegalArgumentException("ttl must be >= 0");
        }
        Objects.requireNonNull(inputUnit, "inputUnit");
        Objects.requireNonNull(engineUnit, "engineUnit");
        long normalizedTtlTicks = TimeUtils.normalizeToEngineTicks(ttlValue, inputUnit, engineUnit);
        return fromRelativeTtl(edgeId, speedFactor, nowTicks, normalizedTtlTicks);
    }

    /**
     * Validates edge and speed-factor domain constraints.
     */
    private static void validate(int edgeId, float speedFactor) {
        if (edgeId < 0) {
            throw new IllegalArgumentException("edge_id must be >= 0");
        }
        if (!Float.isFinite(speedFactor)) {
            throw new IllegalArgumentException("speed_factor must be finite");
        }
        if (speedFactor < 0.0f || speedFactor > 1.0f) {
            throw new IllegalArgumentException("speed_factor must be within [0.0, 1.0]");
        }
    }
}
