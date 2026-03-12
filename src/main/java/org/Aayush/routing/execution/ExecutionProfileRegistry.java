package org.Aayush.routing.execution;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable registry of named execution profiles.
 */
public final class ExecutionProfileRegistry {
    private final Map<String, ExecutionProfileSpec> profilesById;

    /**
     * Creates an empty registry.
     */
    public ExecutionProfileRegistry() {
        this.profilesById = Map.of();
    }

    /**
     * Creates a registry from explicit execution profiles keyed by {@code profileId}.
     */
    public ExecutionProfileRegistry(Collection<? extends ExecutionProfileSpec> profileSpecs) {
        LinkedHashMap<String, ExecutionProfileSpec> profiles = new LinkedHashMap<>();
        if (profileSpecs != null) {
            for (ExecutionProfileSpec profileSpec : profileSpecs) {
                ExecutionProfileSpec nonNullSpec = Objects.requireNonNull(profileSpec, "profileSpec");
                String profileId = normalizeRequiredId(nonNullSpec.getProfileId(), "profileSpec.profileId");
                ExecutionProfileSpec normalizedSpec = ExecutionProfileSpec.builder()
                        .profileId(profileId)
                        .algorithm(nonNullSpec.getAlgorithm())
                        .heuristicType(nonNullSpec.getHeuristicType())
                        .build();
                ExecutionProfileSpec previous = profiles.putIfAbsent(profileId, normalizedSpec);
                if (previous != null) {
                    throw new IllegalArgumentException("duplicate execution profile id: " + profileId);
                }
            }
        }
        this.profilesById = Map.copyOf(profiles);
    }

    /**
     * Returns one registered execution profile by id, or {@code null} when missing.
     */
    public ExecutionProfileSpec profile(String profileId) {
        String normalizedProfileId = normalizeOptionalId(profileId);
        if (normalizedProfileId == null) {
            return null;
        }
        return profilesById.get(normalizedProfileId);
    }

    /**
     * Returns immutable registered execution profile ids.
     */
    public Set<String> profileIds() {
        return profilesById.keySet();
    }

    /**
     * Returns an empty default registry.
     */
    public static ExecutionProfileRegistry defaultRegistry() {
        return new ExecutionProfileRegistry();
    }

    private static String normalizeRequiredId(String id, String fieldName) {
        String normalized = normalizeOptionalId(Objects.requireNonNull(id, fieldName));
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " must be non-blank");
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
}
