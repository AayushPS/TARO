package org.Aayush.api;

import org.Aayush.routing.topology.TopologyVersion;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stage F1 concurrent registry for caller-scoped retained-result ownership.
 * Satisfies closure criterion: API error posture is explicit for unauthorized, expired, and incompatible retained results.
 */
@Component
public final class CallerScopedRetainedResultRegistry {
    private static final Duration METADATA_RETENTION_GRACE = Duration.ofHours(1);

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
    private final Clock clock;

    public CallerScopedRetainedResultRegistry(ObjectProvider<Clock> clockProvider) {
        Clock providedClock = clockProvider.getIfAvailable();
        this.clock = providedClock == null ? Clock.systemUTC() : providedClock;
    }

    /**
     * Stage F1 records retained-result ownership and lineage at evaluation time.
     * Satisfies closure criterion: caller-scoped retrieval stays explicit without recomputation.
     */
    public void register(
            ResultKind resultKind,
            String resultSetId,
            String callerId,
            TopologyVersion topologyVersion,
            Instant expiresAt
    ) {
        pruneExpiredMetadata();
        entries.put(
                key(resultKind, resultSetId),
                new Entry(
                        Objects.requireNonNull(resultKind, "resultKind"),
                        requireText(callerId, "callerId"),
                        requireText(resultSetId, "resultSetId"),
                        Objects.requireNonNull(topologyVersion, "topologyVersion").getTopologyVersion(),
                        Objects.requireNonNull(expiresAt, "expiresAt")
                )
        );
    }

    /**
     * Returns retained-result ownership metadata when the API knows about a result id.
     */
    public Optional<Entry> find(ResultKind resultKind, String resultSetId) {
        return Optional.ofNullable(entries.get(key(resultKind, resultSetId)));
    }

    /**
     * Removes metadata entries whose expiry timestamp is stale beyond the registry grace window.
     */
    public int pruneExpiredMetadata() {
        Instant now = clock.instant();
        int removed = 0;
        for (String key : entries.keySet()) {
            Entry entry = entries.get(key);
            if (entry != null && !entry.expiresAt().plus(METADATA_RETENTION_GRACE).isAfter(now)) {
                if (entries.remove(key, entry)) {
                    removed++;
                }
            }
        }
        return removed;
    }

    /**
     * Returns the current registry size after opportunistic metadata pruning.
     */
    public int size() {
        pruneExpiredMetadata();
        return entries.size();
    }

    private String key(ResultKind resultKind, String resultSetId) {
        return Objects.requireNonNull(resultKind, "resultKind").name() + ":" + requireText(resultSetId, "resultSetId");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must be non-blank");
        }
        return value;
    }

    public enum ResultKind {
        ROUTE,
        MATRIX
    }

    public record Entry(
            ResultKind resultKind,
            String callerId,
            String resultSetId,
            String topologyVersionId,
            Instant expiresAt
    ) {
    }
}
