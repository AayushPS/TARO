package org.Aayush.routing.traits.registry;

import lombok.Builder;
import lombok.Value;
import org.Aayush.routing.core.RouteCore;
import org.Aayush.routing.core.RouteCoreException;
import org.Aayush.routing.traits.addressing.AddressingRuntimeBinder;
import org.Aayush.routing.traits.addressing.AddressingRuntimeConfig;
import org.Aayush.routing.traits.addressing.AddressingTraitCatalog;
import org.Aayush.routing.traits.addressing.CoordinateStrategyRegistry;
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
import org.Aayush.routing.traits.transition.TransitionPolicy;
import org.Aayush.routing.traits.transition.TransitionRuntimeBinder;
import org.Aayush.routing.traits.transition.TransitionRuntimeConfig;
import org.Aayush.routing.traits.transition.TransitionStrategyRegistry;
import org.Aayush.routing.traits.transition.TransitionTelemetry;
import org.Aayush.routing.traits.transition.TransitionTraitCatalog;

import java.util.Objects;

/**
 * Startup binder that resolves one Stage 18 trait bundle into immutable runtime contracts.
 */
public final class TraitBundleRuntimeBinder {
    public static final String CONFIG_SOURCE_NAMED_BUNDLE = "NAMED_BUNDLE";
    public static final String CONFIG_SOURCE_INLINE_BUNDLE = "INLINE_BUNDLE";
    public static final String CONFIG_SOURCE_LEGACY_AXIS_CONFIGS = "LEGACY_AXIS_CONFIGS";

    /**
     * Resolves startup bundle selection and composes Stage 15/16/17 binders.
     */
    public Binding bind(BindInput input) {
        BindInput nonNullInput = Objects.requireNonNull(input, "input");

        AddressingTraitCatalog addressingTraitCatalog = nonNullInput.addressingTraitCatalog == null
                ? AddressingTraitCatalog.defaultCatalog()
                : nonNullInput.addressingTraitCatalog;
        CoordinateStrategyRegistry coordinateStrategyRegistry = nonNullInput.coordinateStrategyRegistry == null
                ? CoordinateStrategyRegistry.defaultRegistry()
                : nonNullInput.coordinateStrategyRegistry;
        TemporalTraitCatalog temporalTraitCatalog = nonNullInput.temporalTraitCatalog == null
                ? TemporalTraitCatalog.defaultCatalog()
                : nonNullInput.temporalTraitCatalog;
        TemporalStrategyRegistry temporalStrategyRegistry = nonNullInput.temporalStrategyRegistry == null
                ? TemporalStrategyRegistry.defaultRegistry()
                : nonNullInput.temporalStrategyRegistry;
        TemporalTimezonePolicyRegistry temporalTimezonePolicyRegistry = nonNullInput.temporalTimezonePolicyRegistry == null
                ? TemporalTimezonePolicyRegistry.defaultRegistry()
                : nonNullInput.temporalTimezonePolicyRegistry;
        TemporalPolicy temporalPolicy = nonNullInput.temporalPolicy == null
                ? TemporalPolicy.defaults()
                : nonNullInput.temporalPolicy;
        TransitionTraitCatalog transitionTraitCatalog = nonNullInput.transitionTraitCatalog == null
                ? TransitionTraitCatalog.defaultCatalog()
                : nonNullInput.transitionTraitCatalog;
        TransitionStrategyRegistry transitionStrategyRegistry = nonNullInput.transitionStrategyRegistry == null
                ? TransitionStrategyRegistry.defaultRegistry()
                : nonNullInput.transitionStrategyRegistry;
        TransitionPolicy transitionPolicy = nonNullInput.transitionPolicy == null
                ? TransitionPolicy.defaults()
                : nonNullInput.transitionPolicy;
        AddressingRuntimeBinder addressingRuntimeBinder = nonNullInput.addressingRuntimeBinder == null
                ? new AddressingRuntimeBinder()
                : nonNullInput.addressingRuntimeBinder;
        TemporalRuntimeBinder temporalRuntimeBinder = nonNullInput.temporalRuntimeBinder == null
                ? new TemporalRuntimeBinder()
                : nonNullInput.temporalRuntimeBinder;
        TransitionRuntimeBinder transitionRuntimeBinder = nonNullInput.transitionRuntimeBinder == null
                ? new TransitionRuntimeBinder()
                : nonNullInput.transitionRuntimeBinder;
        TraitBundleCompatibilityPolicy compatibilityPolicy = nonNullInput.traitBundleCompatibilityPolicy == null
                ? TraitBundleCompatibilityPolicy.defaults()
                : nonNullInput.traitBundleCompatibilityPolicy;
        TraitBundleHasher traitBundleHasher = nonNullInput.traitBundleHasher == null
                ? new TraitBundleHasher()
                : nonNullInput.traitBundleHasher;

        BundleSelection bundleSelection = selectBundle(nonNullInput);
        compatibilityPolicy.validate(bundleSelection.spec(), addressingTraitCatalog);

        AddressingRuntimeBinder.Binding addressingBinding = addressingRuntimeBinder.bind(
                bundleSelection.spec().toAddressingRuntimeConfig(),
                addressingTraitCatalog,
                coordinateStrategyRegistry
        );
        TemporalRuntimeBinder.Binding temporalBinding = temporalRuntimeBinder.bind(
                bundleSelection.spec().toTemporalRuntimeConfig(),
                temporalTraitCatalog,
                temporalStrategyRegistry,
                temporalTimezonePolicyRegistry,
                temporalPolicy
        );
        TransitionRuntimeBinder.Binding transitionBinding = transitionRuntimeBinder.bind(
                bundleSelection.spec().toTransitionRuntimeConfig(),
                transitionTraitCatalog,
                transitionStrategyRegistry,
                transitionPolicy
        );

        ResolvedTemporalContext temporalContext = temporalBinding.getResolvedTemporalContext();
        ResolvedTransitionContext transitionContext = transitionBinding.getResolvedTransitionContext();
        final String traitHash;
        try {
            traitHash = traitBundleHasher.hash(bundleSelection.spec(), temporalContext, transitionContext);
        } catch (RouteCoreException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new RouteCoreException(
                    RouteCore.REASON_TRAIT_HASH_GENERATION_FAILED,
                    "failed to hash resolved trait bundle: " + ex.getMessage(),
                    ex
            );
        }

        ResolvedTraitBundleContext resolvedTraitBundleContext = ResolvedTraitBundleContext.builder()
                .bundleId(bundleSelection.bundleId())
                .configSource(bundleSelection.configSource())
                .addressingTraitId(addressingBinding.getAddressingTrait().id())
                .coordinateDistanceStrategyId(addressingBinding.getCoordinateStrategyId())
                .temporalTraitId(temporalContext.getTemporalTraitId())
                .temporalStrategyId(temporalContext.getTemporalStrategyId())
                .timezonePolicyId(temporalContext.getTimezonePolicyId())
                .modelProfileTimezone(bundleSelection.spec().getModelProfileTimezone())
                .zoneId(temporalContext.getZoneId())
                .transitionTraitId(transitionContext.getTransitionTraitId())
                .transitionStrategyId(transitionContext.getTransitionStrategyId())
                .traitHash(traitHash)
                .build();
        TraitBundleTelemetry traitBundleTelemetry = TraitBundleTelemetry.builder()
                .bundleId(bundleSelection.bundleId())
                .configSource(bundleSelection.configSource())
                .addressingTraitId(addressingBinding.getAddressingTrait().id())
                .coordinateDistanceStrategyId(addressingBinding.getCoordinateStrategyId())
                .temporalTraitId(temporalContext.getTemporalTraitId())
                .temporalStrategyId(temporalContext.getTemporalStrategyId())
                .timezonePolicyId(temporalContext.getTimezonePolicyId())
                .modelProfileTimezone(bundleSelection.spec().getModelProfileTimezone())
                .zoneId(temporalContext.getZoneId())
                .transitionTraitId(transitionContext.getTransitionTraitId())
                .transitionStrategyId(transitionContext.getTransitionStrategyId())
                .traitHash(traitHash)
                .build();

        return Binding.builder()
                .addressingRuntimeBinding(addressingBinding)
                .temporalContextResolver(temporalBinding.getTemporalContextResolver())
                .resolvedTemporalContext(temporalContext)
                .temporalTelemetry(temporalBinding.getTemporalTelemetry())
                .resolvedTransitionContext(transitionContext)
                .transitionTelemetry(transitionBinding.getTransitionTelemetry())
                .resolvedTraitBundleContext(resolvedTraitBundleContext)
                .traitBundleTelemetry(traitBundleTelemetry)
                .build();
    }

    private BundleSelection selectBundle(BindInput input) {
        TraitBundleRuntimeConfig bundleRuntimeConfig = input.traitBundleRuntimeConfig;
        boolean hasAnyLegacyConfig = input.addressingRuntimeConfig != null
                || input.temporalRuntimeConfig != null
                || input.transitionRuntimeConfig != null;
        boolean hasCompleteLegacyConfig = input.addressingRuntimeConfig != null
                && input.temporalRuntimeConfig != null
                && input.transitionRuntimeConfig != null;

        if (bundleRuntimeConfig != null) {
            BundleSelection bundleSelection = resolveBundleRuntimeConfig(
                    bundleRuntimeConfig,
                    input.traitBundleRegistry == null ? TraitBundleRegistry.defaultRegistry() : input.traitBundleRegistry
            );
            if (hasAnyLegacyConfig) {
                if (!hasCompleteLegacyConfig) {
                    throw new RouteCoreException(
                            RouteCore.REASON_TRAIT_BUNDLE_CONFIG_CONFLICT,
                            "traitBundleRuntimeConfig cannot be combined with partial legacy trait-axis configs"
                    );
                }
                TraitBundleSpec legacyBundleSpec = normalizeSpec(TraitBundleSpec.fromLegacyConfigs(
                        input.addressingRuntimeConfig,
                        input.temporalRuntimeConfig,
                        input.transitionRuntimeConfig
                ));
                if (!sameSelection(bundleSelection.spec(), legacyBundleSpec)) {
                    throw new RouteCoreException(
                            RouteCore.REASON_TRAIT_BUNDLE_CONFIG_CONFLICT,
                            "traitBundleRuntimeConfig conflicts with legacy addressing/temporal/transition runtime configs"
                    );
                }
            }
            return bundleSelection;
        }

        if (hasCompleteLegacyConfig) {
            return new BundleSelection(
                    null,
                    CONFIG_SOURCE_LEGACY_AXIS_CONFIGS,
                    normalizeSpec(TraitBundleSpec.fromLegacyConfigs(
                            input.addressingRuntimeConfig,
                            input.temporalRuntimeConfig,
                            input.transitionRuntimeConfig
                    ))
            );
        }

        if (input.temporalRuntimeConfig == null) {
            throw new RouteCoreException(
                    RouteCore.REASON_TEMPORAL_CONFIG_REQUIRED,
                    "temporalRuntimeConfig must be provided at startup"
            );
        }
        if (input.transitionRuntimeConfig == null) {
            throw new RouteCoreException(
                    RouteCore.REASON_TRANSITION_CONFIG_REQUIRED,
                    "transitionRuntimeConfig must be provided at startup"
            );
        }
        if (input.addressingRuntimeConfig == null) {
            throw new RouteCoreException(
                    RouteCore.REASON_ADDRESSING_CONFIG_REQUIRED,
                    "addressingRuntimeConfig must be provided at startup"
            );
        }
        throw new RouteCoreException(
                RouteCore.REASON_TRAIT_BUNDLE_CONFIG_REQUIRED,
                "traitBundleRuntimeConfig must select a named or inline bundle"
        );
    }

    private BundleSelection resolveBundleRuntimeConfig(
            TraitBundleRuntimeConfig bundleRuntimeConfig,
            TraitBundleRegistry traitBundleRegistry
    ) {
        String bundleId = normalizeOptionalId(bundleRuntimeConfig.getTraitBundleId());
        TraitBundleSpec inlineSpec = bundleRuntimeConfig.getInlineTraitBundleSpec();
        TraitBundleSpec namedSpec = null;
        if (bundleId != null) {
            namedSpec = traitBundleRegistry.bundle(bundleId);
            if (namedSpec == null) {
                throw new RouteCoreException(
                        RouteCore.REASON_UNKNOWN_TRAIT_BUNDLE,
                        "unknown trait bundle id: " + bundleId
                );
            }
        }

        if (namedSpec == null && inlineSpec == null) {
            throw new RouteCoreException(
                    RouteCore.REASON_TRAIT_BUNDLE_CONFIG_REQUIRED,
                    "traitBundleRuntimeConfig must provide either traitBundleId or inlineTraitBundleSpec"
            );
        }

        if (namedSpec != null && inlineSpec != null) {
            TraitBundleSpec normalizedNamed = normalizeSpec(namedSpec);
            TraitBundleSpec normalizedInline = normalizeSpec(inlineSpec);
            if (!sameSelection(normalizedNamed, normalizedInline)) {
                throw new RouteCoreException(
                        RouteCore.REASON_TRAIT_BUNDLE_CONFIG_CONFLICT,
                        "traitBundleId and inlineTraitBundleSpec must resolve to the same selection"
                );
            }
            return new BundleSelection(bundleId, CONFIG_SOURCE_NAMED_BUNDLE, withBundleId(normalizedNamed, bundleId));
        }

        if (namedSpec != null) {
            return new BundleSelection(bundleId, CONFIG_SOURCE_NAMED_BUNDLE, withBundleId(normalizeSpec(namedSpec), bundleId));
        }

        TraitBundleSpec normalizedInline = normalizeSpec(inlineSpec);
        String inlineBundleId = normalizeOptionalId(normalizedInline.getBundleId());
        return new BundleSelection(inlineBundleId, CONFIG_SOURCE_INLINE_BUNDLE, normalizedInline);
    }

    private static TraitBundleSpec normalizeSpec(TraitBundleSpec bundleSpec) {
        return TraitBundleSpec.builder()
                .bundleId(normalizeOptionalId(bundleSpec.getBundleId()))
                .addressingTraitId(normalizeOptionalId(bundleSpec.getAddressingTraitId()))
                .coordinateDistanceStrategyId(normalizeOptionalId(bundleSpec.getCoordinateDistanceStrategyId()))
                .temporalTraitId(normalizeOptionalId(bundleSpec.getTemporalTraitId()))
                .timezonePolicyId(normalizeOptionalId(bundleSpec.getTimezonePolicyId()))
                .modelProfileTimezone(normalizeOptionalId(bundleSpec.getModelProfileTimezone()))
                .transitionTraitId(normalizeOptionalId(bundleSpec.getTransitionTraitId()))
                .build();
    }

    private static TraitBundleSpec withBundleId(TraitBundleSpec bundleSpec, String bundleId) {
        return TraitBundleSpec.builder()
                .bundleId(bundleId)
                .addressingTraitId(bundleSpec.getAddressingTraitId())
                .coordinateDistanceStrategyId(bundleSpec.getCoordinateDistanceStrategyId())
                .temporalTraitId(bundleSpec.getTemporalTraitId())
                .timezonePolicyId(bundleSpec.getTimezonePolicyId())
                .modelProfileTimezone(bundleSpec.getModelProfileTimezone())
                .transitionTraitId(bundleSpec.getTransitionTraitId())
                .build();
    }

    private static boolean sameSelection(TraitBundleSpec left, TraitBundleSpec right) {
        return Objects.equals(left.getAddressingTraitId(), right.getAddressingTraitId())
                && Objects.equals(left.getCoordinateDistanceStrategyId(), right.getCoordinateDistanceStrategyId())
                && Objects.equals(left.getTemporalTraitId(), right.getTemporalTraitId())
                && Objects.equals(left.getTimezonePolicyId(), right.getTimezonePolicyId())
                && Objects.equals(left.getModelProfileTimezone(), right.getModelProfileTimezone())
                && Objects.equals(left.getTransitionTraitId(), right.getTransitionTraitId());
    }

    private static String normalizeOptionalId(String id) {
        if (id == null) {
            return null;
        }
        String normalized = id.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private record BundleSelection(String bundleId, String configSource, TraitBundleSpec spec) {
    }

    /**
     * Startup input carrying both Stage 18 bundle config and Stage 15-17 compatibility injections.
     */
    @Value
    @Builder
    public static class BindInput {
        TraitBundleRuntimeConfig traitBundleRuntimeConfig;
        TraitBundleRegistry traitBundleRegistry;
        AddressingRuntimeConfig addressingRuntimeConfig;
        TemporalRuntimeConfig temporalRuntimeConfig;
        TransitionRuntimeConfig transitionRuntimeConfig;
        AddressingTraitCatalog addressingTraitCatalog;
        CoordinateStrategyRegistry coordinateStrategyRegistry;
        AddressingRuntimeBinder addressingRuntimeBinder;
        TemporalTraitCatalog temporalTraitCatalog;
        TemporalStrategyRegistry temporalStrategyRegistry;
        TemporalTimezonePolicyRegistry temporalTimezonePolicyRegistry;
        TemporalPolicy temporalPolicy;
        TemporalRuntimeBinder temporalRuntimeBinder;
        TransitionTraitCatalog transitionTraitCatalog;
        TransitionStrategyRegistry transitionStrategyRegistry;
        TransitionPolicy transitionPolicy;
        TransitionRuntimeBinder transitionRuntimeBinder;
        TraitBundleCompatibilityPolicy traitBundleCompatibilityPolicy;
        TraitBundleHasher traitBundleHasher;
    }

    /**
     * Immutable resolved startup binding spanning Stage 15/16/17 and Stage 18 artifacts.
     */
    @Value
    @Builder
    public static class Binding {
        AddressingRuntimeBinder.Binding addressingRuntimeBinding;
        TemporalContextResolver temporalContextResolver;
        ResolvedTemporalContext resolvedTemporalContext;
        TemporalTelemetry temporalTelemetry;
        ResolvedTransitionContext resolvedTransitionContext;
        TransitionTelemetry transitionTelemetry;
        ResolvedTraitBundleContext resolvedTraitBundleContext;
        TraitBundleTelemetry traitBundleTelemetry;
    }
}
