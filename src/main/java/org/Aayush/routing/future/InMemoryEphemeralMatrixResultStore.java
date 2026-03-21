package org.Aayush.routing.future;

import org.Aayush.routing.core.MatrixResponse;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Bounded in-memory retained-result store for future-aware matrix result sets.
 */
public final class InMemoryEphemeralMatrixResultStore implements EphemeralMatrixResultStore {
    private final Map<String, StoredEntry> entries = new LinkedHashMap<>();
    private final Clock clock;
    private final Config config;
    private final ReentrantLock lock = new ReentrantLock();
    private long totalBytes;
    private long accessSequence;

    public InMemoryEphemeralMatrixResultStore() {
        this(Clock.systemUTC(), Config.defaults());
    }

    public InMemoryEphemeralMatrixResultStore(Clock clock) {
        this(clock, Config.defaults());
    }

    public InMemoryEphemeralMatrixResultStore(Config config) {
        this(Clock.systemUTC(), config);
    }

    public InMemoryEphemeralMatrixResultStore(Clock clock, Config config) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.config = Objects.requireNonNull(config, "config");
    }

    @Override
    public FutureMatrixResultSet put(FutureMatrixResultSet resultSet) {
        FutureMatrixResultSet nonNullResultSet = Objects.requireNonNull(resultSet, "resultSet");
        Instant now = clock.instant();
        StoredMatrixResultSet storedResultSet = compact(nonNullResultSet);
        long storedBytes = FutureResultStoreSizing.estimateMatrixMetadata(nonNullResultSet) + storedResultSet.payloadBytes();
        if (storedBytes > config.maxPerEntryBytes()) {
            purgeExpired();
            return nonNullResultSet;
        }

        lock.lock();
        try {
            purgeExpiredLocked(now);
            removeExistingLocked(nonNullResultSet.getResultSetId());
            entries.put(
                    nonNullResultSet.getResultSetId(),
                    new StoredEntry(storedResultSet, storedBytes, nextAccessSequence())
            );
            totalBytes += storedBytes;
            evictToBudgetLocked(now);
            return nonNullResultSet;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Optional<FutureMatrixResultSet> get(String resultSetId) {
        Objects.requireNonNull(resultSetId, "resultSetId");
        Instant now = clock.instant();
        lock.lock();
        try {
            StoredEntry entry = entries.get(resultSetId);
            if (entry == null) {
                return Optional.empty();
            }
            if (expired(entry.storedResultSet().expiresAt(), now)) {
                removeExistingLocked(resultSetId);
                return Optional.empty();
            }
            entry.touch(nextAccessSequence());
            return Optional.of(entry.storedResultSet().inflate());
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
    public void invalidate(Predicate<FutureMatrixResultSet> predicate) {
        Predicate<FutureMatrixResultSet> nonNullPredicate = Objects.requireNonNull(predicate, "predicate");
        lock.lock();
        try {
            Iterator<Map.Entry<String, StoredEntry>> iterator = entries.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, StoredEntry> entry = iterator.next();
                if (nonNullPredicate.test(entry.getValue().storedResultSet().inflate())) {
                    totalBytes -= entry.getValue().storedBytes();
                    iterator.remove();
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private StoredMatrixResultSet compact(FutureMatrixResultSet resultSet) {
        StoredFutureMatrixAggregate aggregate = StoredFutureMatrixAggregate.from(resultSet.getAggregate(), config);
        ArrayList<StoredFutureMatrixScenarioResult> scenarioResults = new ArrayList<>(resultSet.getScenarioResults().size());
        for (FutureMatrixScenarioResult scenarioResult : resultSet.getScenarioResults()) {
            scenarioResults.add(StoredFutureMatrixScenarioResult.from(scenarioResult, config));
        }
        return new StoredMatrixResultSet(
                resultSet.getResultSetId(),
                resultSet.getCreatedAt(),
                resultSet.getExpiresAt(),
                resultSet.getRequest(),
                resultSet.getTopologyVersion(),
                resultSet.getQuarantineSnapshotId(),
                resultSet.getScenarioBundle(),
                aggregate,
                List.copyOf(scenarioResults)
        );
    }

    private void purgeExpiredLocked(Instant now) {
        Iterator<Map.Entry<String, StoredEntry>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, StoredEntry> entry = iterator.next();
            if (expired(entry.getValue().storedResultSet().expiresAt(), now)) {
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
            if (victim == null || entry.getValue().storedResultSet().expiresAt().isBefore(victim.getValue().storedResultSet().expiresAt())
                    || (entry.getValue().storedResultSet().expiresAt().equals(victim.getValue().storedResultSet().expiresAt())
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

    private static boolean expired(Instant expiresAt, Instant now) {
        return !expiresAt.isAfter(now);
    }

    public record Config(
            long maxEntries,
            long maxTotalBytes,
            long maxPerEntryBytes,
            int compressionThresholdBytes
    ) {
        private static final long DEFAULT_MAX_ENTRIES = 128L;
        private static final long DEFAULT_MAX_TOTAL_BYTES = 256L * 1024L * 1024L;
        private static final long DEFAULT_MAX_PER_ENTRY_BYTES = 32L * 1024L * 1024L;
        private static final int DEFAULT_COMPRESSION_THRESHOLD_BYTES = 256 * 1024;

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
            if (compressionThresholdBytes < 0) {
                throw new IllegalArgumentException("compressionThresholdBytes must be >= 0");
            }
        }

        public static Config defaults() {
            return new Config(
                    DEFAULT_MAX_ENTRIES,
                    DEFAULT_MAX_TOTAL_BYTES,
                    DEFAULT_MAX_PER_ENTRY_BYTES,
                    DEFAULT_COMPRESSION_THRESHOLD_BYTES
            );
        }
    }

    private static final class StoredEntry {
        private final StoredMatrixResultSet storedResultSet;
        private final long storedBytes;
        private long lastReadSequence;

        private StoredEntry(StoredMatrixResultSet storedResultSet, long storedBytes, long lastReadSequence) {
            this.storedResultSet = storedResultSet;
            this.storedBytes = storedBytes;
            this.lastReadSequence = lastReadSequence;
        }

        private StoredMatrixResultSet storedResultSet() {
            return storedResultSet;
        }

        private long storedBytes() {
            return storedBytes;
        }

        private long lastReadSequence() {
            return lastReadSequence;
        }

        private void touch(long nextSequence) {
            this.lastReadSequence = nextSequence;
        }
    }

    private record StoredMatrixResultSet(
            String resultSetId,
            Instant createdAt,
            Instant expiresAt,
            FutureMatrixRequest request,
            org.Aayush.routing.topology.TopologyVersion topologyVersion,
            String quarantineSnapshotId,
            ScenarioBundle scenarioBundle,
            StoredFutureMatrixAggregate aggregate,
            List<StoredFutureMatrixScenarioResult> scenarioResults
    ) {
        private long payloadBytes() {
            long payloadBytes = aggregate.payloadBytes();
            for (StoredFutureMatrixScenarioResult scenarioResult : scenarioResults) {
                payloadBytes += scenarioResult.payloadBytes();
            }
            return payloadBytes;
        }

        private FutureMatrixResultSet inflate() {
            FutureMatrixResultSet.FutureMatrixResultSetBuilder builder = FutureMatrixResultSet.builder()
                    .resultSetId(resultSetId)
                    .createdAt(createdAt)
                    .expiresAt(expiresAt)
                    .request(request)
                    .topologyVersion(topologyVersion)
                    .quarantineSnapshotId(quarantineSnapshotId)
                    .scenarioBundle(scenarioBundle)
                    .aggregate(aggregate.inflate());
            for (StoredFutureMatrixScenarioResult scenarioResult : scenarioResults) {
                builder.scenarioResult(scenarioResult.inflate());
            }
            return builder.build();
        }
    }

    private record StoredFutureMatrixAggregate(
            List<String> sourceExternalIds,
            List<String> targetExternalIds,
            String aggregationNote,
            StoredDoubleMatrix reachabilityProbabilities,
            StoredFloatMatrix expectedCosts,
            StoredFloatMatrix p50Costs,
            StoredFloatMatrix p90Costs,
            StoredFloatMatrix minCosts,
            StoredFloatMatrix maxCosts,
            StoredLongMatrix minArrivalTicks,
            StoredLongMatrix maxArrivalTicks
    ) {
        private static StoredFutureMatrixAggregate from(FutureMatrixAggregate aggregate, Config config) {
            return new StoredFutureMatrixAggregate(
                    List.copyOf(aggregate.getSourceExternalIds()),
                    List.copyOf(aggregate.getTargetExternalIds()),
                    aggregate.getAggregationNote(),
                    StoredDoubleMatrix.from(aggregate.getReachabilityProbabilities(), config),
                    StoredFloatMatrix.from(aggregate.getExpectedCosts(), config),
                    StoredFloatMatrix.from(aggregate.getP50Costs(), config),
                    StoredFloatMatrix.from(aggregate.getP90Costs(), config),
                    StoredFloatMatrix.from(aggregate.getMinCosts(), config),
                    StoredFloatMatrix.from(aggregate.getMaxCosts(), config),
                    StoredLongMatrix.from(aggregate.getMinArrivalTicks(), config),
                    StoredLongMatrix.from(aggregate.getMaxArrivalTicks(), config)
            );
        }

        private FutureMatrixAggregate inflate() {
            return FutureMatrixAggregate.builder()
                    .sourceExternalIds(sourceExternalIds)
                    .targetExternalIds(targetExternalIds)
                    .reachabilityProbabilities(reachabilityProbabilities.inflate())
                    .expectedCosts(expectedCosts.inflate())
                    .p50Costs(p50Costs.inflate())
                    .p90Costs(p90Costs.inflate())
                    .minCosts(minCosts.inflate())
                    .maxCosts(maxCosts.inflate())
                    .minArrivalTicks(minArrivalTicks.inflate())
                    .maxArrivalTicks(maxArrivalTicks.inflate())
                    .aggregationNote(aggregationNote)
                    .build();
        }

        private long payloadBytes() {
            return reachabilityProbabilities.payloadBytes()
                    + expectedCosts.payloadBytes()
                    + p50Costs.payloadBytes()
                    + p90Costs.payloadBytes()
                    + minCosts.payloadBytes()
                    + maxCosts.payloadBytes()
                    + minArrivalTicks.payloadBytes()
                    + maxArrivalTicks.payloadBytes();
        }
    }

    private record StoredFutureMatrixScenarioResult(
            String scenarioId,
            String label,
            double probability,
            List<String> explanationTags,
            StoredMatrixResponse matrix
    ) {
        private static StoredFutureMatrixScenarioResult from(FutureMatrixScenarioResult scenarioResult, Config config) {
            return new StoredFutureMatrixScenarioResult(
                    scenarioResult.getScenarioId(),
                    scenarioResult.getLabel(),
                    scenarioResult.getProbability(),
                    List.copyOf(scenarioResult.getExplanationTags()),
                    StoredMatrixResponse.from(scenarioResult.getMatrix(), config)
            );
        }

        private FutureMatrixScenarioResult inflate() {
            return FutureMatrixScenarioResult.builder()
                    .scenarioId(scenarioId)
                    .label(label)
                    .probability(probability)
                    .explanationTags(explanationTags)
                    .matrix(matrix.inflate())
                    .build();
        }

        private long payloadBytes() {
            return matrix.payloadBytes();
        }
    }

    private record StoredMatrixResponse(
            List<String> sourceExternalIds,
            List<String> targetExternalIds,
            org.Aayush.routing.core.RoutingAlgorithm algorithm,
            org.Aayush.routing.heuristic.HeuristicType heuristicType,
            String implementationNote,
            StoredBooleanMatrix reachable,
            StoredFloatMatrix totalCosts,
            StoredLongMatrix arrivalTicks
    ) {
        private static StoredMatrixResponse from(MatrixResponse response, Config config) {
            return new StoredMatrixResponse(
                    List.copyOf(response.getSourceExternalIds()),
                    List.copyOf(response.getTargetExternalIds()),
                    response.getAlgorithm(),
                    response.getHeuristicType(),
                    response.getImplementationNote(),
                    StoredBooleanMatrix.from(response.getReachable(), config),
                    StoredFloatMatrix.from(response.getTotalCosts(), config),
                    StoredLongMatrix.from(response.getArrivalTicks(), config)
            );
        }

        private MatrixResponse inflate() {
            return MatrixResponse.builder()
                    .sourceExternalIds(sourceExternalIds)
                    .targetExternalIds(targetExternalIds)
                    .reachable(reachable.inflate())
                    .totalCosts(totalCosts.inflate())
                    .arrivalTicks(arrivalTicks.inflate())
                    .algorithm(algorithm)
                    .heuristicType(heuristicType)
                    .implementationNote(implementationNote)
                    .build();
        }

        private long payloadBytes() {
            return reachable.payloadBytes() + totalCosts.payloadBytes() + arrivalTicks.payloadBytes();
        }
    }

    private record StoredBooleanMatrix(int rows, int cols, boolean compressed, byte[] payload) {
        private static StoredBooleanMatrix from(boolean[][] source, Config config) {
            int rows = source.length;
            int cols = rows == 0 ? 0 : source[0].length;
            byte[] raw = new byte[rows * cols];
            int cursor = 0;
            for (boolean[] row : source) {
                for (boolean value : row) {
                    raw[cursor++] = value ? (byte) 1 : (byte) 0;
                }
            }
            CompressionResult compressionResult = maybeCompress(raw, config.compressionThresholdBytes());
            return new StoredBooleanMatrix(rows, cols, compressionResult.compressed(), compressionResult.payload());
        }

        private boolean[][] inflate() {
            byte[] raw = maybeInflate(payload, compressed, rows * cols);
            boolean[][] result = new boolean[rows][cols];
            int cursor = 0;
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < cols; col++) {
                    result[row][col] = raw[cursor++] != 0;
                }
            }
            return result;
        }

        private long payloadBytes() {
            return Integer.BYTES * 2L + 1L + payload.length;
        }
    }

    private record StoredFloatMatrix(int rows, int cols, boolean compressed, byte[] payload) {
        private static StoredFloatMatrix from(float[][] source, Config config) {
            int rows = source.length;
            int cols = rows == 0 ? 0 : source[0].length;
            ByteBuffer buffer = ByteBuffer.allocate(rows * cols * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
            for (float[] row : source) {
                for (float value : row) {
                    buffer.putFloat(value);
                }
            }
            CompressionResult compressionResult = maybeCompress(buffer.array(), config.compressionThresholdBytes());
            return new StoredFloatMatrix(rows, cols, compressionResult.compressed(), compressionResult.payload());
        }

        private float[][] inflate() {
            byte[] raw = maybeInflate(payload, compressed, rows * cols * Float.BYTES);
            ByteBuffer buffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
            float[][] result = new float[rows][cols];
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < cols; col++) {
                    result[row][col] = buffer.getFloat();
                }
            }
            return result;
        }

        private long payloadBytes() {
            return Integer.BYTES * 2L + 1L + payload.length;
        }
    }

    private record StoredLongMatrix(int rows, int cols, boolean compressed, byte[] payload) {
        private static StoredLongMatrix from(long[][] source, Config config) {
            int rows = source.length;
            int cols = rows == 0 ? 0 : source[0].length;
            ByteBuffer buffer = ByteBuffer.allocate(rows * cols * Long.BYTES).order(ByteOrder.LITTLE_ENDIAN);
            for (long[] row : source) {
                for (long value : row) {
                    buffer.putLong(value);
                }
            }
            CompressionResult compressionResult = maybeCompress(buffer.array(), config.compressionThresholdBytes());
            return new StoredLongMatrix(rows, cols, compressionResult.compressed(), compressionResult.payload());
        }

        private long[][] inflate() {
            byte[] raw = maybeInflate(payload, compressed, rows * cols * Long.BYTES);
            ByteBuffer buffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
            long[][] result = new long[rows][cols];
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < cols; col++) {
                    result[row][col] = buffer.getLong();
                }
            }
            return result;
        }

        private long payloadBytes() {
            return Integer.BYTES * 2L + 1L + payload.length;
        }
    }

    private record StoredDoubleMatrix(int rows, int cols, boolean compressed, byte[] payload) {
        private static StoredDoubleMatrix from(double[][] source, Config config) {
            int rows = source.length;
            int cols = rows == 0 ? 0 : source[0].length;
            ByteBuffer buffer = ByteBuffer.allocate(rows * cols * Double.BYTES).order(ByteOrder.LITTLE_ENDIAN);
            for (double[] row : source) {
                for (double value : row) {
                    buffer.putDouble(value);
                }
            }
            CompressionResult compressionResult = maybeCompress(buffer.array(), config.compressionThresholdBytes());
            return new StoredDoubleMatrix(rows, cols, compressionResult.compressed(), compressionResult.payload());
        }

        private double[][] inflate() {
            byte[] raw = maybeInflate(payload, compressed, rows * cols * Double.BYTES);
            ByteBuffer buffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
            double[][] result = new double[rows][cols];
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < cols; col++) {
                    result[row][col] = buffer.getDouble();
                }
            }
            return result;
        }

        private long payloadBytes() {
            return Integer.BYTES * 2L + 1L + payload.length;
        }
    }

    private record CompressionResult(boolean compressed, byte[] payload) {
    }

    private static CompressionResult maybeCompress(byte[] raw, int thresholdBytes) {
        if (raw.length <= thresholdBytes) {
            return new CompressionResult(false, raw);
        }

        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        deflater.setInput(raw);
        deflater.finish();
        byte[] buffer = new byte[Math.max(256, Math.min(raw.length, 8 * 1024))];
        ByteArrayOutputStream output = new ByteArrayOutputStream(raw.length);
        while (!deflater.finished()) {
            int written = deflater.deflate(buffer);
            if (written == 0 && deflater.needsInput()) {
                break;
            }
            output.write(buffer, 0, written);
        }
        deflater.end();
        byte[] compressed = output.toByteArray();
        if (compressed.length >= raw.length) {
            return new CompressionResult(false, raw);
        }
        return new CompressionResult(true, compressed);
    }

    private static byte[] maybeInflate(byte[] payload, boolean compressed, int expectedBytes) {
        if (!compressed) {
            return payload;
        }
        Inflater inflater = new Inflater();
        inflater.setInput(payload);
        byte[] output = new byte[expectedBytes];
        try {
            int offset = 0;
            while (!inflater.finished() && offset < output.length) {
                int inflated = inflater.inflate(output, offset, output.length - offset);
                if (inflated == 0 && inflater.needsInput()) {
                    break;
                }
                offset += inflated;
            }
            if (offset != expectedBytes || !inflater.finished()) {
                throw new IllegalStateException("compressed matrix payload length mismatch");
            }
            return output;
        } catch (Exception ex) {
            throw new IllegalStateException("failed to inflate retained matrix payload", ex);
        } finally {
            inflater.end();
        }
    }
}
