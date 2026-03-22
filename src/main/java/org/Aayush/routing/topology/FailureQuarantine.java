package org.Aayush.routing.topology;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.Aayush.routing.graph.EdgeGraph;
import org.Aayush.routing.overlay.LiveUpdate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Runtime quarantine store for transient edge/node failures.
 */
public final class FailureQuarantine {
    private final String bindingId;
    private final TopologyIncidentIndex incidentIndex;
    private final Map<Integer, FailureRecord> edgeFailures = new HashMap<>();
    private final Map<Integer, FailureRecord> nodeFailures = new HashMap<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicLong version = new AtomicLong();

    /**
     * One quarantined subject.
     */
    public record FailureRecord(
            int subjectId,
            long validUntilTicks,
            long observedAtTicks,
            String reason,
            String source
    ) {
    }

    public FailureQuarantine() {
        this("failure-quarantine", null);
    }

    public FailureQuarantine(String bindingId) {
        this(bindingId, null);
    }

    public FailureQuarantine(String bindingId, TopologyIncidentIndex incidentIndex) {
        if (bindingId == null || bindingId.isBlank()) {
            throw new IllegalArgumentException("bindingId must be non-blank");
        }
        this.bindingId = bindingId;
        this.incidentIndex = incidentIndex;
    }

    /**
     * Quarantines one edge until the provided absolute expiry tick.
     */
    public void quarantineEdge(int edgeId, long validUntilTicks, String reason, String source) {
        quarantineEdge(edgeId, validUntilTicks, Long.MIN_VALUE, reason, source);
    }

    /**
     * Quarantines one edge until the provided absolute expiry tick with an explicit observation timestamp.
     */
    public void quarantineEdge(int edgeId, long validUntilTicks, long observedAtTicks, String reason, String source) {
        if (edgeId < 0) {
            throw new IllegalArgumentException("edgeId must be >= 0");
        }
        putFailure(edgeFailures, edgeId, validUntilTicks, observedAtTicks, reason, source);
    }

    /**
     * Quarantines one node until the provided absolute expiry tick.
     */
    public void quarantineNode(int nodeId, long validUntilTicks, String reason, String source) {
        quarantineNode(nodeId, validUntilTicks, Long.MIN_VALUE, reason, source);
    }

    /**
     * Quarantines one node until the provided absolute expiry tick with an explicit observation timestamp.
     */
    public void quarantineNode(int nodeId, long validUntilTicks, long observedAtTicks, String reason, String source) {
        if (nodeId < 0) {
            throw new IllegalArgumentException("nodeId must be >= 0");
        }
        putFailure(nodeFailures, nodeId, validUntilTicks, observedAtTicks, reason, source);
    }

    /**
     * Clears all quarantines.
     */
    public void clearAll() {
        lock.lock();
        try {
            edgeFailures.clear();
            nodeFailures.clear();
            version.incrementAndGet();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Captures an immutable view of currently active quarantines.
     */
    public Snapshot snapshot(long nowTicks) {
        lock.lock();
        try {
            Map<Integer, FailureRecord> activeEdges = new HashMap<>();
            for (Map.Entry<Integer, FailureRecord> entry : edgeFailures.entrySet()) {
                if (entry.getValue().validUntilTicks() > nowTicks) {
                    activeEdges.put(entry.getKey(), entry.getValue());
                }
            }

            Map<Integer, FailureRecord> activeNodes = new HashMap<>();
            for (Map.Entry<Integer, FailureRecord> entry : nodeFailures.entrySet()) {
                if (entry.getValue().validUntilTicks() > nowTicks) {
                    activeNodes.put(entry.getKey(), entry.getValue());
                }
            }

            String snapshotId = bindingId + ":" + version.get();
            Map<Integer, Long> blockedUntilByEdge = buildBlockedUntilByEdge(activeEdges, activeNodes, incidentIndex);
            boolean nodeExpansionPrecomputed = incidentIndex != null;
            return new Snapshot(
                    snapshotId,
                    nowTicks,
                    Map.copyOf(activeEdges),
                    Map.copyOf(activeNodes),
                    Map.copyOf(blockedUntilByEdge),
                    nodeExpansionPrecomputed
            );
        } finally {
            lock.unlock();
        }
    }

    private void putFailure(
            Map<Integer, FailureRecord> target,
            int subjectId,
            long validUntilTicks,
            long observedAtTicks,
            String reason,
            String source
    ) {
        lock.lock();
        try {
            target.put(subjectId, new FailureRecord(
                    subjectId,
                    validUntilTicks,
                    observedAtTicks,
                    sanitize(reason),
                    sanitize(source)
            ));
            version.incrementAndGet();
        } finally {
            lock.unlock();
        }
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? "" : normalized;
    }

    private static Map<Integer, Long> buildBlockedUntilByEdge(
            Map<Integer, FailureRecord> activeEdgeFailures,
            Map<Integer, FailureRecord> activeNodeFailures,
            TopologyIncidentIndex incidentIndex
    ) {
        Map<Integer, Long> blockedUntilByEdge = new HashMap<>();
        for (FailureRecord record : activeEdgeFailures.values()) {
            mergeBlockedUntil(blockedUntilByEdge, record.subjectId(), record.validUntilTicks());
        }
        if (incidentIndex == null) {
            return blockedUntilByEdge;
        }
        for (FailureRecord record : activeNodeFailures.values()) {
            int[] incidentEdges = incidentIndex.incidentEdges(record.subjectId());
            for (int edgeId : incidentEdges) {
                mergeBlockedUntil(blockedUntilByEdge, edgeId, record.validUntilTicks());
            }
        }
        return blockedUntilByEdge;
    }

    private static void mergeBlockedUntil(Map<Integer, Long> blockedUntilByEdge, int edgeId, long validUntilTicks) {
        blockedUntilByEdge.merge(edgeId, validUntilTicks, Math::max);
    }

    /**
     * Immutable active snapshot of quarantined failures.
     */
    @Getter
    @RequiredArgsConstructor
    @Accessors(fluent = true)
    public static final class Snapshot {
        private final String snapshotId;
        private final long capturedAtTicks;
        private final Map<Integer, FailureRecord> activeEdgeFailures;
        private final Map<Integer, FailureRecord> activeNodeFailures;
        private final Map<Integer, Long> blockedUntilByEdge;
        private final boolean nodeExpansionPrecomputed;

        public boolean hasActiveFailures() {
            return !activeEdgeFailures.isEmpty() || !activeNodeFailures.isEmpty();
        }

        public int activeEdgeFailureCount() {
            return activeEdgeFailures.size();
        }

        public int activeNodeFailureCount() {
            return activeNodeFailures.size();
        }

        /**
         * Returns the most recent known observation tick across active failures, or {@link Long#MIN_VALUE}
         * when none of the active failures have explicit freshness metadata.
         */
        public long mostRecentObservedAtTicks() {
            long mostRecent = Long.MIN_VALUE;
            for (FailureRecord record : activeEdgeFailures.values()) {
                mostRecent = Math.max(mostRecent, record.observedAtTicks());
            }
            for (FailureRecord record : activeNodeFailures.values()) {
                mostRecent = Math.max(mostRecent, record.observedAtTicks());
            }
            return mostRecent;
        }

        /**
         * Returns a stable set of explanation tags sourced from quarantine metadata.
         */
        public List<String> explanationTags() {
            TreeSet<String> tags = new TreeSet<>();
            activeEdgeFailures.values().forEach(record -> appendTags(tags, record));
            activeNodeFailures.values().forEach(record -> appendTags(tags, record));
            return List.copyOf(tags);
        }

        /**
         * Expands active failures into edge-blocking live updates.
         */
        public List<LiveUpdate> toLiveUpdates(EdgeGraph edgeGraph) {
            return toLiveUpdates(Long.MAX_VALUE, edgeGraph);
        }

        /**
         * Expands active failures into edge-blocking live updates without graph access when node expansion was precomputed.
         */
        public List<LiveUpdate> toLiveUpdates() {
            if (!nodeExpansionPrecomputed && !activeNodeFailures.isEmpty()) {
                throw new UnsupportedOperationException(
                        "node failure expansion requires a topology-bound quarantine or an EdgeGraph fallback"
                );
            }
            return toLiveUpdates(Long.MAX_VALUE, null);
        }

        /**
         * Expands active failures into edge-blocking live updates, clipping expiry when needed.
         */
        public List<LiveUpdate> toLiveUpdates(EdgeGraph edgeGraph, long maxValidUntilTicks) {
            return toLiveUpdates(maxValidUntilTicks, edgeGraph);
        }

        private List<LiveUpdate> toLiveUpdates(long maxValidUntilTicks, EdgeGraph edgeGraph) {
            Map<Integer, Long> expandedBlockedUntil = new HashMap<>(blockedUntilByEdge);
            if (!nodeExpansionPrecomputed && !activeNodeFailures.isEmpty()) {
                EdgeGraph nonNullEdgeGraph = Objects.requireNonNull(edgeGraph, "edgeGraph");
                expandNodeFailures(nonNullEdgeGraph, expandedBlockedUntil, maxValidUntilTicks);
            }

            ArrayList<Integer> sortedEdgeIds = new ArrayList<>(expandedBlockedUntil.keySet());
            sortedEdgeIds.sort(Integer::compareTo);
            ArrayList<LiveUpdate> updates = new ArrayList<>(sortedEdgeIds.size());
            for (int edgeId : sortedEdgeIds) {
                long validUntilTicks = clippedExpiry(expandedBlockedUntil.get(edgeId), maxValidUntilTicks);
                if (validUntilTicks <= capturedAtTicks) {
                    continue;
                }
                updates.add(LiveUpdate.of(edgeId, 0.0f, validUntilTicks));
            }
            return List.copyOf(updates);
        }

        private void expandNodeFailures(EdgeGraph edgeGraph, Map<Integer, Long> expandedBlockedUntil, long maxValidUntilTicks) {
            for (int edgeId = 0; edgeId < edgeGraph.edgeCount(); edgeId++) {
                int origin = edgeGraph.getEdgeOrigin(edgeId);
                int destination = edgeGraph.getEdgeDestination(edgeId);
                FailureRecord originFailure = activeNodeFailures.get(origin);
                if (originFailure != null) {
                    mergeBlockedUntil(
                            expandedBlockedUntil,
                            edgeId,
                            clippedExpiry(originFailure.validUntilTicks(), maxValidUntilTicks)
                    );
                }
                FailureRecord destinationFailure = activeNodeFailures.get(destination);
                if (destinationFailure != null) {
                    mergeBlockedUntil(
                            expandedBlockedUntil,
                            edgeId,
                            clippedExpiry(destinationFailure.validUntilTicks(), maxValidUntilTicks)
                    );
                }
            }
        }

        private static void appendTags(TreeSet<String> tags, FailureRecord record) {
            if (!record.reason().isBlank()) {
                tags.add(record.reason());
            }
            if (!record.source().isBlank()) {
                tags.add(record.source());
            }
        }

        private static long clippedExpiry(long validUntilTicks, long maxValidUntilTicks) {
            return Math.min(validUntilTicks, maxValidUntilTicks);
        }

        private static void mergeBlockedUntil(Map<Integer, Long> blockedUntilByEdge, int edgeId, long validUntilTicks) {
            blockedUntilByEdge.merge(edgeId, validUntilTicks, Math::max);
        }
    }
}
