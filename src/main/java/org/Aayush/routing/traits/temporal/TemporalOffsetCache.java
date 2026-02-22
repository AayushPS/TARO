package org.Aayush.routing.traits.temporal;

import java.time.Instant;
import java.time.ZoneId;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Deterministic zone-offset cache keyed by epoch-day.
 *
 * <p>Each cache entry stores one-day offset windows so DST transition days are resolved
 * exactly without requiring full rules traversal on every edge-cost evaluation.</p>
 */
public final class TemporalOffsetCache {
    private static final long SECONDS_PER_DAY = 86_400L;
    private static final int MAX_TRANSITIONS_PER_DAY = 8;

    private final ZoneId zoneId;
    private final ConcurrentMap<Long, DayOffsetSchedule> scheduleByEpochDay = new ConcurrentHashMap<>();

    /**
     * Creates cache for one runtime zone id.
     *
     * @param zoneId runtime zone id.
     */
    public TemporalOffsetCache(ZoneId zoneId) {
        this.zoneId = Objects.requireNonNull(zoneId, "zoneId");
    }

    /**
     * Returns total offset seconds for the provided epoch-second instant.
     *
     * @param epochSeconds instant in Unix epoch seconds.
     * @return total zone offset in seconds.
     */
    public int offsetSeconds(long epochSeconds) {
        try {
            long epochDay = Math.floorDiv(epochSeconds, SECONDS_PER_DAY);
            DayOffsetSchedule schedule = scheduleByEpochDay.computeIfAbsent(epochDay, this::buildScheduleForEpochDay);
            return schedule.offsetSeconds(epochSeconds);
        } catch (RuntimeException ex) {
            return fallbackOffsetSeconds(epochSeconds);
        }
    }

    /**
     * Returns current cache size in cached epoch days.
     */
    public int cachedDays() {
        return scheduleByEpochDay.size();
    }

    private DayOffsetSchedule buildScheduleForEpochDay(long epochDay) {
        long dayStart = Math.multiplyExact(epochDay, SECONDS_PER_DAY);
        long dayEnd = Math.addExact(dayStart, SECONDS_PER_DAY);
        ZoneRules rules = zoneId.getRules();

        List<OffsetWindow> windows = new ArrayList<>(2);
        long windowStart = dayStart;
        int currentOffset = rules.getOffset(Instant.ofEpochSecond(dayStart)).getTotalSeconds();

        ZoneOffsetTransition transition = rules.nextTransition(Instant.ofEpochSecond(dayStart));
        int guard = 0;
        while (transition != null && guard < MAX_TRANSITIONS_PER_DAY) {
            guard++;
            long transitionEpoch = transition.getInstant().getEpochSecond();
            if (transitionEpoch >= dayEnd) {
                break;
            }
            if (transitionEpoch > windowStart) {
                windows.add(new OffsetWindow(windowStart, transitionEpoch, currentOffset));
                windowStart = transitionEpoch;
            }
            currentOffset = transition.getOffsetAfter().getTotalSeconds();
            transition = rules.nextTransition(transition.getInstant());
        }
        windows.add(new OffsetWindow(windowStart, dayEnd, currentOffset));
        return DayOffsetSchedule.of(windows);
    }

    private int fallbackOffsetSeconds(long epochSeconds) {
        ZoneRules rules = zoneId.getRules();
        if (rules.isFixedOffset()) {
            return rules.getOffset(Instant.EPOCH).getTotalSeconds();
        }
        long clampedEpochSeconds = clampEpochSecond(epochSeconds);
        return rules.getOffset(Instant.ofEpochSecond(clampedEpochSeconds)).getTotalSeconds();
    }

    private long clampEpochSecond(long epochSeconds) {
        long min = Instant.MIN.getEpochSecond();
        long max = Instant.MAX.getEpochSecond();
        if (epochSeconds < min) {
            return min;
        }
        if (epochSeconds > max) {
            return max;
        }
        return epochSeconds;
    }

    private record OffsetWindow(long startInclusive, long endExclusive, int offsetSeconds) {
    }

    private static final class DayOffsetSchedule {
        private final long[] startInclusive;
        private final long[] endExclusive;
        private final int[] offsetSeconds;

        private DayOffsetSchedule(long[] startInclusive, long[] endExclusive, int[] offsetSeconds) {
            this.startInclusive = startInclusive;
            this.endExclusive = endExclusive;
            this.offsetSeconds = offsetSeconds;
        }

        static DayOffsetSchedule of(List<OffsetWindow> windows) {
            int size = windows.size();
            long[] starts = new long[size];
            long[] ends = new long[size];
            int[] offsets = new int[size];
            for (int i = 0; i < size; i++) {
                OffsetWindow window = windows.get(i);
                starts[i] = window.startInclusive();
                ends[i] = window.endExclusive();
                offsets[i] = window.offsetSeconds();
            }
            return new DayOffsetSchedule(starts, ends, offsets);
        }

        int offsetSeconds(long epochSeconds) {
            int size = offsetSeconds.length;
            for (int i = 0; i < size; i++) {
                if (epochSeconds >= startInclusive[i] && epochSeconds < endExclusive[i]) {
                    return offsetSeconds[i];
                }
            }
            if (epochSeconds < startInclusive[0]) {
                return offsetSeconds[0];
            }
            return offsetSeconds[size - 1];
        }
    }
}
