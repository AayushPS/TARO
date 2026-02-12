package org.Aayush.routing.heuristic;

import lombok.experimental.UtilityClass;
import org.Aayush.routing.graph.EdgeGraph;
import org.Aayush.routing.profile.ProfileStore;

import java.util.Objects;

/**
 * Deterministic compatibility signature for Stage 12 landmark artifacts.
 */
@UtilityClass
final class LandmarkCompatibility {
    private static final long FNV64_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV64_PRIME = 0x100000001b3L;
    private static final int DAYS_PER_WEEK = 7;

    /**
     * Computes deterministic FNV-1a signature over graph/profile lower-bound contracts.
     */
    static long computeSignature(EdgeGraph edgeGraph, ProfileStore profileStore) {
        Objects.requireNonNull(edgeGraph, "edgeGraph");
        Objects.requireNonNull(profileStore, "profileStore");

        long hash = FNV64_OFFSET_BASIS;
        hash = mixInt(hash, edgeGraph.nodeCount());
        hash = mixInt(hash, edgeGraph.edgeCount());

        for (int edgeId = 0; edgeId < edgeGraph.edgeCount(); edgeId++) {
            float baseWeight = edgeGraph.getBaseWeight(edgeId);
            if (!Float.isFinite(baseWeight) || baseWeight < 0.0f) {
                throw new IllegalArgumentException(
                        "edge " + edgeId + " has invalid base weight for signature: " + baseWeight
                );
            }
            float minimumTemporal = minimumTemporalMultiplier(profileStore, edgeGraph.getProfileId(edgeId));
            if (!Float.isFinite(minimumTemporal) || minimumTemporal <= 0.0f) {
                throw new IllegalArgumentException(
                        "edge " + edgeId + " has invalid temporal minimum for signature: " + minimumTemporal
                );
            }

            double lowerBound = (double) baseWeight * minimumTemporal;
            float lowerBoundWeight = !Double.isFinite(lowerBound) || lowerBound >= Float.MAX_VALUE
                    ? Float.MAX_VALUE
                    : (float) lowerBound;

            hash = mixInt(hash, edgeGraph.getEdgeOrigin(edgeId));
            hash = mixInt(hash, edgeGraph.getEdgeDestination(edgeId));
            hash = mixInt(hash, Float.floatToIntBits(lowerBoundWeight));
        }

        return hash;
    }

    /**
     * Returns minimum temporal multiplier over all weekdays for one profile id.
     */
    private static float minimumTemporalMultiplier(ProfileStore profileStore, int profileId) {
        float minimum = Float.POSITIVE_INFINITY;
        for (int day = 0; day < DAYS_PER_WEEK; day++) {
            int selectedProfileId = profileStore.selectProfileForDay(profileId, day);
            float dayMinimum = selectedProfileId == ProfileStore.DEFAULT_PROFILE_ID
                    ? ProfileStore.DEFAULT_MULTIPLIER
                    : profileStore.getMetadata(selectedProfileId).minMultiplier();
            if (dayMinimum < minimum) {
                minimum = dayMinimum;
            }
        }
        return minimum;
    }

    /**
     * Mixes one integer into FNV-1a state byte-by-byte in little-endian order.
     */
    private static long mixInt(long hash, int value) {
        hash ^= (value & 0xFF);
        hash *= FNV64_PRIME;
        hash ^= ((value >>> 8) & 0xFF);
        hash *= FNV64_PRIME;
        hash ^= ((value >>> 16) & 0xFF);
        hash *= FNV64_PRIME;
        hash ^= ((value >>> 24) & 0xFF);
        hash *= FNV64_PRIME;
        return hash;
    }
}
