package org.Aayush.routing.profile;

import org.Aayush.serialization.flatbuffers.ModelContractValidator;
import org.Aayush.serialization.flatbuffers.taro.model.Model;
import org.Aayush.serialization.flatbuffers.taro.model.TemporalProfile;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Immutable profile lookup store for temporal multipliers.
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
     * Relative weekly-range threshold below which recurring signal is treated as too weak to trust.
     */
    private static final float WEAK_RECURRING_RANGE_THRESHOLD = 0.05f;
    /**
     * Relative weekly-range threshold that marks a recurring signal as strong enough for release gating.
     */
    private static final float STRONG_RECURRING_RANGE_THRESHOLD = 0.20f;
    /**
     * Distance from the neutral multiplier used to identify an always-on persistent baseline.
     */
    private static final float PERSISTENT_BASELINE_DISTANCE_FROM_NEUTRAL = 0.10f;
    /**
     * Reusable metadata object for fallback path (missing profile id).
     */
    private static final ProfileMetadata DEFAULT_METADATA =
            new ProfileMetadata(DEFAULT_MULTIPLIER, DEFAULT_MULTIPLIER, DEFAULT_MULTIPLIER);
    /**
     * Reusable recurring-pattern metadata for missing-profile fallback.
     */
    private static final TemporalPatternMetadata DEFAULT_TEMPORAL_PATTERN_METADATA =
            new TemporalPatternMetadata(
                    0,
                    DEFAULT_MULTIPLIER,
                    DEFAULT_MULTIPLIER,
                    DEFAULT_MULTIPLIER,
                    0.0f,
                    TemporalPatternClass.MISSING,
                    0,
                    0.0f,
                    RecurringCalibrationPosture.NO_RECURRING_SIGNAL,
                    ProfileRecurrenceCalibrationStore.SignalFlavor.NONE
            );

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
     * Recurring-pattern diagnostics by profile id.
     */
    private final TemporalPatternMetadata[] temporalPatternMetadataByProfileId;
    /**
     * Number of loaded profiles in this store (not max profile id).
     */
    private final int profileCount;

    private ProfileStore(
            float[][] bucketMultipliersByProfileId,
            int[] dayMaskByProfileId,
            ProfileMetadata[] metadataByProfileId,
            boolean[] presentProfileId,
            TemporalPatternMetadata[] temporalPatternMetadataByProfileId,
            int profileCount
    ) {
        this.bucketMultipliersByProfileId = bucketMultipliersByProfileId;
        this.dayMaskByProfileId = dayMaskByProfileId;
        this.metadataByProfileId = metadataByProfileId;
        this.presentProfileId = presentProfileId;
        this.temporalPatternMetadataByProfileId = temporalPatternMetadataByProfileId;
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
     * @return true when the profile is active on all seven days.
     */
    public boolean isAllDaysActive(int profileId) {
        return isKnownProfile(profileId) && dayMaskByProfileId[profileId] == ALL_DAYS_MASK;
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
        return isActiveOnDayUnchecked(profileId, dayOfWeek);
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
        validateDayOfWeek(dayOfWeek);
        return getKnownProfileMultiplierForDay(profileId, dayOfWeek, bucketIdx);
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
        validateDayOfWeek(dayOfWeek);
        if (!isKnownProfile(profileId)) {
            return DEFAULT_MULTIPLIER;
        }

        float[] buckets = bucketMultipliersByProfileId[profileId];
        double wrapped = fractionalBucket % buckets.length;
        if (wrapped < 0.0d) {
            wrapped += buckets.length;
        }

        int lower = (int) wrapped;
        double fraction = wrapped - lower;
        float lowerValue = getKnownProfileMultiplierForDay(profileId, dayOfWeek, lower);
        if (fraction == 0.0d) {
            return lowerValue;
        }

        int upperBucket = lower + 1;
        int upperDay = dayOfWeek;
        if (upperBucket == buckets.length) {
            upperBucket = 0;
            upperDay = nextDayOfWeek(dayOfWeek);
        }

        float upperValue = getKnownProfileMultiplierForDay(profileId, upperDay, upperBucket);
        return (float) (lowerValue + (upperValue - lowerValue) * fraction);
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
     * Returns recurring-pattern diagnostics for one profile.
     *
     * <p>This is a release-gate artifact used by the B3 temporal competency tests.
     * It deliberately exposes when periodic signal is too weak to trust instead of
     * flattening that shape into the aggregate avg/min/max metadata.</p>
     *
     * @param profileId internal profile id.
     * @return recurring-pattern diagnostics, or a neutral missing-profile descriptor.
     */
    public TemporalPatternMetadata getTemporalPatternMetadata(int profileId) {
        if (!isKnownProfile(profileId)) {
            return DEFAULT_TEMPORAL_PATTERN_METADATA;
        }
        return temporalPatternMetadataByProfileId[profileId];
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
        TemporalPatternMetadata[] temporalPatternMetadataByProfileId = new TemporalPatternMetadata[maxProfileId + 1];

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
            temporalPatternMetadataByProfileId[profileId] =
                    buildTemporalPatternMetadata(dayMask, buckets, metadataByProfileId[profileId]);
            presentProfileId[profileId] = true;
            loadedProfiles++;
        }

        return new ProfileStore(
                bucketMultipliersByProfileId,
                dayMaskByProfileId,
                metadataByProfileId,
                presentProfileId,
                temporalPatternMetadataByProfileId,
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
     * Classification for recurring temporal signal strength.
     */
    public enum TemporalPatternClass {
        FULLY_PERSISTENT,
        STRICT_PERIODIC,
        MIXED_PERSISTENT_AND_PERIODIC,
        WEAK_SIGNAL_PERIODIC,
        MISSING
    }

    /**
     * Explicit posture for how much recurring signal the artifact believes it has retained.
     */
    public enum RecurringCalibrationPosture {
        NO_RECURRING_SIGNAL,
        WEAK_SIGNAL_REJECTED,
        LOW_CONFIDENCE,
        MEDIUM_CONFIDENCE,
        HIGH_CONFIDENCE
    }

    /**
     * B3 recurring-pattern diagnostics published at artifact load time.
     *
     * @param activeDayCount number of active days in the profile mask.
     * @param effectiveWeeklyMeanMultiplier mean multiplier after inactive-day neutral fallback.
     * @param effectiveWeeklyMinMultiplier minimum multiplier after inactive-day neutral fallback.
     * @param effectiveWeeklyMaxMultiplier maximum multiplier after inactive-day neutral fallback.
     * @param effectiveWeeklyRelativeRange normalized weekly spread used for weak/strong signal gating.
     * @param temporalPatternClass recurring-pattern classification.
     * @param recurringSupportWindowCount count of recurring weekly windows materially supporting the pattern.
     * @param recurringConfidence derived recurring confidence in {@code [0,1]} for fallback calibration.
     * @param recurringCalibrationPosture explicit confidence posture for serving-time calibration.
     * @param recurringSignalFlavor derived recurring signal flavor used when no source override exists.
     */
    public record TemporalPatternMetadata(
            int activeDayCount,
            float effectiveWeeklyMeanMultiplier,
            float effectiveWeeklyMinMultiplier,
            float effectiveWeeklyMaxMultiplier,
            float effectiveWeeklyRelativeRange,
            TemporalPatternClass temporalPatternClass,
            int recurringSupportWindowCount,
            float recurringConfidence,
            RecurringCalibrationPosture recurringCalibrationPosture,
            ProfileRecurrenceCalibrationStore.SignalFlavor recurringSignalFlavor
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
                new TemporalPatternMetadata[0],
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
     * Fast day-mask check for already-validated inputs.
     */
    private boolean isActiveOnDayUnchecked(int profileId, int dayOfWeek) {
        return (dayMaskByProfileId[profileId] & dayBit(dayOfWeek)) != 0;
    }

    /**
     * Day-aware bucket lookup for a known or missing profile.
     */
    private float getKnownProfileMultiplierForDay(int profileId, int dayOfWeek, int bucketIdx) {
        if (!isKnownProfile(profileId)) {
            return DEFAULT_MULTIPLIER;
        }

        float[] buckets = bucketMultipliersByProfileId[profileId];
        if (bucketIdx >= buckets.length) {
            throw new IllegalArgumentException(
                    "bucketIdx out of bounds for profile " + profileId + ": " + bucketIdx +
                            " [0, " + buckets.length + ")"
            );
        }
        if (!isActiveOnDayUnchecked(profileId, dayOfWeek)) {
            return DEFAULT_MULTIPLIER;
        }
        return buckets[bucketIdx];
    }

    /**
     * Converts day index to bit position (Mon=bit0 ... Sun=bit6).
     */
    private static int dayBit(int dayOfWeek) {
        return 1 << dayOfWeek;
    }

    /**
     * Advances day index in the canonical Mon..Sun domain.
     */
    private static int nextDayOfWeek(int dayOfWeek) {
        return (dayOfWeek + 1) % DAYS_PER_WEEK;
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

    /**
     * Builds recurring-pattern diagnostics from loaded profile stats.
     */
    private static TemporalPatternMetadata buildTemporalPatternMetadata(int dayMask, float[] buckets, ProfileMetadata metadata) {
        int activeDayCount = Integer.bitCount(dayMask & ALL_DAYS_MASK);
        float weeklyMean = (
                (metadata.avgMultiplier() * activeDayCount)
                        + (DEFAULT_MULTIPLIER * (DAYS_PER_WEEK - activeDayCount))
        ) / (float) DAYS_PER_WEEK;
        float weeklyMin = activeDayCount == DAYS_PER_WEEK
                ? metadata.minMultiplier()
                : Math.min(metadata.minMultiplier(), DEFAULT_MULTIPLIER);
        float weeklyMax = activeDayCount == DAYS_PER_WEEK
                ? metadata.maxMultiplier()
                : Math.max(metadata.maxMultiplier(), DEFAULT_MULTIPLIER);
        float weeklyRelativeRange = (weeklyMax - weeklyMin) / Math.max(DEFAULT_MULTIPLIER, weeklyMean);

        boolean persistentBaseline =
                weeklyMin >= DEFAULT_MULTIPLIER + PERSISTENT_BASELINE_DISTANCE_FROM_NEUTRAL
                        || weeklyMax <= DEFAULT_MULTIPLIER - PERSISTENT_BASELINE_DISTANCE_FROM_NEUTRAL;

        TemporalPatternClass patternClass;
        if (weeklyRelativeRange >= STRONG_RECURRING_RANGE_THRESHOLD) {
            patternClass = persistentBaseline
                    ? TemporalPatternClass.MIXED_PERSISTENT_AND_PERIODIC
                    : TemporalPatternClass.STRICT_PERIODIC;
        } else if (weeklyRelativeRange >= WEAK_RECURRING_RANGE_THRESHOLD) {
            patternClass = TemporalPatternClass.WEAK_SIGNAL_PERIODIC;
        } else {
            patternClass = TemporalPatternClass.FULLY_PERSISTENT;
        }

        int recurringSupportWindowCount = recurringSupportWindowCount(
                buckets,
                metadata.avgMultiplier(),
                activeDayCount,
                weeklyRelativeRange
        );
        float recurringConfidence = recurringConfidence(
                weeklyRelativeRange,
                activeDayCount,
                recurringSupportWindowCount,
                patternClass
        );
        RecurringCalibrationPosture recurringCalibrationPosture = recurringCalibrationPosture(patternClass, recurringConfidence);
        ProfileRecurrenceCalibrationStore.SignalFlavor recurringSignalFlavor =
                recurringCalibrationPosture == RecurringCalibrationPosture.NO_RECURRING_SIGNAL
                        || recurringCalibrationPosture == RecurringCalibrationPosture.WEAK_SIGNAL_REJECTED
                        ? ProfileRecurrenceCalibrationStore.SignalFlavor.NONE
                        : ProfileRecurrenceCalibrationStore.SignalFlavor.ROUTINE_PERIODIC;

        return new TemporalPatternMetadata(
                activeDayCount,
                weeklyMean,
                weeklyMin,
                weeklyMax,
                weeklyRelativeRange,
                patternClass,
                recurringSupportWindowCount,
                recurringConfidence,
                recurringCalibrationPosture,
                recurringSignalFlavor
        );
    }

    private static int recurringSupportWindowCount(
            float[] buckets,
            float meanMultiplier,
            int activeDayCount,
            float weeklyRelativeRange
    ) {
        if (weeklyRelativeRange < WEAK_RECURRING_RANGE_THRESHOLD) {
            return 0;
        }
        float deviationThreshold = Math.max(0.02f, meanMultiplier * 0.05f);
        int recurringBucketCount = 0;
        for (float bucket : buckets) {
            if (Math.abs(bucket - meanMultiplier) >= deviationThreshold) {
                recurringBucketCount++;
            }
        }
        if (recurringBucketCount == 0) {
            recurringBucketCount = 1;
        }
        return recurringBucketCount * Math.max(1, activeDayCount);
    }

    private static float recurringConfidence(
            float weeklyRelativeRange,
            int activeDayCount,
            int recurringSupportWindowCount,
            TemporalPatternClass patternClass
    ) {
        if (patternClass == TemporalPatternClass.MISSING
                || patternClass == TemporalPatternClass.FULLY_PERSISTENT) {
            return 0.0f;
        }
        if (patternClass == TemporalPatternClass.WEAK_SIGNAL_PERIODIC) {
            return 0.0f;
        }

        float rangeScore = Math.min(1.0f, weeklyRelativeRange / Math.max(STRONG_RECURRING_RANGE_THRESHOLD, 1.0e-6f));
        float activeDayScore = Math.min(1.0f, activeDayCount / (float) DAYS_PER_WEEK);
        float supportScore = Math.min(1.0f, recurringSupportWindowCount / 14.0f);
        return Math.min(1.0f, (0.45f * rangeScore) + (0.25f * activeDayScore) + (0.30f * supportScore));
    }

    private static RecurringCalibrationPosture recurringCalibrationPosture(
            TemporalPatternClass patternClass,
            float recurringConfidence
    ) {
        if (patternClass == TemporalPatternClass.MISSING
                || patternClass == TemporalPatternClass.FULLY_PERSISTENT) {
            return RecurringCalibrationPosture.NO_RECURRING_SIGNAL;
        }
        if (patternClass == TemporalPatternClass.WEAK_SIGNAL_PERIODIC) {
            return RecurringCalibrationPosture.WEAK_SIGNAL_REJECTED;
        }
        if (recurringConfidence >= 0.80f) {
            return RecurringCalibrationPosture.HIGH_CONFIDENCE;
        }
        if (recurringConfidence >= 0.55f) {
            return RecurringCalibrationPosture.MEDIUM_CONFIDENCE;
        }
        return RecurringCalibrationPosture.LOW_CONFIDENCE;
    }
}
