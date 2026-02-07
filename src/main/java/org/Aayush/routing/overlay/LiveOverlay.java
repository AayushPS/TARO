package org.Aayush.routing.overlay;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Stage 7 live overlay store for per-edge runtime overrides.
 * <p>
 * The overlay stores short-lived speed factors keyed by edge id. Lookup is designed
 * for the hot path of the routing cost engine:
 * </p>
 * <ul>
 * <li>O(1) expected lookup/update with {@link ConcurrentHashMap}.</li>
 * <li>Thread-safe concurrent readers and serialized writers.</li>
 * <li>Immutable published entries (writers replace the full entry atomically).</li>
 * <li>Hybrid cleanup:
 *     scheduled sweep + write-budgeted cleanup + optional read-opportunistic cleanup.</li>
 * <li>Hard memory cap with configurable overflow policy.</li>
 * </ul>
 * <p>
 * Expiry contract: an update is treated as expired when
 * {@code validUntilTicks <= nowTicks}. This means {@code validUntilTicks} is exclusive.
 * </p>
 * <p>
 * Speed-factor contract:
 * </p>
 * <ul>
 * <li>{@code speedFactor == 0.0f}: blocked edge ({@code +INF} penalty multiplier).</li>
 * <li>{@code 0 < speedFactor <= 1.0}: active slowdown with multiplier {@code 1/speedFactor}.</li>
 * <li>Missing/expired override: neutral multiplier {@code 1.0f}.</li>
 * </ul>
 */
public final class LiveOverlay {

    /**
     * Logical state returned by {@link #lookup(int, long)}.
     */
    public enum LookupState {
        /** No live override exists for this edge id. */
        MISSING,
        /** An override exists but is already expired for the queried {@code nowTicks}. */
        EXPIRED,
        /** The edge is explicitly blocked ({@code speedFactor == 0.0f}). */
        BLOCKED,
        /** A non-expired non-blocking override exists ({@code 0 < speedFactor <= 1.0}). */
        ACTIVE
    }

    /**
     * Overflow behavior when ingesting a new edge while the overlay is at capacity.
     */
    public enum CapacityPolicy {
        /**
         * Strictest policy. If a batch introduces more new edge ids than available slots,
         * reject the whole non-expired ingest (all-or-nothing for capacity).
         */
        REJECT_BATCH,
        /**
         * First remove expired entries (bounded by cleanup budget), then reject if still full.
         */
        EVICT_EXPIRED_THEN_REJECT,
        /**
         * Remove expired entries first; if still full, evict the entry with the oldest expiry.
         */
        EVICT_OLDEST_EXPIRY
    }

    /**
     * Result payload for lookup semantics.
     */
    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    @Accessors(fluent = true)
    public static final class LookupResult {
        /** Lookup state at the queried time. */
        private final LookupState state;
        /**
         * Live speed factor for ACTIVE/BLOCKED states.
         * NaN for MISSING/EXPIRED states.
         */
        private final float speedFactor;

        /**
         * Converts state + speed factor into the canonical multiplicative penalty used by
         * the cost engine.
         *
         * @return {@code 1.0f} for MISSING/EXPIRED, {@code +INF} for BLOCKED,
         * and {@code 1/speedFactor} for ACTIVE.
         */
        public float livePenaltyMultiplier() {
            return switch (state) {
                case MISSING, EXPIRED -> 1.0f;
                case BLOCKED -> Float.POSITIVE_INFINITY;
                case ACTIVE -> 1.0f / speedFactor;
            };
        }
    }

    /**
     * Summary counters for one {@link #applyBatch(List, long)} call.
     */
    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    @Accessors(fluent = true)
    public static final class BatchApplyResult {
        /** Number of updates accepted into the overlay. */
        private final int accepted;
        /** Number of updates discarded because they were expired at ingest time. */
        private final int rejectedExpiredAtIngest;
        /** Number of updates rejected due to capacity constraints. */
        private final int rejectedCapacity;
        /** Number of expired entries removed during cleanup while applying this batch. */
        private final int expiredRemoved;
        /** Number of entries force-evicted by {@link CapacityPolicy#EVICT_OLDEST_EXPIRY}. */
        private final int oldestExpiryEvicted;
    }

    private static final LookupResult MISSING_RESULT = new LookupResult(LookupState.MISSING, Float.NaN);
    private static final LookupResult EXPIRED_RESULT = new LookupResult(LookupState.EXPIRED, Float.NaN);
    private static final LookupResult BLOCKED_RESULT = new LookupResult(LookupState.BLOCKED, 0.0f);

    private final ConcurrentHashMap<Integer, Entry> entries;
    private final ReentrantLock writeLock;

    @Getter
    @Accessors(fluent = true)
    private final int maxLiveOverrides;
    @Getter
    @Accessors(fluent = true)
    private final CapacityPolicy capacityPolicy;
    private final int writeCleanupBudget;
    private final boolean readCleanupEnabled;

    /**
     * Immutable internal payload for a single live override.
     */
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class Entry {
        private final float speedFactor;
        private final long validUntilTicks;
    }

    /**
     * Constructs an overlay with default operational policy:
     * {@link CapacityPolicy#EVICT_EXPIRED_THEN_REJECT}, write cleanup budget {@code 32},
     * and read-opportunistic cleanup enabled.
     *
     * @param maxLiveOverrides hard upper bound for number of tracked edge overrides.
     */
    public LiveOverlay(int maxLiveOverrides) {
        this(maxLiveOverrides, CapacityPolicy.EVICT_EXPIRED_THEN_REJECT, 32, true);
    }

    /**
     * Constructs an overlay with explicit cleanup and capacity policy.
     *
     * @param maxLiveOverrides hard upper bound for number of tracked edge overrides; must be {@code > 0}.
     * @param capacityPolicy overflow policy used when adding a new edge at capacity.
     * @param writeCleanupBudget number of expired entries to attempt to remove at ingest start; must be {@code >= 0}.
     * @param readCleanupEnabled if true, lookup performs at most one opportunistic removal for expired entries.
     */
    public LiveOverlay(
            int maxLiveOverrides,
            CapacityPolicy capacityPolicy,
            int writeCleanupBudget,
            boolean readCleanupEnabled
    ) {
        if (maxLiveOverrides <= 0) {
            throw new IllegalArgumentException("max_live_overrides must be > 0");
        }
        if (writeCleanupBudget < 0) {
            throw new IllegalArgumentException("writeCleanupBudget must be >= 0");
        }
        this.maxLiveOverrides = maxLiveOverrides;
        this.capacityPolicy = Objects.requireNonNull(capacityPolicy, "capacityPolicy");
        this.writeCleanupBudget = writeCleanupBudget;
        this.readCleanupEnabled = readCleanupEnabled;
        this.entries = new ConcurrentHashMap<>();
        this.writeLock = new ReentrantLock();
    }

    /**
     * Looks up the live state of one edge at the given engine time.
     * <p>
     * Read path is lock-free with a single hash lookup. If an entry is expired, this method
     * returns {@link LookupState#EXPIRED}; optionally it also performs one best-effort remove
     * when read cleanup is enabled.
     * </p>
     *
     * @param edgeId edge identifier (must be non-negative).
     * @param nowTicks current engine time in ticks.
     * @return immutable lookup result describing state and speed factor semantics.
     */
    public LookupResult lookup(int edgeId, long nowTicks) {
        validateEdgeId(edgeId);
        Entry entry = entries.get(edgeId);
        if (entry == null) {
            return MISSING_RESULT;
        }

        if (entry.validUntilTicks <= nowTicks) {
            if (readCleanupEnabled) {
                // Opportunistic read cleanup with strict per-read cap of one remove.
                entries.remove(edgeId, entry);
            }
            return EXPIRED_RESULT;
        }

        if (entry.speedFactor == 0.0f) {
            return BLOCKED_RESULT;
        }

        return new LookupResult(LookupState.ACTIVE, entry.speedFactor);
    }

    /**
     * Convenience projection for {@link LookupResult#livePenaltyMultiplier()}.
     *
     * @param edgeId edge identifier (must be non-negative).
     * @param nowTicks current engine time in ticks.
     * @return canonical multiplier for Stage 7 cost integration.
     */
    public float livePenaltyMultiplier(int edgeId, long nowTicks) {
        return lookup(edgeId, nowTicks).livePenaltyMultiplier();
    }

    /**
     * Single-update convenience wrapper over {@link #applyBatch(List, long)}.
     *
     * @param update update payload.
     * @param nowTicks current engine time in ticks.
     * @return true if the update was accepted.
     */
    public boolean upsert(LiveUpdate update, long nowTicks) {
        return applyBatch(List.of(update), nowTicks).accepted() == 1;
    }

    /**
     * Applies a batch of live updates under one writer lock.
     * <p>
     * Processing order is the incoming list order. Existing edge ids are updated in-place
     * (atomic replacement) and do not consume additional capacity.
     * </p>
     * <p>
     * Capacity behavior:
     * </p>
     * <ul>
     * <li>{@link CapacityPolicy#REJECT_BATCH}: pre-checks distinct new edge ids; rejects the
     * whole non-expired ingest if capacity is insufficient.</li>
     * <li>{@link CapacityPolicy#EVICT_EXPIRED_THEN_REJECT}: opportunistically removes expired entries
     * before rejecting new inserts.</li>
     * <li>{@link CapacityPolicy#EVICT_OLDEST_EXPIRY}: same as above, then evicts the oldest-expiry
     * entry if still full.</li>
     * </ul>
     *
     * @param updates updates to apply; list and elements must be non-null.
     * @param nowTicks current engine time in ticks.
     * @return summary counters for accepted/rejected/evicted work.
     */
    public BatchApplyResult applyBatch(List<LiveUpdate> updates, long nowTicks) {
        Objects.requireNonNull(updates, "updates");
        if (updates.isEmpty()) {
            return new BatchApplyResult(0, 0, 0, 0, 0);
        }

        int accepted = 0;
        int rejectedExpired = 0;
        int rejectedCapacity = 0;
        int expiredRemoved = 0;
        int oldestEvicted = 0;

        writeLock.lock();
        try {
            if (writeCleanupBudget > 0) {
                expiredRemoved += removeExpiredLocked(nowTicks, writeCleanupBudget);
            }

            if (capacityPolicy == CapacityPolicy.REJECT_BATCH) {
                int precheckRejectedExpired = 0;
                HashSet<Integer> newEdgeIds = new HashSet<>();
                for (LiveUpdate update : updates) {
                    Objects.requireNonNull(update, "update");
                    if (update.validUntilTicks() <= nowTicks) {
                        precheckRejectedExpired++;
                        continue;
                    }
                    int edgeId = update.edgeId();
                    if (!entries.containsKey(edgeId)) {
                        newEdgeIds.add(edgeId);
                    }
                }

                int available = maxLiveOverrides - entries.size();
                if (newEdgeIds.size() > available) {
                    int rejectedByCapacity = updates.size() - precheckRejectedExpired;
                    return new BatchApplyResult(0, precheckRejectedExpired, rejectedByCapacity, expiredRemoved, 0);
                }
            }

            for (LiveUpdate update : updates) {
                Objects.requireNonNull(update, "update");
                if (update.validUntilTicks() <= nowTicks) {
                    rejectedExpired++;
                    continue;
                }

                int edgeId = update.edgeId();
                Entry newEntry = new Entry(update.speedFactor(), update.validUntilTicks());

                if (entries.containsKey(edgeId)) {
                    entries.put(edgeId, newEntry);
                    accepted++;
                    continue;
                }

                if (entries.size() >= maxLiveOverrides) {
                    switch (capacityPolicy) {
                        case REJECT_BATCH -> {
                            rejectedCapacity++;
                            continue;
                        }
                        case EVICT_EXPIRED_THEN_REJECT -> {
                            int budget = Math.max(writeCleanupBudget, 1);
                            expiredRemoved += removeExpiredLocked(nowTicks, budget);
                            if (entries.size() >= maxLiveOverrides) {
                                rejectedCapacity++;
                                continue;
                            }
                        }
                        case EVICT_OLDEST_EXPIRY -> {
                            int budget = Math.max(writeCleanupBudget, 1);
                            expiredRemoved += removeExpiredLocked(nowTicks, budget);
                            if (entries.size() >= maxLiveOverrides) {
                                Integer oldestKey = findOldestExpiryKeyLocked();
                                if (oldestKey != null && entries.remove(oldestKey) != null) {
                                    oldestEvicted++;
                                }
                            }
                            if (entries.size() >= maxLiveOverrides) {
                                rejectedCapacity++;
                                continue;
                            }
                        }
                    }
                }

                entries.put(edgeId, newEntry);
                accepted++;
            }
        } finally {
            writeLock.unlock();
        }

        return new BatchApplyResult(accepted, rejectedExpired, rejectedCapacity, expiredRemoved, oldestEvicted);
    }

    /**
     * Scheduled cleanup path for expired entries.
     *
     * @param nowTicks current engine time in ticks.
     * @param maxRemovals max entries to remove; {@code <= 0} means unbounded for this call.
     * @return number of removed expired entries.
     */
    public int runScheduledSweep(long nowTicks, int maxRemovals) {
        writeLock.lock();
        try {
            return removeExpiredLocked(nowTicks, maxRemovals <= 0 ? Integer.MAX_VALUE : maxRemovals);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Current number of tracked edge overrides (expired entries may still be present until cleanup).
     *
     * @return live map size.
     */
    public int size() {
        return entries.size();
    }

    /**
     * Removes all overlay entries.
     */
    public void clear() {
        writeLock.lock();
        try {
            entries.clear();
        } finally {
            writeLock.unlock();
        }
    }

    private int removeExpiredLocked(long nowTicks, int budget) {
        int removed = 0;
        for (Map.Entry<Integer, Entry> mapEntry : entries.entrySet()) {
            if (removed >= budget) {
                break;
            }
            Entry current = mapEntry.getValue();
            if (current != null && current.validUntilTicks <= nowTicks) {
                if (entries.remove(mapEntry.getKey(), current)) {
                    removed++;
                }
            }
        }
        return removed;
    }

    private Integer findOldestExpiryKeyLocked() {
        Integer oldestKey = null;
        long minValidUntil = Long.MAX_VALUE;
        for (Map.Entry<Integer, Entry> mapEntry : entries.entrySet()) {
            Entry e = mapEntry.getValue();
            if (e != null && e.validUntilTicks < minValidUntil) {
                minValidUntil = e.validUntilTicks;
                oldestKey = mapEntry.getKey();
            }
        }
        return oldestKey;
    }

    private static void validateEdgeId(int edgeId) {
        if (edgeId < 0) {
            throw new IllegalArgumentException("edge_id must be >= 0");
        }
    }
}
