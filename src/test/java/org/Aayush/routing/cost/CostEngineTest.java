package org.Aayush.routing.cost;

import com.google.flatbuffers.FlatBufferBuilder;
import org.Aayush.core.time.TimeUtils;
import org.Aayush.routing.graph.EdgeGraph;
import org.Aayush.routing.graph.TurnCostMap;
import org.Aayush.routing.overlay.LiveOverlay;
import org.Aayush.routing.overlay.LiveUpdate;
import org.Aayush.routing.profile.ProfileStore;
import org.Aayush.routing.traits.temporal.ResolvedTemporalContext;
import org.Aayush.routing.traits.temporal.TemporalPolicy;
import org.Aayush.routing.traits.temporal.TemporalRuntimeBinder;
import org.Aayush.routing.traits.temporal.TemporalRuntimeConfig;
import org.Aayush.routing.traits.temporal.TemporalStrategyRegistry;
import org.Aayush.routing.traits.temporal.TemporalTimezonePolicyRegistry;
import org.Aayush.routing.traits.temporal.TemporalTraitCatalog;
import org.Aayush.serialization.flatbuffers.taro.model.GraphTopology;
import org.Aayush.serialization.flatbuffers.taro.model.Metadata;
import org.Aayush.serialization.flatbuffers.taro.model.Model;
import org.Aayush.serialization.flatbuffers.taro.model.TemporalProfile;
import org.Aayush.serialization.flatbuffers.taro.model.TimeUnit;
import org.Aayush.serialization.flatbuffers.taro.model.TurnCost;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Stage 10 CostEngine Tests")
class CostEngineTest {

    private static final int ALL_DAYS_MASK = 0x7F;
    private static final int BUCKET_SIZE_SECONDS = 3_600; // 1-hour buckets
    // Jan 5, 1970 Monday 00:00:00 UTC
    private static final long MONDAY_00_00 = 345_600L;
    private static final long MONDAY_00_30 = MONDAY_00_00 + 1_800L;

    private record ProfileSpec(int profileId, int dayMask, float[] buckets, float multiplier) {}

    private record TurnSpec(int fromEdge, int toEdge, float penaltySeconds) {}

    private record Fixture(EdgeGraph graph, ProfileStore profiles, TurnCostMap turns, LiveOverlay overlay) {}

    @Test
    @DisplayName("Default policy is interpolated and exposes explainable breakdown")
    void testDefaultInterpolatedPolicyAndBreakdown() {
        Fixture fixture = createFixture();
        CostEngine engine = new CostEngine(
                fixture.graph(),
                fixture.profiles(),
                fixture.overlay(),
                TimeUtils.EngineTimeUnit.SECONDS,
                BUCKET_SIZE_SECONDS
        );

        assertEquals(CostEngine.TemporalSamplingPolicy.INTERPOLATED, engine.temporalSamplingPolicy());

        CostEngine.CostBreakdown breakdown = engine.explainEdgeCost(0, CostEngine.NO_PREDECESSOR, MONDAY_00_30);
        assertEquals(0, breakdown.edgeId());
        assertEquals(CostEngine.NO_PREDECESSOR, breakdown.fromEdgeId());
        assertEquals(0, breakdown.bucketIndex());
        assertEquals(0.5d, breakdown.fractionalBucket(), 1e-9);
        assertEquals(2.0f, breakdown.temporalMultiplier(), 1e-6f); // blend(1.0,3.0,0.5)
        assertEquals(20.0f, breakdown.effectiveCost(), 1e-6f);    // 10 * 2.0 * 1.0
        assertFalse(breakdown.turnPenaltyApplied());
    }

    @Test
    @DisplayName("Discrete policy uses integer bucket lookup")
    void testDiscretePolicy() {
        Fixture fixture = createFixture();
        CostEngine engine = new CostEngine(
                fixture.graph(),
                fixture.profiles(),
                fixture.overlay(),
                null,
                TimeUtils.EngineTimeUnit.SECONDS,
                BUCKET_SIZE_SECONDS,
                CostEngine.TemporalSamplingPolicy.DISCRETE
        );

        float cost = engine.computeEdgeCost(0, CostEngine.NO_PREDECESSOR, MONDAY_00_30);
        assertEquals(10.0f, cost, 1e-6f); // bucket 0 multiplier = 1.0
    }

    @Test
    @DisplayName("Blocked live update returns infinite edge cost")
    void testBlockedLiveReturnsInfinity() {
        Fixture fixture = createFixture();
        fixture.overlay().upsert(LiveUpdate.of(0, 0.0f, 1_000_000L), 0L);

        CostEngine engine = new CostEngine(
                fixture.graph(),
                fixture.profiles(),
                fixture.overlay(),
                TimeUtils.EngineTimeUnit.SECONDS,
                BUCKET_SIZE_SECONDS
        );

        assertEquals(Float.POSITIVE_INFINITY, engine.computeEdgeCost(0, CostEngine.NO_PREDECESSOR, MONDAY_00_00));
    }

    @Test
    @DisplayName("Slowdown live update follows division-based semantics")
    void testLiveSlowdownSemantics() {
        Fixture fixture = createFixture();
        fixture.overlay().upsert(LiveUpdate.of(0, 0.5f, 1_000_000L), 0L);

        CostEngine engine = new CostEngine(
                fixture.graph(),
                fixture.profiles(),
                fixture.overlay(),
                null,
                TimeUtils.EngineTimeUnit.SECONDS,
                BUCKET_SIZE_SECONDS,
                CostEngine.TemporalSamplingPolicy.DISCRETE
        );

        float cost = engine.computeEdgeCost(0, CostEngine.NO_PREDECESSOR, MONDAY_00_00);
        assertEquals(20.0f, cost, 1e-6f); // 10 * 1.0 * (1/0.5)
    }

    @Test
    @DisplayName("Expired live update falls back to neutral penalty")
    void testExpiredLiveFallback() {
        Fixture fixture = createFixture();
        fixture.overlay().upsert(LiveUpdate.of(0, 0.5f, 10L), 0L);

        CostEngine engine = new CostEngine(
                fixture.graph(),
                fixture.profiles(),
                fixture.overlay(),
                null,
                TimeUtils.EngineTimeUnit.SECONDS,
                BUCKET_SIZE_SECONDS,
                CostEngine.TemporalSamplingPolicy.DISCRETE
        );

        float cost = engine.computeEdgeCost(0, CostEngine.NO_PREDECESSOR, 10L);
        assertEquals(10.0f, cost, 1e-6f); // expired -> penalty 1.0
    }

    @Test
    @DisplayName("Turn cost is optional when map is absent")
    void testTurnCostOptionalWhenMissingMap() {
        Fixture fixture = createFixture(new TurnSpec(1, 0, 5.0f));

        CostEngine engineWithoutTurns = new CostEngine(
                fixture.graph(),
                fixture.profiles(),
                fixture.overlay(),
                null,
                TimeUtils.EngineTimeUnit.SECONDS,
                BUCKET_SIZE_SECONDS,
                CostEngine.TemporalSamplingPolicy.DISCRETE
        );

        float cost = engineWithoutTurns.computeEdgeCost(0, 1, MONDAY_00_00);
        assertEquals(10.0f, cost, 1e-6f); // no turn map => no turn penalty applied
    }

    @Test
    @DisplayName("Turn cost applies when map and predecessor are both present")
    void testTurnCostAppliedWhenAvailable() {
        Fixture fixture = createFixture(new TurnSpec(1, 0, 5.0f));

        CostEngine engine = new CostEngine(
                fixture.graph(),
                fixture.profiles(),
                fixture.overlay(),
                fixture.turns(),
                TimeUtils.EngineTimeUnit.SECONDS,
                BUCKET_SIZE_SECONDS,
                CostEngine.TemporalSamplingPolicy.DISCRETE
        );

        float cost = engine.computeEdgeCost(0, 1, MONDAY_00_00);
        assertEquals(15.0f, cost, 1e-6f); // edge(10) + turn(5)
    }

    @Test
    @DisplayName("No predecessor skips turn penalty even when turn map exists")
    void testNoPredecessorSkipsTurnPenalty() {
        Fixture fixture = createFixture(new TurnSpec(1, 0, 5.0f));

        CostEngine engine = new CostEngine(
                fixture.graph(),
                fixture.profiles(),
                fixture.overlay(),
                fixture.turns(),
                TimeUtils.EngineTimeUnit.SECONDS,
                BUCKET_SIZE_SECONDS,
                CostEngine.TemporalSamplingPolicy.DISCRETE
        );

        float cost = engine.computeEdgeCost(0, CostEngine.NO_PREDECESSOR, MONDAY_00_00);
        assertEquals(10.0f, cost, 1e-6f);
    }

    @Test
    @DisplayName("Forbidden turn produces infinite effective cost")
    void testForbiddenTurnProducesInfinity() {
        Fixture fixture = createFixture(new TurnSpec(1, 0, Float.POSITIVE_INFINITY));

        CostEngine engine = new CostEngine(
                fixture.graph(),
                fixture.profiles(),
                fixture.overlay(),
                fixture.turns(),
                TimeUtils.EngineTimeUnit.SECONDS,
                BUCKET_SIZE_SECONDS,
                CostEngine.TemporalSamplingPolicy.DISCRETE
        );

        CostEngine.CostBreakdown breakdown = engine.explainEdgeCost(0, 1, MONDAY_00_00);
        assertEquals(Float.POSITIVE_INFINITY, breakdown.effectiveCost());
        assertTrue(breakdown.turnPenaltyApplied());
        assertTrue(breakdown.forbiddenTurn());
    }

    @Test
    @DisplayName("Validates constructor and input edge bounds")
    void testValidation() {
        Fixture fixture = createFixture();

        assertThrows(NullPointerException.class,
                () -> new CostEngine(null, fixture.profiles(), fixture.overlay(),
                        TimeUtils.EngineTimeUnit.SECONDS, BUCKET_SIZE_SECONDS));
        assertThrows(IllegalArgumentException.class,
                () -> new CostEngine(fixture.graph(), fixture.profiles(), fixture.overlay(),
                        TimeUtils.EngineTimeUnit.SECONDS, 0));

        CostEngine engine = new CostEngine(
                fixture.graph(),
                fixture.profiles(),
                fixture.overlay(),
                TimeUtils.EngineTimeUnit.SECONDS,
                BUCKET_SIZE_SECONDS
        );
        assertThrows(IllegalArgumentException.class, () -> engine.computeEdgeCost(-1, MONDAY_00_00));
        assertThrows(IllegalArgumentException.class, () -> engine.computeEdgeCost(99, MONDAY_00_00));
        assertThrows(IllegalArgumentException.class, () -> engine.computeEdgeCost(0, -2, MONDAY_00_00));
        assertThrows(IllegalArgumentException.class, () -> engine.computeEdgeCost(0, 99, MONDAY_00_00));
    }

    @Test
    @DisplayName("Blocked live breakdown reports blocked flag deterministically")
    void testBlockedLiveBreakdownFlags() {
        Fixture fixture = createFixture();
        fixture.overlay().upsert(LiveUpdate.of(0, 0.0f, 1_000_000L), 0L);

        CostEngine engine = new CostEngine(
                fixture.graph(),
                fixture.profiles(),
                fixture.overlay(),
                TimeUtils.EngineTimeUnit.SECONDS,
                BUCKET_SIZE_SECONDS
        );

        CostEngine.CostBreakdown breakdown = engine.explainEdgeCost(0, MONDAY_00_00);
        assertEquals(Float.POSITIVE_INFINITY, breakdown.effectiveCost());
        assertTrue(breakdown.blockedByLive());
        assertFalse(breakdown.forbiddenTurn());
    }

    @Test
    @DisplayName("Allocation-free explain path matches immutable breakdown values")
    void testAllocationFreeExplainPathParity() {
        Fixture fixture = createFixture(new TurnSpec(1, 0, 5.0f));
        fixture.overlay().upsert(LiveUpdate.of(0, 0.5f, 1_000_000L), 0L);

        CostEngine engine = new CostEngine(
                fixture.graph(),
                fixture.profiles(),
                fixture.overlay(),
                fixture.turns(),
                TimeUtils.EngineTimeUnit.SECONDS,
                BUCKET_SIZE_SECONDS,
                CostEngine.TemporalSamplingPolicy.DISCRETE
        );

        CostEngine.MutableCostBreakdown mutable = new CostEngine.MutableCostBreakdown();
        engine.explainEdgeCost(0, 1, MONDAY_00_00, mutable);
        CostEngine.CostBreakdown immutable = engine.explainEdgeCost(0, 1, MONDAY_00_00);

        assertEquals(immutable.edgeId(), mutable.edgeId);
        assertEquals(immutable.fromEdgeId(), mutable.fromEdgeId);
        assertEquals(immutable.entryTicks(), mutable.entryTicks);
        assertEquals(immutable.profileId(), mutable.profileId);
        assertEquals(immutable.dayOfWeek(), mutable.dayOfWeek);
        assertEquals(immutable.bucketIndex(), mutable.bucketIndex);
        assertEquals(immutable.temporalSamplingPolicy(), mutable.temporalSamplingPolicy);
        assertEquals(immutable.turnPenaltyApplied(), mutable.turnPenaltyApplied);
        assertEquals(immutable.liveState(), mutable.liveState);

        assertEquals(immutable.baseWeight(), mutable.baseWeight, 1e-6f);
        assertEquals(immutable.temporalMultiplier(), mutable.temporalMultiplier, 1e-6f);
        assertEquals(immutable.livePenaltyMultiplier(), mutable.livePenaltyMultiplier, 1e-6f);
        assertEquals(immutable.turnPenalty(), mutable.turnPenalty, 1e-6f);
        assertEquals(immutable.edgeTravelCost(), mutable.edgeTravelCost, 1e-6f);
        assertEquals(immutable.effectiveCost(), mutable.effectiveCost, 1e-6f);
    }

    @Test
    @DisplayName("Determinism: repeated and concurrent reads return stable costs")
    void testDeterministicRepeatedAndConcurrentReads() throws InterruptedException {
        Fixture fixture = createFixture();
        fixture.overlay().upsert(LiveUpdate.of(0, 0.75f, 1_000_000L), 0L);
        fixture.overlay().upsert(LiveUpdate.of(1, 0.50f, 1_000_000L), 0L);

        CostEngine engine = new CostEngine(
                fixture.graph(),
                fixture.profiles(),
                fixture.overlay(),
                TimeUtils.EngineTimeUnit.SECONDS,
                BUCKET_SIZE_SECONDS
        );

        float expectedEdge0 = engine.computeEdgeCost(0, MONDAY_00_30);
        float expectedEdge1 = engine.computeEdgeCost(1, MONDAY_00_30);

        for (int i = 0; i < 10_000; i++) {
            assertEquals(expectedEdge0, engine.computeEdgeCost(0, MONDAY_00_30), 1e-6f);
            assertEquals(expectedEdge1, engine.computeEdgeCost(1, MONDAY_00_30), 1e-6f);
        }

        int threads = 8;
        int loopsPerThread = 20_000;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicBoolean failed = new AtomicBoolean(false);

        for (int t = 0; t < threads; t++) {
            executor.execute(() -> {
                try {
                    for (int i = 0; i < loopsPerThread; i++) {
                        float edge0 = engine.computeEdgeCost(0, MONDAY_00_30);
                        float edge1 = engine.computeEdgeCost(1, MONDAY_00_30);
                        if (Math.abs(edge0 - expectedEdge0) > 1e-6f || Math.abs(edge1 - expectedEdge1) > 1e-6f) {
                            failed.set(true);
                            break;
                        }
                    }
                } catch (Throwable t1) {
                    failed.set(true);
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(20, java.util.concurrent.TimeUnit.SECONDS), "Concurrent determinism test timed out");
        executor.shutdownNow();
        assertFalse(failed.get(), "Concurrent reads produced non-deterministic costs");
    }

    @Test
    @DisplayName("Explicit LINEAR temporal context ignores day masks")
    void testLinearTemporalContextIgnoresDayMask() {
        ProfileSpec[] profiles = new ProfileSpec[]{
                new ProfileSpec(1, 0x1F, new float[]{2.0f, 2.0f}, 1.0f), // Mon..Fri only
                new ProfileSpec(2, ALL_DAYS_MASK, new float[]{2.0f, 2.0f}, 1.0f)
        };
        ByteBuffer model = buildModelBuffer(profiles, null);
        EdgeGraph edgeGraph = EdgeGraph.fromFlatBuffer(model.duplicate().order(ByteOrder.LITTLE_ENDIAN));
        ProfileStore profileStore = ProfileStore.fromFlatBuffer(model.duplicate().order(ByteOrder.LITTLE_ENDIAN));

        CostEngine engine = new CostEngine(
                edgeGraph,
                profileStore,
                new LiveOverlay(32),
                null,
                TimeUtils.EngineTimeUnit.SECONDS,
                BUCKET_SIZE_SECONDS,
                CostEngine.TemporalSamplingPolicy.DISCRETE
        );

        TemporalRuntimeBinder binder = new TemporalRuntimeBinder();
        ResolvedTemporalContext calendarContext = binder.bind(
                TemporalRuntimeConfig.calendarUtc(),
                TemporalTraitCatalog.defaultCatalog(),
                TemporalStrategyRegistry.defaultRegistry(),
                TemporalTimezonePolicyRegistry.defaultRegistry(),
                TemporalPolicy.defaults()
        ).getResolvedTemporalContext();
        ResolvedTemporalContext linearContext = binder.bind(
                TemporalRuntimeConfig.linear(),
                TemporalTraitCatalog.defaultCatalog(),
                TemporalStrategyRegistry.defaultRegistry(),
                TemporalTimezonePolicyRegistry.defaultRegistry(),
                TemporalPolicy.defaults()
        ).getResolvedTemporalContext();

        long sundayUtc = 259_200L; // 1970-01-04 Sunday
        float calendarCost = engine.computeEdgeCost(0, CostEngine.NO_PREDECESSOR, sundayUtc, calendarContext);
        float linearCost = engine.computeEdgeCost(0, CostEngine.NO_PREDECESSOR, sundayUtc, linearContext);

        assertEquals(10.0f, calendarCost, 1e-6f); // day mask inactive -> DEFAULT_MULTIPLIER = 1.0
        assertEquals(20.0f, linearCost, 1e-6f);   // day mask ignored -> profile multiplier = 2.0
    }

    private Fixture createFixture(TurnSpec... turns) {
        ProfileSpec[] profiles = new ProfileSpec[]{
                new ProfileSpec(1, ALL_DAYS_MASK, new float[]{1.0f, 3.0f}, 1.0f),
                new ProfileSpec(2, ALL_DAYS_MASK, new float[]{2.0f, 2.0f}, 1.0f)
        };
        ByteBuffer model = buildModelBuffer(profiles, turns);
        EdgeGraph edgeGraph = EdgeGraph.fromFlatBuffer(model.duplicate().order(ByteOrder.LITTLE_ENDIAN));
        ProfileStore profileStore = ProfileStore.fromFlatBuffer(model.duplicate().order(ByteOrder.LITTLE_ENDIAN));
        TurnCostMap turnCostMap = TurnCostMap.fromFlatBuffer(model.duplicate().order(ByteOrder.LITTLE_ENDIAN));
        LiveOverlay overlay = new LiveOverlay(32);
        return new Fixture(edgeGraph, profileStore, turnCostMap, overlay);
    }

    private ByteBuffer buildModelBuffer(ProfileSpec[] profiles, TurnSpec[] turns) {
        FlatBufferBuilder builder = new FlatBufferBuilder(4096);

        int firstEdgeVec = GraphTopology.createFirstEdgeVector(builder, new int[]{0, 1, 2, 2});
        int edgeTargetVec = GraphTopology.createEdgeTargetVector(builder, new int[]{1, 2});
        int baseWeightsVec = GraphTopology.createBaseWeightsVector(builder, new float[]{10.0f, 20.0f});
        int edgeProfileIdVec = GraphTopology.createEdgeProfileIdVector(builder, new int[]{1, 2});
        int edgeOriginVec = GraphTopology.createEdgeOriginVector(builder, new int[]{0, 1});

        GraphTopology.startGraphTopology(builder);
        GraphTopology.addNodeCount(builder, 3);
        GraphTopology.addEdgeCount(builder, 2);
        GraphTopology.addFirstEdge(builder, firstEdgeVec);
        GraphTopology.addEdgeTarget(builder, edgeTargetVec);
        GraphTopology.addBaseWeights(builder, baseWeightsVec);
        GraphTopology.addEdgeProfileId(builder, edgeProfileIdVec);
        GraphTopology.addEdgeOrigin(builder, edgeOriginVec);
        int topology = GraphTopology.endGraphTopology(builder);

        int profilesVec = 0;
        if (profiles != null && profiles.length > 0) {
            int[] profileOffsets = new int[profiles.length];
            for (int i = 0; i < profiles.length; i++) {
                ProfileSpec profile = profiles[i];
                int buckets = TemporalProfile.createBucketsVector(builder, profile.buckets());
                profileOffsets[i] = TemporalProfile.createTemporalProfile(
                        builder,
                        profile.profileId(),
                        profile.dayMask(),
                        buckets,
                        profile.multiplier()
                );
            }
            profilesVec = Model.createProfilesVector(builder, profileOffsets);
        }

        int turnsVec = 0;
        if (turns != null && turns.length > 0) {
            int[] turnOffsets = new int[turns.length];
            for (int i = 0; i < turns.length; i++) {
                TurnSpec turn = turns[i];
                turnOffsets[i] = TurnCost.createTurnCost(builder, turn.fromEdge(), turn.toEdge(), turn.penaltySeconds());
            }
            turnsVec = Model.createTurnCostsVector(builder, turnOffsets);
        }

        int metadata = createMetadata(builder);

        Model.startModel(builder);
        Model.addMetadata(builder, metadata);
        Model.addTopology(builder, topology);
        if (profilesVec != 0) {
            Model.addProfiles(builder, profilesVec);
        }
        if (turnsVec != 0) {
            Model.addTurnCosts(builder, turnsVec);
        }
        int root = Model.endModel(builder);
        Model.finishModelBuffer(builder, root);
        return ByteBuffer.wrap(builder.sizedByteArray()).order(ByteOrder.LITTLE_ENDIAN);
    }

    private int createMetadata(FlatBufferBuilder builder) {
        int modelVersion = builder.createString("cost-engine-test");
        Metadata.startMetadata(builder);
        Metadata.addSchemaVersion(builder, 1L);
        Metadata.addModelVersion(builder, modelVersion);
        Metadata.addTimeUnit(builder, TimeUnit.SECONDS);
        Metadata.addTickDurationNs(builder, 1_000_000_000L);
        return Metadata.endMetadata(builder);
    }
}
