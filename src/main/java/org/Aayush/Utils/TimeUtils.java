package org.Aayush.Utils;

public final class TimeUtils {
    
    // Constants
    private static final long SECONDS_PER_DAY = 86400L;      // 24 * 60 * 60
    private static final long SECONDS_PER_HOUR = 3600L;
    private static final int DAYS_PER_WEEK = 7;
    
    // Unix epoch started on Thursday, Jan 1, 1970
    // To make Monday=0, we offset by 3 days
    private static final int EPOCH_DAY_OFFSET = 3;
    
    private TimeUtils() {
        throw new AssertionError("Utility class - do not instantiate");
    }
    
    /**
     * Converts Unix epoch seconds to bucket index for time-dependent profiles.
     * * Example: 
     * epochSec = 1706171400 (8:30 AM UTC on some day)
     * bucketSize = 900 (15 minutes)
     * → secondsSinceMidnight = 30600 (8.5 hours)
     * → bucket = 30600 / 900 = 34
     * * @param epochSec Unix timestamp in seconds (UTC)
     * @param bucketSizeSeconds Duration of each bucket (e.g., 900 for 15min)
     * @return Bucket index [0, bucketsPerDay-1]
     */
    public static int toBucket(long epochSec, int bucketSizeSeconds) {
        if (bucketSizeSeconds <= 0) {
            throw new IllegalArgumentException("Bucket size must be positive");
        }
        
        // Handle negative timestamps (pre-1970)
        long secondsSinceMidnight = epochSec % SECONDS_PER_DAY;
        if (secondsSinceMidnight < 0) {
            secondsSinceMidnight += SECONDS_PER_DAY;
        }
        
        return (int) (secondsSinceMidnight / bucketSizeSeconds);
    }
    
    /**
     * Extracts day of week from Unix epoch.
     * * @param epochSec Unix timestamp in seconds (UTC)
     * @return Day of week: 0=Monday, 1=Tuesday, ..., 6=Sunday
     */
    public static int getDayOfWeek(long epochSec) {
        // Convert seconds to days
        long daysSinceEpoch = epochSec / SECONDS_PER_DAY;
        
        // Handle negative timestamps (before 1970)
        // Integer division truncates towards zero, but we need floor for negative days
        if (epochSec < 0 && epochSec % SECONDS_PER_DAY != 0) {
            daysSinceEpoch--;
        }
        
        // Epoch was Thursday, adjust to Monday=0 convention
        long dayOfWeek = (daysSinceEpoch + EPOCH_DAY_OFFSET) % DAYS_PER_WEEK;
        
        // Handle negative modulo result
        if (dayOfWeek < 0) {
            dayOfWeek += DAYS_PER_WEEK;
        }
        
        return (int) dayOfWeek;
    }
    
    /**
     * Returns seconds elapsed since midnight (UTC).
     * * @param epochSec Unix timestamp in seconds (UTC)
     * @return Seconds since midnight [0, 86399]
     */
    public static long getTimeOfDay(long epochSec) {
        long timeOfDay = epochSec % SECONDS_PER_DAY;
        
        // Handle negative timestamps
        if (timeOfDay < 0) {
            timeOfDay += SECONDS_PER_DAY;
        }
        
        return timeOfDay;
    }
    
    /**
     * Validates FIFO property: arrival times must be monotonically non-decreasing.
     * * Critical for time-dependent routing correctness.
     * If this returns false, the cost function violates causality.
     * * @param arrivalTimes Array of arrival timestamps
     * @return true if FIFO property holds, false if violation detected
     */
    public static boolean validateFIFO(long[] arrivalTimes) {
        if (arrivalTimes == null || arrivalTimes.length < 2) {
            return true;  // Trivially valid
        }
        
        for (int i = 1; i < arrivalTimes.length; i++) {
            if (arrivalTimes[i] < arrivalTimes[i - 1]) {
                return false;  // Violation: later departure arrives earlier
            }
        }
        
        return true;
    }
    
    /**
     * Adds seconds to a Unix timestamp.
     * * @param epochSec Base timestamp
     * @param deltaSec Seconds to add (can be negative)
     * @return New timestamp
     */
    public static long addSeconds(long epochSec, long deltaSec) {
        return epochSec + deltaSec;
    }
    
    /**
     * Utility to format bucket index back to human-readable time.
     * Only for debugging/logging - NOT used in hot paths.
     * * @param bucketIndex Bucket index
     * @param bucketSizeSeconds Size of each bucket
     * @return String like "08:00-08:15"
     */
    public static String bucketToTimeRange(int bucketIndex, int bucketSizeSeconds) {
        long startSeconds = (long) bucketIndex * bucketSizeSeconds;
        long endSeconds = startSeconds + bucketSizeSeconds;
        
        // Clamp endSeconds to 24:00 (86400) for display purposes if it wraps
        if (endSeconds > SECONDS_PER_DAY) endSeconds = SECONDS_PER_DAY;

        return formatTimeOfDay(startSeconds) + "-" + formatTimeOfDay(endSeconds);
    }
    
    private static String formatTimeOfDay(long seconds) {
        long hours = seconds / SECONDS_PER_HOUR;
        long minutes = (seconds % SECONDS_PER_HOUR) / 60;
        // Handle "24:00" case for end range
        if (hours == 24 && minutes == 0) return "24:00";
        return String.format("%02d:%02d", hours, minutes);
    }
}