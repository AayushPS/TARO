package org.Aayush.routing.profile;

import com.google.flatbuffers.FlatBufferBuilder;
import org.Aayush.serialization.flatbuffers.taro.model.Metadata;
import org.Aayush.serialization.flatbuffers.taro.model.Model;
import org.Aayush.serialization.flatbuffers.taro.model.TemporalProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Stage 9 ProfileStore Tests")
class ProfileStoreTest {

    private static final int ALL_DAYS = 0x7F;
    private static final int WEEKDAY_MASK = 0x1F; // Mon..Fri
    private static final int WEEKEND_MASK = 0x60; // Sat + Sun

    private record ProfileSpec(int profileId, int dayMask, float[] buckets, float multiplier) {}

    @Test
    @DisplayName("Loads profiles and supports O(1)-style direct bucket lookup")
    void testLoadAndDirectLookup() {
        ByteBuffer model = buildModelBuffer(
                new ProfileSpec(7, ALL_DAYS, new float[]{1.0f, 2.0f, 3.0f, 4.0f}, 2.0f),
                new ProfileSpec(11, WEEKEND_MASK, new float[]{1.5f, 1.5f, 2.0f, 2.5f}, 1.0f)
        );

        ProfileStore store = ProfileStore.fromFlatBuffer(model.duplicate().order(ByteOrder.LITTLE_ENDIAN));

        assertEquals(2, store.profileCount());
        assertTrue(store.hasProfile(7));
        assertTrue(store.hasProfile(11));
        assertFalse(store.hasProfile(5));

        assertEquals(4, store.bucketCount(7));
        assertEquals(2.0f, store.getMultiplier(7, 0), 1e-6f);
        assertEquals(8.0f, store.getMultiplier(7, 3), 1e-6f);
        assertEquals(2.5f, store.getMultiplier(11, 3), 1e-6f);
    }

    @Test
    @DisplayName("Day-mask selection is deterministic with neutral fallback")
    void testDayMaskSelectionAndFallback() {
        ByteBuffer model = buildModelBuffer(
                new ProfileSpec(3, WEEKDAY_MASK, new float[]{1.0f, 1.2f, 1.4f, 1.6f}, 1.0f)
        );

        ProfileStore store = ProfileStore.fromFlatBuffer(model.duplicate().order(ByteOrder.LITTLE_ENDIAN));

        assertTrue(store.isActiveOnDay(3, 0)); // Monday
        assertFalse(store.isActiveOnDay(3, 6)); // Sunday

        assertEquals(3, store.selectProfileForDay(3, 2)); // Wednesday
        assertEquals(ProfileStore.DEFAULT_PROFILE_ID, store.selectProfileForDay(3, 6)); // Sunday fallback
        assertEquals(ProfileStore.DEFAULT_PROFILE_ID, store.selectProfileForDay(999, 2)); // Missing profile fallback

        assertEquals(1.4f, store.getMultiplierForDay(3, 4, 2), 1e-6f);
        assertEquals(ProfileStore.DEFAULT_MULTIPLIER, store.getMultiplierForDay(3, 6, 2), 1e-6f);
    }

    @Test
    @DisplayName("Interpolation policy is cyclic linear with wrap-around")
    void testInterpolationPolicy() {
        ByteBuffer model = buildModelBuffer(
                new ProfileSpec(9, ALL_DAYS, new float[]{1.0f, 3.0f, 5.0f, 7.0f}, 1.0f)
        );

        ProfileStore store = ProfileStore.fromFlatBuffer(model.duplicate().order(ByteOrder.LITTLE_ENDIAN));

        assertEquals(2.0f, store.interpolate(9, 0.5), 1e-6f);
        assertEquals(4.0f, store.interpolate(9, 1.5), 1e-6f);
        assertEquals(4.0f, store.interpolate(9, 3.5), 1e-6f);   // wraps to bucket 0
        assertEquals(4.0f, store.interpolate(9, -0.5), 1e-6f);  // negative wraps to tail
        assertEquals(1.0f, store.interpolate(9, 4.0), 1e-6f);   // exact wrap
    }

    @Test
    @DisplayName("Metadata reports avg/min/max multipliers")
    void testMetadata() {
        ByteBuffer model = buildModelBuffer(
                new ProfileSpec(2, ALL_DAYS, new float[]{1.0f, 2.0f, 3.0f, 4.0f}, 2.0f)
        );

        ProfileStore store = ProfileStore.fromFlatBuffer(model.duplicate().order(ByteOrder.LITTLE_ENDIAN));
        ProfileStore.ProfileMetadata metadata = store.getMetadata(2);

        assertEquals(5.0f, metadata.avgMultiplier(), 1e-6f);
        assertEquals(2.0f, metadata.minMultiplier(), 1e-6f);
        assertEquals(8.0f, metadata.maxMultiplier(), 1e-6f);

        ProfileStore.ProfileMetadata fallback = store.getMetadata(999);
        assertEquals(1.0f, fallback.avgMultiplier(), 1e-6f);
        assertEquals(1.0f, fallback.minMultiplier(), 1e-6f);
        assertEquals(1.0f, fallback.maxMultiplier(), 1e-6f);
    }

    @Test
    @DisplayName("Missing profile fallback returns neutral multiplier")
    void testMissingProfileFallback() {
        ByteBuffer model = buildModelBuffer(); // no profiles
        ProfileStore store = ProfileStore.fromFlatBuffer(model.duplicate().order(ByteOrder.LITTLE_ENDIAN));

        assertEquals(0, store.profileCount());
        assertEquals(ProfileStore.DEFAULT_MULTIPLIER, store.getMultiplier(7, 0), 1e-6f);
        assertEquals(ProfileStore.DEFAULT_MULTIPLIER, store.interpolate(7, 12.5), 1e-6f);
        assertEquals(ProfileStore.DEFAULT_MULTIPLIER, store.getMultiplierForDay(7, 0, 3), 1e-6f);
        assertEquals(ProfileStore.DEFAULT_MULTIPLIER, store.interpolateForDay(7, 0, 3.5), 1e-6f);
    }

    @Test
    @DisplayName("Rejects malformed profile contracts")
    void testMalformedContractsRejected() {
        ByteBuffer duplicateProfileId = buildModelBuffer(
                new ProfileSpec(4, ALL_DAYS, new float[]{1.0f}, 1.0f),
                new ProfileSpec(4, WEEKDAY_MASK, new float[]{1.1f}, 1.0f)
        );
        assertThrows(IllegalArgumentException.class,
                () -> ProfileStore.fromFlatBuffer(duplicateProfileId.duplicate().order(ByteOrder.LITTLE_ENDIAN)));

        ByteBuffer invalidDayMask = buildModelBuffer(
                new ProfileSpec(4, 0x80, new float[]{1.0f}, 1.0f)
        );
        assertThrows(IllegalArgumentException.class,
                () -> ProfileStore.fromFlatBuffer(invalidDayMask.duplicate().order(ByteOrder.LITTLE_ENDIAN)));

        ByteBuffer zeroDayMask = buildModelBuffer(
                new ProfileSpec(4, 0, new float[]{1.0f}, 1.0f)
        );
        assertThrows(IllegalArgumentException.class,
                () -> ProfileStore.fromFlatBuffer(zeroDayMask.duplicate().order(ByteOrder.LITTLE_ENDIAN)));

        ByteBuffer emptyBuckets = buildModelBuffer(
                new ProfileSpec(4, ALL_DAYS, new float[0], 1.0f)
        );
        assertThrows(IllegalArgumentException.class,
                () -> ProfileStore.fromFlatBuffer(emptyBuckets.duplicate().order(ByteOrder.LITTLE_ENDIAN)));

        ByteBuffer invalidBucket = buildModelBuffer(
                new ProfileSpec(4, ALL_DAYS, new float[]{Float.NaN}, 1.0f)
        );
        assertThrows(IllegalArgumentException.class,
                () -> ProfileStore.fromFlatBuffer(invalidBucket.duplicate().order(ByteOrder.LITTLE_ENDIAN)));

        ByteBuffer invalidMultiplier = buildModelBuffer(
                new ProfileSpec(4, ALL_DAYS, new float[]{1.0f}, Float.NaN)
        );
        assertThrows(IllegalArgumentException.class,
                () -> ProfileStore.fromFlatBuffer(invalidMultiplier.duplicate().order(ByteOrder.LITTLE_ENDIAN)));

        ByteBuffer missingIdentifier = buildModelBufferWithoutIdentifier(
                new ProfileSpec(4, ALL_DAYS, new float[]{1.0f}, 1.0f)
        );
        assertThrows(IllegalArgumentException.class,
                () -> ProfileStore.fromFlatBuffer(missingIdentifier.duplicate().order(ByteOrder.LITTLE_ENDIAN)));
    }

    @Test
    @DisplayName("Input validation for day and bucket accessors")
    void testAccessorValidation() {
        ByteBuffer model = buildModelBuffer(
                new ProfileSpec(1, ALL_DAYS, new float[]{1.0f, 2.0f}, 1.0f)
        );
        ProfileStore store = ProfileStore.fromFlatBuffer(model.duplicate().order(ByteOrder.LITTLE_ENDIAN));

        assertThrows(IllegalArgumentException.class, () -> store.isActiveOnDay(1, -1));
        assertThrows(IllegalArgumentException.class, () -> store.isActiveOnDay(1, 7));
        assertThrows(IllegalArgumentException.class, () -> store.getMultiplier(1, -1));
        assertThrows(IllegalArgumentException.class, () -> store.getMultiplier(1, 2));
        assertThrows(IllegalArgumentException.class, () -> store.getMultiplierForDay(999, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> store.interpolate(1, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> store.interpolate(1, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> store.interpolateForDay(999, 0, Double.NaN));
    }

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    @DisplayName("Concurrent read access remains stable")
    void testConcurrentReads() throws InterruptedException {
        ProfileSpec[] specs = new ProfileSpec[256];
        for (int i = 0; i < specs.length; i++) {
            float[] buckets = new float[96];
            for (int b = 0; b < buckets.length; b++) {
                buckets[b] = 1.0f + (i % 8) * 0.05f + (b % 6) * 0.01f;
            }
            int dayMask = (i & 1) == 0 ? ALL_DAYS : WEEKDAY_MASK;
            specs[i] = new ProfileSpec(i, dayMask, buckets, 1.0f);
        }

        ByteBuffer model = buildModelBuffer(specs);
        ProfileStore store = ProfileStore.fromFlatBuffer(model.duplicate().order(ByteOrder.LITTLE_ENDIAN));

        int threads = 8;
        int loopsPerThread = 50_000;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicBoolean failed = new AtomicBoolean(false);

        for (int t = 0; t < threads; t++) {
            final int seed = 1_000 + t;
            executor.execute(() -> {
                Random random = new Random(seed);
                try {
                    for (int i = 0; i < loopsPerThread; i++) {
                        int profileId = random.nextInt(specs.length);
                        int day = random.nextInt(7);
                        int bucket = random.nextInt(96);
                        double fractionalBucket = bucket + random.nextDouble();

                        float direct = store.getMultiplier(profileId, bucket);
                        float dayAware = store.getMultiplierForDay(profileId, day, bucket);
                        float interpolated = store.interpolate(profileId, fractionalBucket);

                        if (!Float.isFinite(direct) || !Float.isFinite(dayAware) || !Float.isFinite(interpolated)) {
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

        assertTrue(latch.await(20, TimeUnit.SECONDS), "Concurrent read test timed out");
        executor.shutdownNow();
        assertFalse(failed.get(), "Concurrent reads produced invalid results");
    }

    private ByteBuffer buildModelBuffer(ProfileSpec... specs) {
        return buildModelBuffer(true, specs);
    }

    private ByteBuffer buildModelBufferWithoutIdentifier(ProfileSpec... specs) {
        return buildModelBuffer(false, specs);
    }

    private ByteBuffer buildModelBuffer(boolean includeIdentifier, ProfileSpec... specs) {
        FlatBufferBuilder builder = new FlatBufferBuilder(4096);

        int profilesVec = 0;
        if (specs != null && specs.length > 0) {
            int[] profileOffsets = new int[specs.length];
            for (int i = 0; i < specs.length; i++) {
                ProfileSpec spec = specs[i];
                int bucketsOffset = TemporalProfile.createBucketsVector(builder, spec.buckets());
                profileOffsets[i] = TemporalProfile.createTemporalProfile(
                        builder,
                        spec.profileId(),
                        spec.dayMask(),
                        bucketsOffset,
                        spec.multiplier()
                );
            }
            profilesVec = Model.createProfilesVector(builder, profileOffsets);
        }

        int metadataRef = createMetadata(builder);

        Model.startModel(builder);
        Model.addMetadata(builder, metadataRef);
        if (profilesVec != 0) {
            Model.addProfiles(builder, profilesVec);
        }
        int root = Model.endModel(builder);
        if (includeIdentifier) {
            Model.finishModelBuffer(builder, root);
        } else {
            builder.finish(root);
        }
        return ByteBuffer.wrap(builder.sizedByteArray()).order(ByteOrder.LITTLE_ENDIAN);
    }

    private int createMetadata(FlatBufferBuilder builder) {
        int modelVersion = builder.createString("stage9-test");

        Metadata.startMetadata(builder);
        Metadata.addSchemaVersion(builder, 1);
        Metadata.addModelVersion(builder, modelVersion);
        Metadata.addTimeUnit(builder, org.Aayush.serialization.flatbuffers.taro.model.TimeUnit.SECONDS);
        Metadata.addTickDurationNs(builder, 1_000_000_000L);
        return Metadata.endMetadata(builder);
    }
}
