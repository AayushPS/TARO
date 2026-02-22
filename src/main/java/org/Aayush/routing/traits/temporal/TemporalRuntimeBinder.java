package org.Aayush.routing.traits.temporal;

import lombok.Builder;
import lombok.Value;
import org.Aayush.routing.core.RouteCore;
import org.Aayush.routing.core.RouteCoreException;

import java.time.ZoneId;
import java.util.Objects;

/**
 * Startup-only temporal runtime binder.
 *
 * <p>This component validates one runtime config, resolves trait/strategy/policy once,
 * and constructs immutable resolver/context objects used for all requests.</p>
 */
public final class TemporalRuntimeBinder {

    /**
     * Binds one temporal runtime config into immutable execution contracts.
     *
     * @param runtimeConfig temporal runtime configuration.
     * @param traitCatalog temporal trait catalog.
     * @param strategyRegistry temporal strategy registry.
     * @param timezonePolicyRegistry timezone policy registry.
     * @param temporalPolicy compatibility policy.
     * @return immutable temporal runtime binding.
     */
    public Binding bind(
            TemporalRuntimeConfig runtimeConfig,
            TemporalTraitCatalog traitCatalog,
            TemporalStrategyRegistry strategyRegistry,
            TemporalTimezonePolicyRegistry timezonePolicyRegistry,
            TemporalPolicy temporalPolicy
    ) {
        if (runtimeConfig == null) {
            throw new RouteCoreException(
                    RouteCore.REASON_TEMPORAL_CONFIG_REQUIRED,
                    "temporalRuntimeConfig must be provided at startup"
            );
        }
        TemporalRuntimeConfig nonNullConfig = runtimeConfig;
        TemporalTraitCatalog nonNullTraitCatalog = Objects.requireNonNull(traitCatalog, "traitCatalog");
        TemporalStrategyRegistry nonNullStrategyRegistry = Objects.requireNonNull(strategyRegistry, "strategyRegistry");
        TemporalTimezonePolicyRegistry nonNullTimezoneRegistry =
                Objects.requireNonNull(timezonePolicyRegistry, "timezonePolicyRegistry");
        TemporalPolicy nonNullTemporalPolicy = Objects.requireNonNull(temporalPolicy, "temporalPolicy");

        String traitId = normalizeRequiredId(
                nonNullConfig.getTemporalTraitId(),
                RouteCore.REASON_TEMPORAL_CONFIG_REQUIRED,
                "temporalTraitId must be provided"
        );
        TemporalTrait trait = nonNullTraitCatalog.trait(traitId);
        if (trait == null) {
            throw new RouteCoreException(
                    RouteCore.REASON_UNKNOWN_TEMPORAL_TRAIT,
                    "unknown temporal trait id: " + traitId
            );
        }

        String strategyId = normalizeRequiredId(
                trait.strategyId(),
                RouteCore.REASON_UNKNOWN_TEMPORAL_STRATEGY,
                "strategy id must be present for trait " + trait.id()
        );
        TemporalResolutionStrategy strategy = nonNullStrategyRegistry.strategy(strategyId);
        if (strategy == null) {
            throw new RouteCoreException(
                    RouteCore.REASON_UNKNOWN_TEMPORAL_STRATEGY,
                    "unknown temporal strategy id: " + strategyId + " for trait " + trait.id()
            );
        }

        String timezonePolicyId = normalizeOptionalId(nonNullConfig.getTimezonePolicyId());
        try {
            nonNullTemporalPolicy.validateCompatibility(trait, strategy, timezonePolicyId);
        } catch (TemporalPolicy.CompatibilityException ex) {
            throw new RouteCoreException(ex.reasonCode(), ex.getMessage(), ex);
        }

        ZoneId zoneId = null;
        TemporalTimezonePolicy timezonePolicy = null;
        if (timezonePolicyId != null) {
            timezonePolicy = nonNullTimezoneRegistry.policy(timezonePolicyId);
            if (timezonePolicy == null) {
                throw new RouteCoreException(
                        RouteCore.REASON_UNKNOWN_TIMEZONE_POLICY,
                        "unknown timezone policy id: " + timezonePolicyId
                );
            }
            try {
                zoneId = timezonePolicy.resolveZoneId(nonNullConfig);
            } catch (TemporalTimezonePolicy.PolicyResolutionException ex) {
                throw new RouteCoreException(ex.reasonCode(), ex.getMessage(), ex);
            } catch (RouteCoreException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                throw new RouteCoreException(
                        RouteCore.REASON_TEMPORAL_CONFIG_INCOMPATIBLE,
                        "timezone policy " + timezonePolicy.id() + " failed to resolve zone id: " + ex.getMessage(),
                        ex
                );
            }
        }

        if (strategy.dayMaskAware() && zoneId == null) {
            throw new RouteCoreException(
                    RouteCore.REASON_TEMPORAL_CONFIG_INCOMPATIBLE,
                    "day-mask-aware temporal strategy requires a resolved non-null zone id"
            );
        }

        TemporalOffsetCache offsetCache = null;
        if (TemporalTimezonePolicyRegistry.POLICY_MODEL_TIMEZONE.equals(timezonePolicyId)) {
            offsetCache = new TemporalOffsetCache(zoneId);
        }

        TemporalContextResolver resolver = new TemporalContextResolver(strategy, zoneId, offsetCache);
        ResolvedTemporalContext context = ResolvedTemporalContext.builder()
                .temporalTraitId(trait.id())
                .temporalStrategyId(strategy.id())
                .timezonePolicyId(timezonePolicyId)
                .zoneId(zoneId == null ? null : zoneId.getId())
                .dayMaskAware(strategy.dayMaskAware())
                .resolver(resolver)
                .build();
        TemporalTelemetry telemetry = TemporalTelemetry.builder()
                .temporalTraitId(trait.id())
                .temporalStrategyId(strategy.id())
                .timezonePolicyId(timezonePolicy == null ? null : timezonePolicy.id())
                .zoneId(zoneId == null ? null : zoneId.getId())
                .build();
        return Binding.builder()
                .resolvedTemporalContext(context)
                .temporalContextResolver(resolver)
                .temporalTelemetry(telemetry)
                .build();
    }

    private static String normalizeRequiredId(String id, String reasonCode, String message) {
        String normalized = normalizeOptionalId(id);
        if (normalized == null) {
            throw new RouteCoreException(reasonCode, message);
        }
        return normalized;
    }

    private static String normalizeOptionalId(String id) {
        if (id == null) {
            return null;
        }
        String normalized = id.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized;
    }

    /**
     * Immutable temporal runtime binding output.
     */
    @Value
    @Builder
    public static class Binding {
        /**
         * Locked temporal context attached to normalized requests.
         */
        ResolvedTemporalContext resolvedTemporalContext;

        /**
         * Locked resolver consumed in cost-engine hot path.
         */
        TemporalContextResolver temporalContextResolver;

        /**
         * Startup telemetry for bound temporal mode.
         */
        TemporalTelemetry temporalTelemetry;
    }
}
