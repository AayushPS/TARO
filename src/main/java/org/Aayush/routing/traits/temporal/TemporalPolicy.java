package org.Aayush.routing.traits.temporal;

import lombok.Getter;
import lombok.experimental.Accessors;
import org.Aayush.routing.core.RouteCore;

import java.util.Objects;

/**
 * Stage 16 compatibility policy for trait/strategy/timezone combinations.
 */
public final class TemporalPolicy {

    /**
     * Validates one temporal trait configuration tuple.
     *
     * @param trait selected temporal trait.
     * @param strategy selected temporal strategy.
     * @param timezonePolicyId selected timezone policy id (nullable).
     */
    public void validateCompatibility(
            TemporalTrait trait,
            TemporalResolutionStrategy strategy,
            String timezonePolicyId
    ) {
        TemporalTrait nonNullTrait = Objects.requireNonNull(trait, "trait");
        TemporalResolutionStrategy nonNullStrategy = Objects.requireNonNull(strategy, "strategy");

        boolean calendarTrait = TemporalTraitCatalog.TRAIT_CALENDAR.equals(nonNullTrait.id());
        boolean linearTrait = TemporalTraitCatalog.TRAIT_LINEAR.equals(nonNullTrait.id());

        if (!calendarTrait && !linearTrait) {
            throw new CompatibilityException(
                    RouteCore.REASON_TEMPORAL_CONFIG_INCOMPATIBLE,
                    "temporal trait is not recognized by Stage 16 policy: " + nonNullTrait.id()
            );
        }

        if (calendarTrait && timezonePolicyId == null) {
            throw new CompatibilityException(
                    RouteCore.REASON_TIMEZONE_POLICY_REQUIRED,
                    "timezonePolicyId is required for CALENDAR temporal trait"
            );
        }

        if (linearTrait && timezonePolicyId != null) {
            throw new CompatibilityException(
                    RouteCore.REASON_TIMEZONE_POLICY_NOT_APPLICABLE,
                    "timezonePolicyId is not applicable to LINEAR temporal trait"
            );
        }

        if (calendarTrait && !nonNullStrategy.dayMaskAware()) {
            throw new CompatibilityException(
                    RouteCore.REASON_TEMPORAL_CONFIG_INCOMPATIBLE,
                    "CALENDAR trait requires a day-mask-aware temporal strategy"
            );
        }

        if (linearTrait && nonNullStrategy.dayMaskAware()) {
            throw new CompatibilityException(
                    RouteCore.REASON_TEMPORAL_CONFIG_INCOMPATIBLE,
                    "LINEAR trait requires a day-mask-agnostic temporal strategy"
            );
        }
    }

    /**
     * Returns default Stage 16 compatibility policy.
     */
    public static TemporalPolicy defaults() {
        return new TemporalPolicy();
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
    }
}
