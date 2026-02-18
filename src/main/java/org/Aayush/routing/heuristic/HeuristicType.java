package org.Aayush.routing.heuristic;

/**
 * Supported heuristic modes.
 *
 * <p>{@code NONE} disables heuristic guidance (pure Dijkstra behavior).</p>
 * <p>{@code EUCLIDEAN} and {@code SPHERICAL} derive admissible lower bounds from geometry.</p>
 * <p>{@code LANDMARK} uses ALT lower bounds from precomputed landmark distances.</p>
 */
public enum HeuristicType {
    NONE,
    EUCLIDEAN,
    SPHERICAL,
    LANDMARK
}
