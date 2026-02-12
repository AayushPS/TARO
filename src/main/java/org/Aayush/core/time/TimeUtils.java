package org.Aayush.core.time;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Shared deterministic time helpers for routing/profile runtime logic.
 *
 * <p>All methods operate in UTC and are safe for negative timestamps.</p>
 */
public final class TimeUtils {

    /**
     * Canonical tick units supported by TARO runtime metadata and execution.
     */
    @Getter
    @Accessors(fluent = true)
    @RequiredArgsConstructor
    public enum EngineTimeUnit {
        SECONDS(1_000_000_000L, 1L),
        MILLISECONDS(1_000_000L, 1_000L);

        /** Tick duration in nanoseconds written in model metadata. */
        private final long tickDurationNs;
        /** Number of ticks that represent one second in this unit. */
        private final long ticksPerSecond;
    }

    private static final long SECONDS_PER_DAY = 86_400L;
    private static final long SECONDS_PER_HOUR = 3600L;
    private static final int DAYS_PER_WEEK = 7;
    // Unix epoch started on Thursday (1970-01-01). Offset by +3 to keep Monday = 0.
    private static final int EPOCH_DAY_OFFSET = 3;

    /**
     * Prevents instantiation of this utility class.
     */
    private TimeUtils() {
        throw new AssertionError("Utility class - do not instantiate");
    }

    /**
     * Converts Unix epoch seconds to bucket index for time-dependent profiles.
     *
     * @param epochSec Unix timestamp in seconds (UTC).
     * @param bucketSizeSeconds duration of each bucket (for example, 900 for 15 minutes).
     * @return bucket index in range {@code [0, bucketsPerDay - 1]}.
     */
    public static int toBucket(long epochSec, int bucketSizeSeconds) {
        if (bucketSizeSeconds <= 0) {
            throw new IllegalArgumentException("Bucket size must be positive");
        }

        // Handle negative timestamps (pre-1970).
        long secondsSinceMidnight = epochSec % SECONDS_PER_DAY;
        if (secondsSinceMidnight < 0) {
            secondsSinceMidnight += SECONDS_PER_DAY;
        }

        return (int) (secondsSinceMidnight / bucketSizeSeconds);
    }

    /**
     * Converts timestamps from model ticks to UTC bucket index.
     *
     * @param timestamp timestamp in model ticks.
     * @param bucketSizeSeconds bucket size in seconds.
     * @param unit unit used by {@code timestamp}.
     * @return bucket index in range {@code [0, bucketsPerDay - 1]}.
     */
    public static int toBucket(long timestamp, int bucketSizeSeconds, EngineTimeUnit unit) {
        return toBucket(toEpochSeconds(timestamp, unit), bucketSizeSeconds);
    }

    /**
     * Extracts day of week from Unix epoch.
     *
     * @param epochSec Unix timestamp in seconds (UTC).
     * @return day-of-week where Monday = 0 and Sunday = 6.
     */
    public static int getDayOfWeek(long epochSec) {
        // Convert seconds to days.
        long daysSinceEpoch = epochSec / SECONDS_PER_DAY;

        // Integer division truncates toward zero; correct to floor for negatives.
        if (epochSec < 0 && epochSec % SECONDS_PER_DAY != 0) {
            daysSinceEpoch--;
        }

        // Epoch was Thursday; adjust to Monday=0 convention.
        long dayOfWeek = (daysSinceEpoch + EPOCH_DAY_OFFSET) % DAYS_PER_WEEK;

        // Handle negative modulo result.
        if (dayOfWeek < 0) {
            dayOfWeek += DAYS_PER_WEEK;
        }

        return (int) dayOfWeek;
    }

    /**
     * Extracts day-of-week from model ticks.
     *
     * @param timestamp timestamp in model ticks.
     * @param unit unit used by {@code timestamp}.
     * @return day-of-week where Monday = 0 and Sunday = 6.
     */
    public static int getDayOfWeek(long timestamp, EngineTimeUnit unit) {
        return getDayOfWeek(toEpochSeconds(timestamp, unit));
    }

    /**
     * Returns seconds elapsed since midnight (UTC).
     *
     * @param epochSec Unix timestamp in seconds (UTC).
     * @return seconds since midnight in range {@code [0, 86399]}.
     */
    public static long getTimeOfDay(long epochSec) {
        long timeOfDay = epochSec % SECONDS_PER_DAY;

        // Handle negative timestamps.
        if (timeOfDay < 0) {
            timeOfDay += SECONDS_PER_DAY;
        }

        return timeOfDay;
    }

    /**
     * Returns elapsed ticks since midnight in the model's time unit.
     *
     * @param timestamp timestamp in model ticks.
     * @param unit unit used by {@code timestamp}.
     * @return elapsed ticks since UTC midnight in {@code unit}.
     */
    public static long getTimeOfDayTicks(long timestamp, EngineTimeUnit unit) {
        long seconds = getTimeOfDay(toEpochSeconds(timestamp, unit));
        return Math.multiplyExact(seconds, unit.ticksPerSecond());
    }

    /**
     * Validates FIFO property: arrival times must be monotonically non-decreasing.
     *
     * @param arrivalTimes arrival timeline to validate.
     * @return {@code true} when FIFO ordering is preserved; otherwise {@code false}.
     */
    public static boolean validateFIFO(long[] arrivalTimes) {
        if (arrivalTimes == null || arrivalTimes.length < 2) {
            return true;
        }

        for (int i = 1; i < arrivalTimes.length; i++) {
            if (arrivalTimes[i] < arrivalTimes[i - 1]) {
                return false;
            }
        }

        return true;
    }

    /**
     * Adds seconds to a Unix timestamp.
     *
     * @param epochSec base timestamp.
     * @param deltaSec seconds delta (can be negative).
     * @return shifted timestamp.
     */
    public static long addSeconds(long epochSec, long deltaSec) {
        return epochSec + deltaSec;
    }

    /**
     * Unit-aware timestamp normalization. Use this to align incoming request time
     * with model time during ingestion/runtime boundaries.
     *
     * @param timestamp input timestamp value.
     * @param inputUnit input timestamp unit.
     * @param engineUnit target runtime unit.
     * @return timestamp normalized into {@code engineUnit}.
     */
    public static long normalizeToEngineTicks(long timestamp, EngineTimeUnit inputUnit, EngineTimeUnit engineUnit) {
        if (inputUnit == null || engineUnit == null) {
            throw new IllegalArgumentException("Time units cannot be null");
        }
        if (inputUnit == engineUnit) {
            return timestamp;
        }
        if (inputUnit == EngineTimeUnit.SECONDS && engineUnit == EngineTimeUnit.MILLISECONDS) {
            return Math.multiplyExact(timestamp, 1_000L);
        }
        // Millis -> Seconds: use floor division for negative timestamp correctness.
        return Math.floorDiv(timestamp, 1_000L);
    }

    /**
     * Maps metadata tick duration to a known engine unit.
     *
     * @param tickDurationNs metadata tick duration.
     * @return corresponding runtime engine unit.
     */
    public static EngineTimeUnit fromTickDurationNs(long tickDurationNs) {
        if (tickDurationNs == EngineTimeUnit.SECONDS.tickDurationNs()) {
            return EngineTimeUnit.SECONDS;
        }
        if (tickDurationNs == EngineTimeUnit.MILLISECONDS.tickDurationNs()) {
            return EngineTimeUnit.MILLISECONDS;
        }
        throw new IllegalArgumentException("Unsupported tick_duration_ns: " + tickDurationNs);
    }

    /**
     * Validates metadata unit/tick pairing and returns the canonical tick duration.
     *
     * @param unit metadata time unit.
     * @param tickDurationNs metadata tick duration.
     * @return the validated tick duration.
     */
    public static long validateTickDurationNs(EngineTimeUnit unit, long tickDurationNs) {
        if (unit == null) {
            throw new IllegalArgumentException("Engine unit cannot be null");
        }
        if (tickDurationNs <= 0) {
            throw new IllegalArgumentException("tick_duration_ns must be positive");
        }
        if (unit.tickDurationNs() != tickDurationNs) {
            throw new IllegalArgumentException(
                    "tick_duration_ns mismatch for " + unit + ": expected " + unit.tickDurationNs() + ", got " + tickDurationNs);
        }
        return tickDurationNs;
    }

    /**
     * Formats a bucket index into a human-readable UTC time range.
     * Intended for debugging and logging, not hot-path usage.
     *
     * @param bucketIndex bucket index.
     * @param bucketSizeSeconds bucket width in seconds.
     * @return formatted range like {@code 08:00-08:15}.
     */
    public static String bucketToTimeRange(int bucketIndex, int bucketSizeSeconds) {
        long startSeconds = (long) bucketIndex * bucketSizeSeconds;
        long endSeconds = startSeconds + bucketSizeSeconds;

        // Clamp endSeconds to 24:00 for display when the range crosses midnight.
        if (endSeconds > SECONDS_PER_DAY) {
            endSeconds = SECONDS_PER_DAY;
        }

        return formatTimeOfDay(startSeconds) + "-" + formatTimeOfDay(endSeconds);
    }

    /**
     * Formats seconds-since-midnight as {@code HH:mm} (supports {@code 24:00} sentinel).
     */
    private static String formatTimeOfDay(long seconds) {
        long hours = seconds / SECONDS_PER_HOUR;
        long minutes = (seconds % SECONDS_PER_HOUR) / 60;
        if (hours == 24 && minutes == 0) {
            return "24:00";
        }
        return String.format("%02d:%02d", hours, minutes);
    }

    /**
     * Converts timestamp in model unit into epoch seconds (floor for milliseconds).
     */
    private static long toEpochSeconds(long timestamp, EngineTimeUnit unit) {
        if (unit == null) {
            throw new IllegalArgumentException("Engine unit cannot be null");
        }
        if (unit == EngineTimeUnit.SECONDS) {
            return timestamp;
        }
        return Math.floorDiv(timestamp, 1_000L);
    }
}
