package org.Aayush.routing.future;

import java.time.Clock;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

/**
 * Bounded in-memory retained-result store for future-aware route result sets.
 */
public final class InMemoryEphemeralRouteResultStore implements EphemeralRouteResultStore {
    private final Map<String, StoredEntry> entries = new LinkedHashMap<>();
    private final Clock clock;
    private final Config config;
    private final ReentrantLock lock = new ReentrantLock();
    private long totalBytes;
    private long accessSequence;

    public InMemoryEphemeralRouteResultStore() {
        this(Clock.systemUTC(), Config.defaults());
    }

    public InMemoryEphemeralRouteResultStore(Clock clock) {
        this(clock, Config.defaults());
    }

    public InMemoryEphemeralRouteResultStore(Config config) {
        this(Clock.systemUTC(), config);
    }

    public InMemoryEphemeralRouteResultStore(Clock clock, Config config) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.config = Objects.requireNonNull(config, "config");
    }

    @Override
    public FutureRouteResultSet put(FutureRouteResultSet resultSet) {
        FutureRouteResultSet nonNullResultSet = Objects.requireNonNull(resultSet, "resultSet");
        Instant now = clock.instant();
        long estimatedBytes = FutureResultStoreSizing.estimateRouteResultSet(nonNullResultSet);
        if (estimatedBytes > config.maxPerEntryBytes()) {
            purgeExpired();
            return nonNullResultSet;
        }

        lock.lock();
        try {
            purgeExpiredLocked(now);
            removeExistingLocked(nonNullResultSet.getResultSetId());
            entries.put(
                    nonNullResultSet.getResultSetId(),
                    new StoredEntry(nonNullResultSet, estimatedBytes, nextAccessSequence())
            );
            totalBytes += estimatedBytes;
            evictToBudgetLocked(now);
            return nonNullResultSet;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Optional<FutureRouteResultSet> get(String resultSetId) {
        Objects.requireNonNull(resultSetId, "resultSetId");
        Instant now = clock.instant();
        lock.lock();
        try {
            StoredEntry entry = entries.get(resultSetId);
            if (entry == null) {
                return Optional.empty();
            }
            if (expired(entry.resultSet(), now)) {
                removeExistingLocked(resultSetId);
                return Optional.empty();
            }
            entry.touch(nextAccessSequence());
            return Optional.of(entry.resultSet());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void purgeExpired() {
        lock.lock();
        try {
            purgeExpiredLocked(clock.instant());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void invalidate(Predicate<FutureRouteResultSet> predicate) {
        Predicate<FutureRouteResultSet> nonNullPredicate = Objects.requireNonNull(predicate, "predicate");
        lock.lock();
        try {
            Iterator<Map.Entry<String, StoredEntry>> iterator = entries.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, StoredEntry> entry = iterator.next();
                if (nonNullPredicate.test(entry.getValue().resultSet())) {
                    totalBytes -= entry.getValue().storedBytes();
                    iterator.remove();
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private void purgeExpiredLocked(Instant now) {
        Iterator<Map.Entry<String, StoredEntry>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, StoredEntry> entry = iterator.next();
            if (expired(entry.getValue().resultSet(), now)) {
                totalBytes -= entry.getValue().storedBytes();
                iterator.remove();
            }
        }
    }

    private void evictToBudgetLocked(Instant now) {
        purgeExpiredLocked(now);
        while (entries.size() > config.maxEntries() || totalBytes > config.maxTotalBytes()) {
            Map.Entry<String, StoredEntry> victim = selectVictim();
            if (victim == null) {
                break;
            }
            totalBytes -= victim.getValue().storedBytes();
            entries.remove(victim.getKey());
        }
    }

    private Map.Entry<String, StoredEntry> selectVictim() {
        Map.Entry<String, StoredEntry> victim = null;
        for (Map.Entry<String, StoredEntry> entry : entries.entrySet()) {
            if (victim == null || entry.getValue().expiresAt().isBefore(victim.getValue().expiresAt())
                    || (entry.getValue().expiresAt().equals(victim.getValue().expiresAt())
                    && entry.getValue().lastReadSequence() < victim.getValue().lastReadSequence())) {
                victim = entry;
            }
        }
        return victim;
    }

    private void removeExistingLocked(String resultSetId) {
        StoredEntry removed = entries.remove(resultSetId);
        if (removed != null) {
            totalBytes -= removed.storedBytes();
        }
    }

    private long nextAccessSequence() {
        return ++accessSequence;
    }

    private static boolean expired(FutureRouteResultSet resultSet, Instant now) {
        return !resultSet.getExpiresAt().isAfter(now);
    }

    public record Config(long maxEntries, long maxTotalBytes, long maxPerEntryBytes) {
        private static final long DEFAULT_MAX_ENTRIES = 512L;
        private static final long DEFAULT_MAX_TOTAL_BYTES = 64L * 1024L * 1024L;
        private static final long DEFAULT_MAX_PER_ENTRY_BYTES = 2L * 1024L * 1024L;

        public Config {
            if (maxEntries <= 0L) {
                throw new IllegalArgumentException("maxEntries must be > 0");
            }
            if (maxTotalBytes <= 0L) {
                throw new IllegalArgumentException("maxTotalBytes must be > 0");
            }
            if (maxPerEntryBytes <= 0L) {
                throw new IllegalArgumentException("maxPerEntryBytes must be > 0");
            }
            if (maxPerEntryBytes > maxTotalBytes) {
                throw new IllegalArgumentException("maxPerEntryBytes must be <= maxTotalBytes");
            }
        }

        public static Config defaults() {
            return new Config(DEFAULT_MAX_ENTRIES, DEFAULT_MAX_TOTAL_BYTES, DEFAULT_MAX_PER_ENTRY_BYTES);
        }
    }

    private static final class StoredEntry {
        private final FutureRouteResultSet resultSet;
        private final long storedBytes;
        private long lastReadSequence;

        private StoredEntry(FutureRouteResultSet resultSet, long storedBytes, long lastReadSequence) {
            this.resultSet = resultSet;
            this.storedBytes = storedBytes;
            this.lastReadSequence = lastReadSequence;
        }

        private FutureRouteResultSet resultSet() {
            return resultSet;
        }

        private long storedBytes() {
            return storedBytes;
        }

        private Instant expiresAt() {
            return resultSet.getExpiresAt();
        }

        private long lastReadSequence() {
            return lastReadSequence;
        }

        private void touch(long nextSequence) {
            this.lastReadSequence = nextSequence;
        }
    }
}
