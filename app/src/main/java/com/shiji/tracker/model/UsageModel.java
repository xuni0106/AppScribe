package com.shiji.tracker.model;

import android.graphics.drawable.Drawable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable view model for all screens. Instances are built off the UI thread
 * and then read freely by views.
 */
public final class UsageModel {

    private UsageModel() {
    }

    public static final class AppUsage {
        public final String pkg;
        public final String label;
        public final Drawable icon;
        public final long ms;
        public final boolean installed;

        public AppUsage(String pkg, String label, Drawable icon, long ms, boolean installed) {
            this.pkg = pkg;
            this.label = label;
            this.icon = icon;
            this.ms = ms;
            this.installed = installed;
        }
    }

    public static final class DayPoint {
        public final LocalDate date;
        public final long ms;

        public DayPoint(LocalDate date, long ms) {
            this.date = date;
            this.ms = ms;
        }
    }

    public static final class RangeStats {
        public final LocalDate start;
        public final LocalDate end;
        public final int dayCount;
        public final long totalMs;
        public final List<AppUsage> apps;
        public final long[] hourMs;
        public final List<DayPoint> days;
        public final int activeDays;

        public RangeStats(LocalDate start, LocalDate end, int dayCount, long totalMs,
                          List<AppUsage> apps, long[] hourMs, List<DayPoint> days,
                          int activeDays) {
            this.start = start;
            this.end = end;
            this.dayCount = dayCount;
            this.totalMs = totalMs;
            this.apps = Collections.unmodifiableList(apps);
            this.hourMs = hourMs;
            this.days = Collections.unmodifiableList(days);
            this.activeDays = activeDays;
        }

        public AppUsage top() {
            return apps.isEmpty() ? null : apps.get(0);
        }

        public int peakHour() {
            int peak = -1;
            long best = 0L;
            for (int i = 0; i < hourMs.length; i++) {
                if (hourMs[i] > best) {
                    best = hourMs[i];
                    peak = i;
                }
            }
            return peak;
        }

        public long peakMs() {
            int peak = peakHour();
            return peak < 0 ? 0L : hourMs[peak];
        }

        public int activeHours() {
            int count = 0;
            for (long value : hourMs) {
                if (value > 0L) {
                    count++;
                }
            }
            return count;
        }
    }

    public static final class Snapshot {
        public final LocalDate today;
        public final RangeStats day;
        public final RangeStats week;
        public final RangeStats month;
        public final RangeStats all;
        public final Map<Integer, RangeStats> years;
        public final List<Integer> availableYears;
        public final int defaultYear;
        public final long lastSyncMs;
        public final boolean hasData;

        public Snapshot(LocalDate today, RangeStats day, RangeStats week, RangeStats month,
                        RangeStats all, Map<Integer, RangeStats> years, int defaultYear,
                        long lastSyncMs, boolean hasData) {
            this.today = today;
            this.day = day;
            this.week = week;
            this.month = month;
            this.all = all;
            this.years = Collections.unmodifiableMap(new LinkedHashMap<>(years));
            List<Integer> ordered = new ArrayList<>(years.keySet());
            Collections.sort(ordered, new Comparator<Integer>() {
                @Override
                public int compare(Integer left, Integer right) {
                    return Integer.compare(right, left);
                }
            });
            this.availableYears = Collections.unmodifiableList(ordered);
            this.defaultYear = defaultYear;
            this.lastSyncMs = lastSyncMs;
            this.hasData = hasData;
        }

        public boolean hasYear(int year) {
            RangeStats stats = years.get(year);
            return stats != null && stats.totalMs > 0L;
        }

        public RangeStats year(int year) {
            return years.get(year);
        }

        public RangeStats yearOrEmpty(int year) {
            RangeStats stats = years.get(year);
            if (stats != null) {
                return stats;
            }
            return new RangeStats(today, today, 1, 0L, Collections.<AppUsage>emptyList(),
                    new long[24], Collections.singletonList(new DayPoint(today, 0L)), 0);
        }
    }
}
