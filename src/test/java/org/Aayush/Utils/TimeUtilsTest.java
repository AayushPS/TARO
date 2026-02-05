package org.Aayush.Utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class TimeUtilsTest {

    // ========== Bucket Calculation Tests ==========

    @ParameterizedTest
    @CsvSource({
            "1706169600, 900, 32",   // 8:00 AM UTC -> bucket 32
            "1706140800, 900, 0",    // Midnight UTC -> bucket 0
            "1706227199, 900, 95",   // 23:59:59 (last second) -> bucket 95
            "-86400, 900, 0",        // Dec 31 1969 (Midnight) -> bucket 0
            "-43200, 900, 48"        // Noon previous day -> bucket 48
    })
    void testBucketCalculation_General(long epochSec, int bucketSize, int expectedBucket) {
        assertEquals(expectedBucket, TimeUtils.toBucket(epochSec, bucketSize));
    }

    @Test
    void testBucketCalculation_Standard15MinBuckets() {
        // Explicit test for documentation: 8:00 AM
        long epochAt8AM = 1706169600L;
        assertEquals(32, TimeUtils.toBucket(epochAt8AM, 900));
    }

    @Test
    void testBucketCalculation_LastBucketOfDay() {
        // Explicit test for documentation: 23:59:59
        long lastSecond = 1706140800L + 86399L;
        assertEquals(95, TimeUtils.toBucket(lastSecond, 900));
    }

    @Test
    void testBucketCalculation_NegativeEpoch() {
        // Explicit test for pre-1970 handling
        long before1970 = -86400L;
        assertEquals(0, TimeUtils.toBucket(before1970, 900));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -900})
    void testBucketCalculation_InvalidBucketSize(int invalidSize) {
        assertThrows(IllegalArgumentException.class,
                () -> TimeUtils.toBucket(1706169600L, invalidSize));
    }

    // ========== Day of Week Tests ==========

    @ParameterizedTest
    @CsvSource({
            "0, 3",           // Jan 1, 1970 (epoch) = Thursday = 3
            "86400, 4",       // Jan 2, 1970 = Friday = 4
            "172800, 5",      // Jan 3, 1970 = Saturday = 5
            "259200, 6",      // Jan 4, 1970 = Sunday = 6
            "345600, 0",      // Jan 5, 1970 = Monday = 0
            "1706140800, 3",  // Jan 25, 2024 = Thursday = 3
            "-172800, 1",     // Dec 30, 1969 = Tuesday = 1
            "-432000, 5"      // Dec 27, 1969 = Saturday = 5 (negative modulo branch)
    })
    void testDayOfWeek_General(long epochSec, int expectedDay) {
        assertEquals(expectedDay, TimeUtils.getDayOfWeek(epochSec));
    }

    @Test
    void testDayOfWeek_LeapYear() {
        // Feb 29, 2024 (leap year) - verify no off-by-one errors
        long feb29_2024 = 1709164800L;  // This is a Thursday
        assertEquals(3, TimeUtils.getDayOfWeek(feb29_2024));
    }

    @Test
    void testDayOfWeek_Before1970() {
        // Dec 31, 1969 = Wednesday = 2
        long dec31_1969 = -86400L;
        assertEquals(2, TimeUtils.getDayOfWeek(dec31_1969));
    }

    @Test
    void testDayOfWeek_Year2100() {
        // Test far future (year 2038+ problem check)
        long jan1_2100 = 4102444800L;  // Jan 1, 2100 = Friday
        assertEquals(4, TimeUtils.getDayOfWeek(jan1_2100));
    }

    // ========== Time of Day Tests ==========

    @ParameterizedTest
    @CsvSource({
            "1706140800, 0",      // Midnight
            "1706184000, 43200",  // Noon (1706140800 + 43200)
            "1706227199, 86399",  // 23:59:59
            "-1, 86399"           // 1 second before 1970
    })
    void testTimeOfDay_General(long epochSec, long expectedSeconds) {
        assertEquals(expectedSeconds, TimeUtils.getTimeOfDay(epochSec));
    }

    @Test
    void testTimeOfDay_MidnightRollover() {
        // One second before midnight
        long beforeMidnight = 1706227199L;
        assertEquals(86399, TimeUtils.getTimeOfDay(beforeMidnight));

        // Exactly midnight next day
        long nextMidnight = beforeMidnight + 1;
        assertEquals(0, TimeUtils.getTimeOfDay(nextMidnight));
    }

    // ========== FIFO Validation Tests ==========

    @Test
    void testFIFO_ValidMonotonicIncrease() {
        long[] arrivals = {100, 150, 200, 250, 300};
        assertTrue(TimeUtils.validateFIFO(arrivals));
    }

    @Test
    void testFIFO_ValidEqualTimes() {
        // Equal times are acceptable (waiting at node)
        long[] arrivals = {100, 150, 150, 200};
        assertTrue(TimeUtils.validateFIFO(arrivals));
    }

    @Test
    void testFIFO_Violation() {
        // Third element arrives before second
        long[] arrivals = {100, 200, 150, 250};
        assertFalse(TimeUtils.validateFIFO(arrivals));
    }

    @Test
    void testFIFO_SingleElement() {
        long[] arrivals = {100};
        assertTrue(TimeUtils.validateFIFO(arrivals));
    }

    @Test
    void testFIFO_EmptyArray() {
        long[] arrivals = {};
        assertTrue(TimeUtils.validateFIFO(arrivals));
    }

    @Test
    void testFIFO_NullArray() {
        assertTrue(TimeUtils.validateFIFO(null));
    }

    // ========== Add Seconds Tests ==========

    @ParameterizedTest
    @CsvSource({
            "1706140800, 3600, 1706144400",   // Positive delta
            "1706140800, -7200, 1706133600",  // Negative delta
            "1706140800, 0, 1706140800"       // Zero delta
    })
    void testAddSeconds(long base, long delta, long expected) {
        assertEquals(expected, TimeUtils.addSeconds(base, delta));
    }

    // ========== Formatting Tests (Debug Only) ==========

    @ParameterizedTest
    @CsvSource({
            "32, 900, 08:00-08:15",   // Standard
            "0, 900, 00:00-00:15",    // Midnight
            "95, 900, 23:45-24:00"    // End of day
    })
    void testBucketToTimeRange(int bucketIndex, int bucketSize, String expectedRange) {
        assertEquals(expectedRange, TimeUtils.bucketToTimeRange(bucketIndex, bucketSize));
    }

    @Test
    void testBucketToTimeRange_ClampEnd() {
        // Forces endSeconds > 86400 to hit clamp branch
        assertEquals("24:00-24:00", TimeUtils.bucketToTimeRange(96, 900));
    }

    @Test
    void testPrivateConstructorThrows() throws Exception {
        var ctor = TimeUtils.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        var ex = assertThrows(java.lang.reflect.InvocationTargetException.class, ctor::newInstance);
        assertTrue(ex.getCause() instanceof AssertionError);
    }

    // ========== Performance Smoke Test ==========

    @Test
    void testPerformance_10MillionBucketCalculations() {
        int bucketSize = 900;
        long start = System.nanoTime();

        // Perform 10 million ops
        // Start from an arbitrary epoch and increment
        long baseEpoch = 1706140800L;

        for (int i = 0; i < 10_000_000; i++) {
            TimeUtils.toBucket(baseEpoch + i, bucketSize);
        }

        long elapsedNs = System.nanoTime() - start;
        double nsPerOp = elapsedNs / 10_000_000.0;

        System.out.printf("10M bucket calculations: %.2f ns/op%n", nsPerOp);

        // Fail only if we are egregiously slow (e.g., > 10ns).
        // In a real JIT warmed environment, this should be ~1-2ns.
        assertTrue(nsPerOp < 10, "Average time per bucket calc: " + nsPerOp + "ns (should be <10ns)");
    }
}
