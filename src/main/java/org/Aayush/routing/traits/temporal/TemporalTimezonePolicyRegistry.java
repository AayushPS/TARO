package org.Aayush.routing.traits.temporal;

import org.Aayush.routing.core.RouteCore;
import org.Aayush.serialization.flatbuffers.ModelContractValidator;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable registry for Stage 16 timezone policies.
 */
public final class TemporalTimezonePolicyRegistry {
    public static final String POLICY_UTC = "UTC";
    public static final String POLICY_MODEL_TIMEZONE = "MODEL_TIMEZONE";

    private static final TemporalTimezonePolicy UTC_POLICY = new UtcTimezonePolicy();
    private static final TemporalTimezonePolicy MODEL_TIMEZONE_POLICY = new ModelTimezonePolicy();

    private final Map<String, TemporalTimezonePolicy> policiesById;

    /**
     * Creates a registry with built-in policies only.
     */
    public TemporalTimezonePolicyRegistry() {
        this.policiesById = Map.copyOf(materialize(defaultPolicies()));
    }

    /**
     * Creates a registry by merging built-ins with custom policies.
     *
     * <p>Custom policy ids override built-ins when ids collide.</p>
     */
    public TemporalTimezonePolicyRegistry(Collection<? extends TemporalTimezonePolicy> customPolicies) {
        this.policiesById = Map.copyOf(materialize(mergeWithBuiltIns(customPolicies)));
    }

    /**
     * Creates an explicit registry from provided policies.
     */
    public TemporalTimezonePolicyRegistry(Collection<? extends TemporalTimezonePolicy> policies, boolean includeBuiltIns) {
        Collection<? extends TemporalTimezonePolicy> source = includeBuiltIns ? mergeWithBuiltIns(policies) : policies;
        this.policiesById = Map.copyOf(materialize(source));
    }

    /**
     * Returns timezone policy by id, or {@code null} when not registered.
     */
    public TemporalTimezonePolicy policy(String policyId) {
        if (policyId == null) {
            return null;
        }
        return policiesById.get(policyId);
    }

    /**
     * Returns immutable set of registered policy ids.
     */
    public Set<String> policyIds() {
        return policiesById.keySet();
    }

    /**
     * Returns default timezone policy registry.
     */
    public static TemporalTimezonePolicyRegistry defaultRegistry() {
        return new TemporalTimezonePolicyRegistry();
    }

    private static Collection<? extends TemporalTimezonePolicy> defaultPolicies() {
        return java.util.List.of(UTC_POLICY, MODEL_TIMEZONE_POLICY);
    }

    private static Collection<? extends TemporalTimezonePolicy> mergeWithBuiltIns(
            Collection<? extends TemporalTimezonePolicy> customPolicies
    ) {
        LinkedHashMap<String, TemporalTimezonePolicy> merged = new LinkedHashMap<>();
        for (TemporalTimezonePolicy policy : defaultPolicies()) {
            merged.put(policy.id(), policy);
        }
        if (customPolicies != null) {
            for (TemporalTimezonePolicy policy : customPolicies) {
                TemporalTimezonePolicy nonNullPolicy = Objects.requireNonNull(policy, "policy");
                merged.put(normalizeRequiredId(nonNullPolicy.id(), "policy.id"), nonNullPolicy);
            }
        }
        return merged.values();
    }

    private static LinkedHashMap<String, TemporalTimezonePolicy> materialize(
            Collection<? extends TemporalTimezonePolicy> policies
    ) {
        Objects.requireNonNull(policies, "policies");
        LinkedHashMap<String, TemporalTimezonePolicy> map = new LinkedHashMap<>();
        for (TemporalTimezonePolicy policy : policies) {
            TemporalTimezonePolicy nonNullPolicy = Objects.requireNonNull(policy, "policy");
            map.put(normalizeRequiredId(nonNullPolicy.id(), "policy.id"), nonNullPolicy);
        }
        return map;
    }

    private static String normalizeRequiredId(String id, String fieldName) {
        String normalized = Objects.requireNonNull(id, fieldName).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must be non-blank");
        }
        return normalized;
    }

    private static final class UtcTimezonePolicy implements TemporalTimezonePolicy {
        @Override
        public String id() {
            return POLICY_UTC;
        }

        @Override
        public ZoneId resolveZoneId(TemporalRuntimeConfig runtimeConfig) {
            return ZoneOffset.UTC;
        }
    }

    private static final class ModelTimezonePolicy implements TemporalTimezonePolicy {
        @Override
        public String id() {
            return POLICY_MODEL_TIMEZONE;
        }

        @Override
        public ZoneId resolveZoneId(TemporalRuntimeConfig runtimeConfig) {
            Objects.requireNonNull(runtimeConfig, "runtimeConfig");
            String timezoneId = runtimeConfig.getModelProfileTimezone();
            if (timezoneId == null || timezoneId.isBlank()) {
                throw new TemporalTimezonePolicy.PolicyResolutionException(
                        RouteCore.REASON_MODEL_TIMEZONE_REQUIRED,
                        "metadata.profile_timezone is required for MODEL_TIMEZONE policy"
                );
            }
            try {
                return ModelContractValidator.parseProfileTimezone(timezoneId, "TemporalRuntimeConfig.modelProfileTimezone");
            } catch (IllegalArgumentException ex) {
                throw new TemporalTimezonePolicy.PolicyResolutionException(
                        RouteCore.REASON_INVALID_MODEL_TIMEZONE,
                        ex.getMessage(),
                        ex
                );
            }
        }
    }
}
