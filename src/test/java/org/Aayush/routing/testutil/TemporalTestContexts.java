package org.Aayush.routing.testutil;

import org.Aayush.routing.traits.temporal.ResolvedTemporalContext;
import org.Aayush.routing.traits.temporal.TemporalPolicy;
import org.Aayush.routing.traits.temporal.TemporalRuntimeBinder;
import org.Aayush.routing.traits.temporal.TemporalRuntimeConfig;
import org.Aayush.routing.traits.temporal.TemporalStrategyRegistry;
import org.Aayush.routing.traits.temporal.TemporalTimezonePolicyRegistry;
import org.Aayush.routing.traits.temporal.TemporalTraitCatalog;

/**
 * Shared temporal contexts for tests that call low-level planner/cost APIs directly.
 */
public final class TemporalTestContexts {
    private static final ResolvedTemporalContext CALENDAR_UTC = bind(TemporalRuntimeConfig.calendarUtc());
    private static final ResolvedTemporalContext LINEAR = bind(TemporalRuntimeConfig.linear());

    private TemporalTestContexts() {
    }

    public static ResolvedTemporalContext calendarUtc() {
        return CALENDAR_UTC;
    }

    public static ResolvedTemporalContext linear() {
        return LINEAR;
    }

    private static ResolvedTemporalContext bind(TemporalRuntimeConfig runtimeConfig) {
        return new TemporalRuntimeBinder().bind(
                runtimeConfig,
                TemporalTraitCatalog.defaultCatalog(),
                TemporalStrategyRegistry.defaultRegistry(),
                TemporalTimezonePolicyRegistry.defaultRegistry(),
                TemporalPolicy.defaults()
        ).getResolvedTemporalContext();
    }
}
