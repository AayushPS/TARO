package org.Aayush.routing.profile;

import org.Aayush.serialization.flatbuffers.ModelContractValidator;
import org.Aayush.serialization.flatbuffers.taro.model.Model;
import org.Aayush.serialization.flatbuffers.taro.model.TemporalProfile;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Stage 9: Immutable profile lookup store for temporal multipliers.
 * <p>
 * Contract summary:
 * </p>
 * <ul>
 * <li>Day-mask aware selection with deterministic fallback to neutral multiplier {@code 1.0}.</li>
 * <li>O(1) profile lookup by id and bucket lookup by index.</li>
 * <li>Explicit interpolation policy: cyclic linear interpolation with wrap-around.</li>
 * <li>Immutable after construction; safe for concurrent readers.</li>
 * </ul>
 */
public final class ProfileStore {

    /**
     * Sentinel id representing "no active profile selected" after day-mask filtering.
     */
    public static final int DEFAULT_PROFILE_ID = -1;
    /**
     * Neutral multiplier used for deterministic fallback when profile data is absent/inactive.
     */
    public static final float DEFAULT_MULTIPLIER = 1.0f;

    /**
     * TARO FlatBuffer file identifier as little-endian int for header validation.
     * Hex value maps to ASCII "TARO".
     */
    private static final int FILE_IDENTIFIER = 0x4F524154; // "TARO"
    /**
     * Day domain size for day-mask checks (Mon=0 ... Sun=6 in this runtime convention).
     */
    private static final int DAYS_PER_WEEK = 7;
    /**
     * 7-bit mask for all days active: 0b1111111.
     */
    private static final int ALL_DAYS_MASK = 0x7F;
    /**
     * Reusable metadata object for fallback path (missing profile id).
     */
    private static final ProfileMetadata DEFAULT_METADATA =
            new ProfileMetadata(DEFAULT_MULTIPLIER, DEFAULT_MULTIPLIER, DEFAULT_MULTIPLIER);

    /**
     * Dense profile storage indexed by profile id.
     * <p>
     * Entry is null when a profile id is not present in the model.
     * Each row contains already-scaled bucket multipliers.
     * </p>
     */
    private final float[][] bucketMultipliersByProfileId;
    /**
     * Day mask for each profile id (same index space as {@link #bucketMultipliersByProfileId}).
     */
    private final int[] dayMaskByProfileId;
    /**
     * Pre-computed aggregate stats (avg/min/max) by profile id.
     */
    private final ProfileMetadata[] metadataByProfileId;
    /**
     * Presence bitmap to distinguish "missing profile id" from sparse array defaults.
     */
    private final boolean[] presentProfileId;
    /**
     * Number of loaded profiles in this store (not max profile id).
     */
    private final int profileCount;

    private ProfileStore(
            float[][] bucketMultipliersByProfileId,
            int[] dayMaskByProfileId,
            ProfileMetadata[] metadataByProfileId,
            boolean[] presentProfileId,
            int profileCount
    ) {
        this.bucketMultipliersByProfileId = bucketMultipliersByProfileId;
        this.dayMaskByProfileId = dayMaskByProfileId;
        this.metadataByProfileId = metadataByProfileId;
        this.presentProfileId = presentProfileId;
        this.profileCount = profileCount;
    }

    /**
     * @return number of materialized profiles loaded from the model.
     */
    public int profileCount() {
        return profileCount;
    }

    /**
     * @param profileId internal profile id.
     * @return true when the id exists in this store.
     */
    public boolean hasProfile(int profileId) {
        return isKnownProfile(profileId);
    }

    /**
     * @param profileId internal profile id.
     * @return number of buckets for this profile; {@code 0} when profile is missing.
     */
    public int bucketCount(int profileId) {
        if (!isKnownProfile(profileId)) {
            return 0;
        }
        return bucketMultipliersByProfileId[profileId].length;
    }

    /**
     * Selects which profile id to use for a given day.
     *
     * @param profileId candidate profile id.
     * @param dayOfWeek day index in range [0,6].
     * @return requested profile id when active on the given day, else {@link #DEFAULT_PROFILE_ID}.
     */
    public int selectProfileForDay(int profileId, int dayOfWeek) {
        return isActiveOnDay(profileId, dayOfWeek) ? profileId : DEFAULT_PROFILE_ID;
    }

    /**
     * Returns whether the profile's day mask includes the given day.
     *
     * @param profileId internal profile id.
     * @param dayOfWeek day index in range [0,6].
     * @return true when the profile exists and has that day bit enabled.
     */
    public boolean isActiveOnDay(int profileId, int dayOfWeek) {
        validateDayOfWeek(dayOfWeek);
        if (!isKnownProfile(profileId)) {
            return false;
        }
        return (dayMaskByProfileId[profileId] & dayBit(dayOfWeek)) != 0;
    }

    /**
     * Direct bucket lookup by profile id and integer bucket index.
     * Missing profile ids fallback to neutral multiplier {@code 1.0}.
     *
     * @param profileId internal profile id.
     * @param bucketIdx bucket index in range [0, bucketCount).
     * @return bucket multiplier or neutral fallback multiplier when profile is missing.
     */
    public float getMultiplier(int profileId, int bucketIdx) {
        if (bucketIdx < 0) {
            throw new IllegalArgumentException("bucketIdx must be >= 0");
        }
        if (!isKnownProfile(profileId)) {
            return DEFAULT_MULTIPLIER;
        }

        float[] buckets = bucketMultipliersByProfileId[profileId];
        if (bucketIdx >= buckets.length) {
            throw new IllegalArgumentException(
                    "bucketIdx out of bounds for profile " + profileId + ": " + bucketIdx +
                            " [0, " + buckets.length + ")");
        }
        return buckets[bucketIdx];
    }

    /**
     * Day-aware bucket lookup with deterministic fallback semantics.
     *
     * @param profileId internal profile id.
     * @param dayOfWeek day index in range [0,6].
     * @param bucketIdx bucket index in range [0, bucketCount).
     * @return bucket multiplier when day is active, else {@link #DEFAULT_MULTIPLIER}.
     */
    public float getMultiplierForDay(int profileId, int dayOfWeek, int bucketIdx) {
        if (bucketIdx < 0) {
            throw new IllegalArgumentException("bucketIdx must be >= 0");
        }
        int selectedProfile = selectProfileForDay(profileId, dayOfWeek);
        if (selectedProfile == DEFAULT_PROFILE_ID) {
            return DEFAULT_MULTIPLIER;
        }
        return getMultiplier(selectedProfile, bucketIdx);
    }

    /**
     * Cyclic linear interpolation for fractional bucket position.
     * <p>
     * Policy:
     * </p>
     * <ul>
     * <li>Input is wrapped into {@code [0, bucketCount)}.</li>
     * <li>Interpolation is between floor bucket and next bucket.</li>
     * <li>Upper neighbor wraps from last bucket to bucket 0.</li>
     * </ul>
     *
     * @param profileId internal profile id.
     * @param fractionalBucket continuous bucket coordinate (can be negative; wraps cyclically).
     * @return interpolated multiplier or neutral fallback multiplier when profile is missing.
     */
    public float interpolate(int profileId, double fractionalBucket) {
        validateFractionalBucket(fractionalBucket);
        if (!isKnownProfile(profileId)) {
            return DEFAULT_MULTIPLIER;
        }
        return interpolateInternal(bucketMultipliersByProfileId[profileId], fractionalBucket);
    }

    /**
     * Day-aware interpolation with deterministic fallback semantics.
     *
     * @param profileId internal profile id.
     * @param dayOfWeek day index in range [0,6].
     * @param fractionalBucket continuous bucket coordinate.
     * @return interpolated multiplier when day is active, else {@link #DEFAULT_MULTIPLIER}.
     */
    public float interpolateForDay(int profileId, int dayOfWeek, double fractionalBucket) {
        validateFractionalBucket(fractionalBucket);
        int selectedProfile = selectProfileForDay(profileId, dayOfWeek);
        if (selectedProfile == DEFAULT_PROFILE_ID) {
            return DEFAULT_MULTIPLIER;
        }
        return interpolate(selectedProfile, fractionalBucket);
    }

    /**
     * Metadata lookup for aggregate profile multipliers.
     * Missing profile ids return neutral metadata (avg=min=max=1.0).
     *
     * @param profileId internal profile id.
     * @return immutable metadata for profile or neutral fallback metadata.
     */
    public ProfileMetadata getMetadata(int profileId) {
        if (!isKnownProfile(profileId)) {
            return DEFAULT_METADATA;
        }
        return metadataByProfileId[profileId];
    }

    /**
     * Constructs a profile store from TARO model FlatBuffer.
     * <p>
     * Loader approach:
     * </p>
     * <ol>
     * <li>Validate file identifier and parse root model.</li>
     * <li>Scan once for max profile id to allocate dense arrays.</li>
     * <li>Scan profiles again to validate and materialize per-profile data.</li>
     * <li>Precompute metadata statistics (avg/min/max) for O(1) retrieval.</li>
     * </ol>
     *
     * @param buffer TARO model buffer (root table with "TARO" file identifier).
     * @return immutable profile store.
     */
    public static ProfileStore fromFlatBuffer(ByteBuffer buffer) {
        if (buffer == null) {
            throw new IllegalArgumentException("Buffer cannot be null");
        }

        ByteBuffer bb = buffer.slice().order(ByteOrder.LITTLE_ENDIAN);
        if (bb.remaining() < 8) {
            throw new IllegalArgumentException("Buffer too small for .taro file header");
        }
        if (!Model.ModelBufferHasIdentifier(bb)) {
            int ident = bb.getInt(4);
            throw new IllegalArgumentException(String.format(
                    "Invalid file identifier. Expected 'TARO' (0x%08X), got 0x%08X",
                    FILE_IDENTIFIER, ident));
        }

        Model model = Model.getRootAsModel(bb);
        ModelContractValidator.validateMetadataContract(model, "ProfileStore");
        int vectorLen = model.profilesLength();
        if (vectorLen == 0) {
            return empty();
        }

        TemporalProfile cursor = new TemporalProfile();
        int maxProfileId = -1;
        for (int i = 0; i < vectorLen; i++) {
            TemporalProfile profile = model.profiles(cursor, i);
            if (profile == null) {
                throw new IllegalArgumentException("profiles[" + i + "] is null");
            }
            maxProfileId = Math.max(maxProfileId, profile.profileId());
        }

        if (maxProfileId < 0) {
            return empty();
        }

        float[][] bucketMultipliersByProfileId = new float[maxProfileId + 1][];
        int[] dayMaskByProfileId = new int[maxProfileId + 1];
        ProfileMetadata[] metadataByProfileId = new ProfileMetadata[maxProfileId + 1];
        boolean[] presentProfileId = new boolean[maxProfileId + 1];

        int loadedProfiles = 0;

        for (int i = 0; i < vectorLen; i++) {
            TemporalProfile profile = model.profiles(cursor, i);
            if (profile == null) {
                throw new IllegalArgumentException("profiles[" + i + "] is null");
            }

            int profileId = profile.profileId();
            if (presentProfileId[profileId]) {
                throw new IllegalArgumentException("Duplicate profile_id found: " + profileId);
            }

            int dayMask = profile.dayMask();
            validateDayMask(dayMask, profileId);

            int bucketCount = profile.bucketsLength();
            if (bucketCount <= 0) {
                throw new IllegalArgumentException(
                        "profile " + profileId + " must have non-empty buckets");
            }

            float scale = profile.multiplier();
            validatePositiveFinite(scale, "profile " + profileId + ".multiplier");

            float[] buckets = new float[bucketCount];
            double sum = 0.0;
            float min = Float.POSITIVE_INFINITY;
            float max = Float.NEGATIVE_INFINITY;

            for (int b = 0; b < bucketCount; b++) {
                float rawBucket = profile.buckets(b);
                validatePositiveFinite(rawBucket, "profile " + profileId + ".buckets[" + b + "]");

                float scaledBucket = rawBucket * scale;
                if (!Float.isFinite(scaledBucket) || scaledBucket <= 0.0f) {
                    throw new IllegalArgumentException(
                            "profile " + profileId + ".buckets[" + b + "] * multiplier must be positive and finite");
                }

                buckets[b] = scaledBucket;
                sum += scaledBucket;
                min = Math.min(min, scaledBucket);
                max = Math.max(max, scaledBucket);
            }

            bucketMultipliersByProfileId[profileId] = buckets;
            dayMaskByProfileId[profileId] = dayMask;
            metadataByProfileId[profileId] =
                    new ProfileMetadata((float) (sum / bucketCount), min, max);
            presentProfileId[profileId] = true;
            loadedProfiles++;
        }

        return new ProfileStore(
                bucketMultipliersByProfileId,
                dayMaskByProfileId,
                metadataByProfileId,
                presentProfileId,
                loadedProfiles
        );
    }

    /**
     * Aggregate profile statistics precomputed at load time.
     *
     * @param avgMultiplier arithmetic mean of all scaled buckets.
     * @param minMultiplier minimum scaled bucket value.
     * @param maxMultiplier maximum scaled bucket value.
     */
    public record ProfileMetadata(
            float avgMultiplier,
            float minMultiplier,
            float maxMultiplier
    ) { }

    /**
     * Creates an empty store with deterministic fallback behavior.
     */
    private static ProfileStore empty() {
        return new ProfileStore(
                new float[0][],
                new int[0],
                new ProfileMetadata[0],
                new boolean[0],
                0
        );
    }

    /**
     * Fast profile-id existence check for all lookup paths.
     */
    private boolean isKnownProfile(int profileId) {
        return profileId >= 0
                && profileId < presentProfileId.length
                && presentProfileId[profileId];
    }

    /**
     * Validates that day mask is a non-zero 7-bit set.
     */
    private static void validateDayMask(int dayMask, int profileId) {
        if (dayMask <= 0 || dayMask > ALL_DAYS_MASK) {
            throw new IllegalArgumentException(
                    "profile " + profileId + ".day_mask must be a non-zero 7-bit mask [1,127], got " + dayMask);
        }
    }

    /**
     * Validates runtime day index.
     */
    private static void validateDayOfWeek(int dayOfWeek) {
        if (dayOfWeek < 0 || dayOfWeek >= DAYS_PER_WEEK) {
            throw new IllegalArgumentException("dayOfWeek must be in [0,6], got " + dayOfWeek);
        }
    }

    /**
     * Converts day index to bit position (Mon=bit0 ... Sun=bit6).
     */
    private static int dayBit(int dayOfWeek) {
        return 1 << dayOfWeek;
    }

    /**
     * Validates interpolation coordinate.
     */
    private static void validateFractionalBucket(double fractionalBucket) {
        if (!Double.isFinite(fractionalBucket)) {
            throw new IllegalArgumentException("fractionalBucket must be finite");
        }
    }

    /**
     * Shared guard for scale/bucket values loaded from model.
     */
    private static void validatePositiveFinite(float value, String fieldName) {
        if (!Float.isFinite(value) || value <= 0.0f) {
            throw new IllegalArgumentException(fieldName + " must be positive and finite");
        }
    }

    /**
     * Internal interpolation helper for known profile buckets.
     * Input is already validated by caller.
     */
    private static float interpolateInternal(float[] buckets, double fractionalBucket) {
        if (buckets.length == 1) {
            return buckets[0];
        }

        double wrapped = fractionalBucket % buckets.length;
        if (wrapped < 0.0d) {
            wrapped += buckets.length;
        }

        int lower = (int) wrapped;
        double fraction = wrapped - lower;
        if (fraction == 0.0d) {
            return buckets[lower];
        }

        int upper = lower + 1;
        if (upper == buckets.length) {
            upper = 0;
        }

        float lowerValue = buckets[lower];
        float upperValue = buckets[upper];
        return (float) (lowerValue + (upperValue - lowerValue) * fraction);
    }
}
