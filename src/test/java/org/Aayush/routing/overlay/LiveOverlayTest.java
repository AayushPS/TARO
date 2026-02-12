package org.Aayush.routing.overlay;

import org.Aayush.core.time.TimeUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Live Overlay Tests")
class LiveOverlayTest {

    @Test
    @DisplayName("LiveUpdate validates edge and speed_factor domain")
    void testLiveUpdateValidation() {
        assertThrows(IllegalArgumentException.class, () -> LiveUpdate.of(-1, 0.5f, 100));
        assertThrows(IllegalArgumentException.class, () -> LiveUpdate.of(1, -0.1f, 100));
        assertThrows(IllegalArgumentException.class, () -> LiveUpdate.of(1, 1.1f, 100));
        assertThrows(IllegalArgumentException.class, () -> LiveUpdate.of(1, Float.NaN, 100));
        assertThrows(IllegalArgumentException.class, () -> LiveUpdate.of(1, Float.POSITIVE_INFINITY, 100));
        assertThrows(IllegalArgumentException.class, () -> LiveUpdate.of(1, Float.NEGATIVE_INFINITY, 100));

        LiveUpdate blocked = LiveUpdate.of(10, 0.0f, 200);
        assertEquals(0.0f, blocked.speedFactor());
        LiveUpdate active = LiveUpdate.of(10, 1.0f, 200);
        assertEquals(1.0f, active.speedFactor());
    }

    @Test
    @DisplayName("TTL normalization to engine ticks works for mixed units")
    void testTtlNormalization() {
        LiveUpdate secToMs = LiveUpdate.fromRelativeTtl(
                1,
                0.75f,
                1_000L,
                2L,
                TimeUtils.EngineTimeUnit.SECONDS,
                TimeUtils.EngineTimeUnit.MILLISECONDS
        );
        assertEquals(3_000L, secToMs.validUntilTicks());

        LiveUpdate msToSec = LiveUpdate.fromRelativeTtl(
                2,
                0.50f,
                100L,
                2_500L,
                TimeUtils.EngineTimeUnit.MILLISECONDS,
                TimeUtils.EngineTimeUnit.SECONDS
        );
        assertEquals(102L, msToSec.validUntilTicks());
    }

    @Test
    @DisplayName("Relative TTL validation rejects negative/null/overflow inputs")
    void testRelativeTtlValidation() {
        assertThrows(IllegalArgumentException.class,
                () -> LiveUpdate.fromRelativeTtl(1, 0.5f, 10L, -1L));
        assertThrows(ArithmeticException.class,
                () -> LiveUpdate.fromRelativeTtl(1, 0.5f, Long.MAX_VALUE, 1L));

        assertThrows(IllegalArgumentException.class,
                () -> LiveUpdate.fromRelativeTtl(
                        1, 0.5f, 10L, -1L,
                        TimeUtils.EngineTimeUnit.SECONDS,
                        TimeUtils.EngineTimeUnit.SECONDS
                ));
        assertThrows(NullPointerException.class,
                () -> LiveUpdate.fromRelativeTtl(1, 0.5f, 10L, 1L, null, TimeUtils.EngineTimeUnit.SECONDS));
        assertThrows(NullPointerException.class,
                () -> LiveUpdate.fromRelativeTtl(1, 0.5f, 10L, 1L, TimeUtils.EngineTimeUnit.SECONDS, null));
    }

    @Test
    @DisplayName("Overlay validates constructor and ingest inputs")
    void testOverlayInputValidation() {
        assertThrows(IllegalArgumentException.class,
                () -> new LiveOverlay(0, LiveOverlay.CapacityPolicy.REJECT_BATCH, 0, true));
        assertThrows(IllegalArgumentException.class,
                () -> new LiveOverlay(1, LiveOverlay.CapacityPolicy.REJECT_BATCH, -1, true));
        assertThrows(NullPointerException.class,
                () -> new LiveOverlay(1, null, 0, true));

        LiveOverlay overlay = new LiveOverlay(4);
        assertThrows(IllegalArgumentException.class, () -> overlay.lookup(-1, 0));
        assertThrows(NullPointerException.class, () -> overlay.upsert(null, 0));
        assertThrows(NullPointerException.class, () -> overlay.applyBatch(null, 0));
        assertThrows(NullPointerException.class, () -> overlay.applyBatch(List.of((LiveUpdate) null), 0));

        LiveOverlay.BatchApplyResult empty = overlay.applyBatch(List.of(), 0);
        assertEquals(0, empty.accepted());
        assertEquals(0, empty.rejectedCapacity());
        assertEquals(0, empty.rejectedExpiredAtIngest());
    }

    @Test
    @DisplayName("Lookup returns missing, active, blocked, expired semantics")
    void testLookupSemantics() {
        LiveOverlay overlay = new LiveOverlay(16);

        assertEquals(LiveOverlay.LookupState.MISSING, overlay.lookup(1, 0).state());

        assertTrue(overlay.upsert(LiveUpdate.of(1, 0.4f, 100), 0));
        LiveOverlay.LookupResult active = overlay.lookup(1, 10);
        assertEquals(LiveOverlay.LookupState.ACTIVE, active.state());
        assertEquals(0.4f, active.speedFactor(), 0.0001f);

        assertTrue(overlay.upsert(LiveUpdate.of(1, 0.0f, 100), 10));
        LiveOverlay.LookupResult blocked = overlay.lookup(1, 10);
        assertEquals(LiveOverlay.LookupState.BLOCKED, blocked.state());
        assertEquals(Float.POSITIVE_INFINITY, blocked.livePenaltyMultiplier());

        assertTrue(overlay.upsert(LiveUpdate.of(2, 0.8f, 30), 0));
        LiveOverlay.LookupResult expired = overlay.lookup(2, 30);
        assertEquals(LiveOverlay.LookupState.EXPIRED, expired.state());
        assertEquals(LiveOverlay.LookupState.EXPIRED, overlay.lookup(2, 30).state());
        assertEquals(2, overlay.size(), "Lookup should not mutate overlay state");
    }

    @Test
    @DisplayName("Live penalty formula obeys Stage 7 semantics")
    void testLivePenaltySemantics() {
        LiveOverlay overlay = new LiveOverlay(8);
        assertEquals(1.0f, overlay.livePenaltyMultiplier(77, 0), 0.0001f);

        assertTrue(overlay.upsert(LiveUpdate.of(77, 0.5f, 100), 0));
        assertEquals(2.0f, overlay.livePenaltyMultiplier(77, 10), 0.0001f);

        assertTrue(overlay.upsert(LiveUpdate.of(77, 0.0f, 100), 10));
        assertEquals(Float.POSITIVE_INFINITY, overlay.livePenaltyMultiplier(77, 10));
    }

    @Test
    @DisplayName("Capacity policy: reject on cap breach")
    void testCapacityRejectPolicy() {
        LiveOverlay overlay = new LiveOverlay(
                2,
                LiveOverlay.CapacityPolicy.REJECT_BATCH,
                0,
                true
        );

        LiveOverlay.BatchApplyResult result = overlay.applyBatch(List.of(
                LiveUpdate.of(1, 0.9f, 100),
                LiveUpdate.of(2, 0.8f, 100),
                LiveUpdate.of(3, 0.7f, 100)
        ), 0);

        assertEquals(0, result.accepted());
        assertEquals(3, result.rejectedCapacity());
        assertEquals(0, overlay.size());
        assertEquals(LiveOverlay.LookupState.MISSING, overlay.lookup(3, 10).state());
    }

    @Test
    @DisplayName("Reject-batch precheck counts expired ingest separately")
    void testRejectBatchPrecheckExpiredAccounting() {
        LiveOverlay overlay = new LiveOverlay(
                1,
                LiveOverlay.CapacityPolicy.REJECT_BATCH,
                0,
                true
        );
        LiveOverlay.BatchApplyResult result = overlay.applyBatch(List.of(
                LiveUpdate.of(1, 0.9f, 50),  // active
                LiveUpdate.of(2, 0.8f, 50),  // active
                LiveUpdate.of(3, 0.7f, 0)    // expired at ingest
        ), 10);

        assertEquals(0, result.accepted());
        assertEquals(1, result.rejectedExpiredAtIngest());
        assertEquals(2, result.rejectedCapacity());
    }

    @Test
    @DisplayName("Capacity policy: evict expired then accept")
    void testCapacityEvictExpiredThenRejectPolicy() {
        LiveOverlay overlay = new LiveOverlay(
                1,
                LiveOverlay.CapacityPolicy.EVICT_EXPIRED_THEN_REJECT,
                8,
                true
        );

        assertTrue(overlay.upsert(LiveUpdate.of(1, 0.7f, 10), 0));
        assertTrue(overlay.upsert(LiveUpdate.of(2, 0.6f, 50), 20));

        assertEquals(1, overlay.size());
        assertEquals(LiveOverlay.LookupState.MISSING, overlay.lookup(1, 20).state());
        assertEquals(LiveOverlay.LookupState.ACTIVE, overlay.lookup(2, 20).state());
    }

    @Test
    @DisplayName("Capacity policy: evict expired then reject when still full")
    void testCapacityEvictExpiredThenRejectStillFull() {
        LiveOverlay overlay = new LiveOverlay(
                1,
                LiveOverlay.CapacityPolicy.EVICT_EXPIRED_THEN_REJECT,
                0,
                true
        );
        assertTrue(overlay.upsert(LiveUpdate.of(1, 0.9f, 100), 0));

        LiveOverlay.BatchApplyResult result = overlay.applyBatch(
                List.of(LiveUpdate.of(2, 0.9f, 100)),
                0
        );
        assertEquals(0, result.accepted());
        assertEquals(1, result.rejectedCapacity());
        assertEquals(1, overlay.size());
        assertEquals(LiveOverlay.LookupState.MISSING, overlay.lookup(2, 0).state());
    }

    @Test
    @DisplayName("Capacity policy: oldest-expiry eviction")
    void testCapacityOldestExpiryEviction() {
        LiveOverlay overlay = new LiveOverlay(
                2,
                LiveOverlay.CapacityPolicy.EVICT_OLDEST_EXPIRY,
                0,
                true
        );

        assertTrue(overlay.upsert(LiveUpdate.of(1, 0.9f, 100), 0));
        assertTrue(overlay.upsert(LiveUpdate.of(2, 0.8f, 200), 0));
        assertTrue(overlay.upsert(LiveUpdate.of(3, 0.7f, 300), 0));

        assertEquals(2, overlay.size());
        assertEquals(LiveOverlay.LookupState.MISSING, overlay.lookup(1, 0).state());
        assertEquals(LiveOverlay.LookupState.ACTIVE, overlay.lookup(2, 0).state());
        assertEquals(LiveOverlay.LookupState.ACTIVE, overlay.lookup(3, 0).state());
    }

    @Test
    @DisplayName("Scheduled sweep respects removal budget")
    void testScheduledSweepBudget() {
        LiveOverlay overlay = new LiveOverlay(10);
        overlay.upsert(LiveUpdate.of(1, 0.5f, 5), 0);
        overlay.upsert(LiveUpdate.of(2, 0.5f, 5), 0);
        overlay.upsert(LiveUpdate.of(3, 0.5f, 5), 0);
        overlay.upsert(LiveUpdate.of(4, 0.5f, 50), 0);

        int removed = overlay.runScheduledSweep(10, 2);
        assertEquals(2, removed);
        assertEquals(2, overlay.size());

        removed = overlay.runScheduledSweep(10, 0); // unbounded
        assertEquals(1, removed);
        assertEquals(1, overlay.size());
        assertEquals(LiveOverlay.LookupState.ACTIVE, overlay.lookup(4, 10).state());
    }

    @Test
    @DisplayName("Lookup reads do not mutate repeated expired-entry semantics")
    void testLookupStabilityForExpiredEntries() {
        LiveOverlay overlay = new LiveOverlay(
                4,
                LiveOverlay.CapacityPolicy.REJECT_BATCH,
                0,
                true
        );
        assertTrue(overlay.upsert(LiveUpdate.of(9, 0.8f, 10), 0));

        LiveOverlay.LookupResult first = overlay.lookup(9, 10);
        LiveOverlay.LookupResult second = overlay.lookup(9, 10);
        assertEquals(LiveOverlay.LookupState.EXPIRED, first.state());
        assertEquals(LiveOverlay.LookupState.EXPIRED, second.state());
        assertEquals(1.0f, first.livePenaltyMultiplier(), 0.0001f);
        assertEquals(1.0f, second.livePenaltyMultiplier(), 0.0001f);
        assertEquals(1, overlay.size(), "Expired entry cleanup should not happen on read");
    }

    @Test
    @DisplayName("Read cleanup can be disabled for deterministic retention")
    void testReadCleanupDisabled() {
        LiveOverlay overlay = new LiveOverlay(
                4,
                LiveOverlay.CapacityPolicy.REJECT_BATCH,
                0,
                false
        );
        assertTrue(overlay.upsert(LiveUpdate.of(5, 0.8f, 10), 0));
        assertEquals(LiveOverlay.LookupState.EXPIRED, overlay.lookup(5, 10).state());
        assertEquals(LiveOverlay.LookupState.EXPIRED, overlay.lookup(5, 10).state());
        assertEquals(1, overlay.size(), "Expired entry should remain when read cleanup is disabled");
    }

    @Test
    @DisplayName("Overlay clear removes all tracked entries")
    void testClearOverlay() {
        LiveOverlay overlay = new LiveOverlay(8);
        assertTrue(overlay.upsert(LiveUpdate.of(1, 0.5f, 100), 0));
        assertTrue(overlay.upsert(LiveUpdate.of(2, 0.5f, 100), 0));
        assertEquals(2, overlay.size());

        overlay.clear();
        assertEquals(0, overlay.size());
        assertEquals(LiveOverlay.LookupState.MISSING, overlay.lookup(1, 0).state());
        assertEquals(LiveOverlay.LookupState.MISSING, overlay.lookup(2, 0).state());
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("Concurrent read/write workload remains consistent")
    void testConcurrentReadWrite() throws InterruptedException {
        LiveOverlay overlay = new LiveOverlay(1_000);
        AtomicBoolean failed = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(6);
        ExecutorService executor = Executors.newFixedThreadPool(6);

        for (int i = 0; i < 2; i++) {
            final int seed = 100 + i;
            executor.execute(() -> {
                try {
                    Random random = new Random(seed);
                    for (int j = 0; j < 20_000; j++) {
                        int edgeId = random.nextInt(1_000);
                        float speed = random.nextFloat(); // [0,1)
                        long validUntil = 1_000 + random.nextInt(1_000);
                        overlay.upsert(LiveUpdate.of(edgeId, speed, validUntil), 0);
                    }
                } catch (Throwable t) {
                    failed.set(true);
                } finally {
                    latch.countDown();
                }
            });
        }

        for (int i = 0; i < 4; i++) {
            final int seed = 300 + i;
            executor.execute(() -> {
                try {
                    Random random = new Random(seed);
                    for (int j = 0; j < 50_000; j++) {
                        int edgeId = random.nextInt(1_000);
                        LiveOverlay.LookupResult result = overlay.lookup(edgeId, 0);
                        float penalty = result.livePenaltyMultiplier();
                        if (Float.isNaN(penalty) || penalty < 1.0f) {
                            failed.set(true);
                            break;
                        }
                    }
                } catch (Throwable t) {
                    failed.set(true);
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS), "Workload timed out");
        executor.shutdownNow();
        assertFalse(failed.get(), "Overlay should not produce invalid states under concurrency");
    }
}
