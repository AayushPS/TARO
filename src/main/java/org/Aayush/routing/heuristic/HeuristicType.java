package org.Aayush.routing.heuristic;

/**
 * Stage 11+ heuristic modes.
 *
 * <p>Stage 11 modes: {@code NONE}, {@code EUCLIDEAN}, {@code SPHERICAL}.</p>
 * <p>Stage 12 adds {@code LANDMARK} (ALT).</p>
 */
public enum HeuristicType {
    NONE,
    EUCLIDEAN,
    SPHERICAL,
    LANDMARK
}
