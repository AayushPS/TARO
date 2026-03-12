package org.Aayush.routing.execution;

import org.Aayush.routing.core.RouteCore;
import org.Aayush.routing.core.RouteCoreException;
import org.Aayush.routing.core.RoutingAlgorithm;
import org.Aayush.routing.heuristic.HeuristicConfigurationException;
import org.Aayush.routing.heuristic.HeuristicProvider;
import org.Aayush.routing.heuristic.HeuristicProviderFactory;
import org.Aayush.routing.heuristic.HeuristicType;

import java.util.Objects;

/**
 * Default startup binder for execution-profile selection.
 */
public final class DefaultExecutionRuntimeBinder implements ExecutionRuntimeBinder {
    public static final String CONFIG_SOURCE_NAMED_PROFILE = "NAMED_PROFILE";
    public static final String CONFIG_SOURCE_INLINE_PROFILE = "INLINE_PROFILE";

    /**
     * Binds one execution profile into immutable runtime state.
     */
    @Override
    public Binding bind(BindInput input) {
        BindInput nonNullInput = Objects.requireNonNull(input, "input");
        ExecutionRuntimeConfig runtimeConfig = nonNullInput.getExecutionRuntimeConfig();
        if (runtimeConfig == null) {
            throw new RouteCoreException(
                    RouteCore.REASON_EXECUTION_CONFIG_REQUIRED,
                    "executionRuntimeConfig must be provided at startup"
            );
        }

        String profileId = normalizeOptionalId(runtimeConfig.getExecutionProfileId());
        ExecutionProfileSpec inlineSpec = runtimeConfig.getInlineExecutionProfileSpec();
        if (profileId != null && inlineSpec != null) {
            throw new RouteCoreException(
                    RouteCore.REASON_EXECUTION_CONFIG_CONFLICT,
                    "executionProfileId and inlineExecutionProfileSpec cannot both be provided"
            );
        }

        ExecutionProfileSpec resolvedSpec;
        String configSource;
        if (profileId != null) {
            ExecutionProfileRegistry registry = nonNullInput.getExecutionProfileRegistry() == null
                    ? ExecutionProfileRegistry.defaultRegistry()
                    : nonNullInput.getExecutionProfileRegistry();
            resolvedSpec = registry.profile(profileId);
            if (resolvedSpec == null) {
                throw new RouteCoreException(
                        RouteCore.REASON_UNKNOWN_EXECUTION_PROFILE,
                        "unknown execution profile id: " + profileId
                );
            }
            configSource = CONFIG_SOURCE_NAMED_PROFILE;
        } else if (inlineSpec != null) {
            resolvedSpec = inlineSpec;
            configSource = CONFIG_SOURCE_INLINE_PROFILE;
        } else {
            throw new RouteCoreException(
                    RouteCore.REASON_EXECUTION_CONFIG_REQUIRED,
                    "either executionProfileId or inlineExecutionProfileSpec must be provided"
            );
        }

        RoutingAlgorithm algorithm = Objects.requireNonNull(
                resolvedSpec.getAlgorithm(),
                "executionProfileSpec.algorithm"
        );
        HeuristicType heuristicType = Objects.requireNonNull(
                resolvedSpec.getHeuristicType(),
                "executionProfileSpec.heuristicType"
        );
        if (algorithm == RoutingAlgorithm.DIJKSTRA && heuristicType != HeuristicType.NONE) {
            throw new RouteCoreException(
                    RouteCore.REASON_EXECUTION_PROFILE_INCOMPATIBLE,
                    "DIJKSTRA execution profile requires heuristicType NONE, got " + heuristicType
            );
        }

        HeuristicProviderFactory heuristicProviderFactory = Objects.requireNonNull(
                nonNullInput.getHeuristicProviderFactory(),
                "heuristicProviderFactory"
        );
        final HeuristicProvider heuristicProvider;
        try {
            heuristicProvider = heuristicProviderFactory.create(
                    heuristicType,
                    Objects.requireNonNull(nonNullInput.getEdgeGraph(), "edgeGraph"),
                    Objects.requireNonNull(nonNullInput.getProfileStore(), "profileStore"),
                    Objects.requireNonNull(nonNullInput.getCostEngine(), "costEngine"),
                    nonNullInput.getLandmarkStore()
            );
        } catch (HeuristicConfigurationException ex) {
            throw new RouteCoreException(
                    RouteCore.REASON_EXECUTION_PROFILE_INCOMPATIBLE,
                    "failed to initialize startup heuristic " + heuristicType + ": " + ex.getMessage(),
                    ex
            );
        }

        return Binding.builder()
                .resolvedExecutionProfileContext(ResolvedExecutionProfileContext.builder()
                        .profileId(profileId)
                        .configSource(configSource)
                        .algorithm(algorithm)
                        .heuristicType(heuristicType)
                        .build())
                .heuristicProvider(heuristicProvider)
                .build();
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
}
