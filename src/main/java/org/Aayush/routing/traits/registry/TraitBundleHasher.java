package org.Aayush.routing.traits.registry;

import org.Aayush.routing.core.RouteCore;
import org.Aayush.routing.core.RouteCoreException;
import org.Aayush.routing.traits.temporal.ResolvedTemporalContext;
import org.Aayush.routing.traits.transition.ResolvedTransitionContext;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * Stable SHA-256 trait-bundle hasher for lineage and telemetry.
 */
public class TraitBundleHasher {
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final String NULL_TOKEN = "<null>";

    /**
     * Returns the canonical config string used to compute the bundle trait hash.
     */
    public String canonicalConfig(
            TraitBundleSpec bundleSpec,
            ResolvedTemporalContext temporalContext,
            ResolvedTransitionContext transitionContext
    ) {
        Objects.requireNonNull(bundleSpec, "bundleSpec");
        Objects.requireNonNull(temporalContext, "temporalContext");
        Objects.requireNonNull(transitionContext, "transitionContext");
        return String.join(
                "\n",
                "addressing_trait_id=" + token(bundleSpec.getAddressingTraitId()),
                "coordinate_strategy_id=" + token(bundleSpec.getCoordinateDistanceStrategyId()),
                "temporal_trait_id=" + token(bundleSpec.getTemporalTraitId()),
                "temporal_strategy_id=" + token(temporalContext.getTemporalStrategyId()),
                "timezone_policy_id=" + token(bundleSpec.getTimezonePolicyId()),
                "model_profile_timezone=" + token(bundleSpec.getModelProfileTimezone()),
                "zone_id=" + token(temporalContext.getZoneId()),
                "transition_trait_id=" + token(bundleSpec.getTransitionTraitId()),
                "transition_strategy_id=" + token(transitionContext.getTransitionStrategyId())
        );
    }

    /**
     * Computes the stable lowercase SHA-256 hash of one resolved bundle config.
     */
    public String hash(
            TraitBundleSpec bundleSpec,
            ResolvedTemporalContext temporalContext,
            ResolvedTransitionContext transitionContext
    ) {
        String canonicalConfig = canonicalConfig(bundleSpec, temporalContext, transitionContext);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return toHex(digest.digest(canonicalConfig.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new RouteCoreException(
                    RouteCore.REASON_TRAIT_HASH_GENERATION_FAILED,
                    "failed to initialize SHA-256 trait hash generator",
                    ex
            );
        } catch (RuntimeException ex) {
            throw new RouteCoreException(
                    RouteCore.REASON_TRAIT_HASH_GENERATION_FAILED,
                    "failed to hash trait bundle config: " + ex.getMessage(),
                    ex
            );
        }
    }

    private static String token(String value) {
        if (value == null) {
            return NULL_TOKEN;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? NULL_TOKEN : normalized;
    }

    private static String toHex(byte[] bytes) {
        char[] chars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xFF;
            chars[i * 2] = HEX[value >>> 4];
            chars[(i * 2) + 1] = HEX[value & 0x0F];
        }
        return new String(chars);
    }
}
