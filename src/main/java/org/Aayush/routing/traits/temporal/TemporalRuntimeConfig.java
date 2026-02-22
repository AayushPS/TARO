package org.Aayush.routing.traits.temporal;

import lombok.Builder;
import lombok.Value;

/**
 * Runtime configuration used to bind Stage 16 temporal behavior once at startup.
 */
@Value
@Builder
public class TemporalRuntimeConfig {

    /**
     * Selected temporal trait id (for example {@code LINEAR} or {@code CALENDAR}).
     */
    String temporalTraitId;

    /**
     * Selected timezone policy id.
     *
     * <p>This must be omitted for {@code LINEAR} and required for {@code CALENDAR}.</p>
     */
    String timezonePolicyId;

    /**
     * Model timezone metadata value used by {@code MODEL_TIMEZONE} policy.
     *
     * <p>This should be the value of {@code metadata.profile_timezone}.</p>
     */
    String modelProfileTimezone;

    /**
     * Returns convenience runtime config for {@code LINEAR}.
     */
    public static TemporalRuntimeConfig linear() {
        return TemporalRuntimeConfig.builder()
                .temporalTraitId(TemporalTraitCatalog.TRAIT_LINEAR)
                .build();
    }

    /**
     * Returns convenience runtime config for {@code CALENDAR + UTC}.
     */
    public static TemporalRuntimeConfig calendarUtc() {
        return TemporalRuntimeConfig.builder()
                .temporalTraitId(TemporalTraitCatalog.TRAIT_CALENDAR)
                .timezonePolicyId(TemporalTimezonePolicyRegistry.POLICY_UTC)
                .build();
    }

    /**
     * Returns convenience runtime config for {@code CALENDAR + MODEL_TIMEZONE}.
     *
     * @param modelProfileTimezone timezone id from model metadata.
     */
    public static TemporalRuntimeConfig calendarModelTimezone(String modelProfileTimezone) {
        return TemporalRuntimeConfig.builder()
                .temporalTraitId(TemporalTraitCatalog.TRAIT_CALENDAR)
                .timezonePolicyId(TemporalTimezonePolicyRegistry.POLICY_MODEL_TIMEZONE)
                .modelProfileTimezone(modelProfileTimezone)
                .build();
    }
}
