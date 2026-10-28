package com.shiji.tracker.data;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Process;
import android.util.Log;

import com.shiji.tracker.model.UsageModel.AppUsage;
import com.shiji.tracker.model.UsageModel.DayPoint;
import com.shiji.tracker.model.UsageModel.RangeStats;
import com.shiji.tracker.model.UsageModel.Snapshot;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Reads UsageEvents, rolls sessions up into local day/package/hour buckets and
 * exposes immutable screen data. All methods are expected to be called off the
 * main thread except {@link #hasUsageAccess()}.
 *
 * <p>The first successful sync after usage access is granted queries as much
 * UsageEvents history as the platform will return (bounded to one year). Later
 * syncs only revisit the tail since the previous sync. Nothing in this class
 * removes stored rows outside the days that were actually re-observed.</p>
 */
public final class UsageRepository {

    /** How far back a first-time backfill asks the platform for events. */
    private static final int BACKFILL_DAYS = 365;
    /** UsageEvents are processed in bounded windows to keep memory flat. */
    private static final int CHUNK_DAYS = 15;
    /** Pre-roll that re-establishes the foreground app at a window boundary. */
    private static final int OVERLAP_DAYS = 2;
    private static final int RECENT_READ_DAYS = 30;
    private static final String TAG = "ShijiSync";

    private final Context context;
    private final UsageDb db;
    private final PackageManager packageManager;
    private final ZoneId zone = ZoneId.systemDefault();
    private final Map<String, Meta> metaCache = new HashMap<>();

    public UsageRepository(Context context) {
        this.context = context.getApplicationContext();
        this.db = new UsageDb(this.context);
        this.packageManager = this.context.getPackageManager();
    }

    public UsageDb db() {
        return db;
    }

    public boolean hasUsageAccess() {
        try {
            AppOpsManager appOps =
                    (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            if (appOps == null) {
                return false;
            }
            int mode;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                mode = appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                        Process.myUid(), context.getPackageName());
            } else {
                mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                        Process.myUid(), context.getPackageName());
            }
            if (mode == AppOpsManager.MODE_ALLOWED) {
                return true;
            }
            if (mode == AppOpsManager.MODE_DEFAULT) {
                // A few OEM builds report DEFAULT even after the toggle was granted.
                UsageStatsManager stats =
                        (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
                if (stats != null) {
                    long now = System.currentTimeMillis();
                    List<android.app.usage.UsageStats> recent =
                            stats.queryUsageStats(UsageStatsManager.INTERVAL_DAILY,
                                    now - TimeUnit.HOURS.toMillis(24), now);
                    return recent != null && !recent.isEmpty();
                }
            }
        } catch (Throwable ignored) {
            // Permission is treated as absent when AppOps cannot be inspected.
        }
        return false;
    }

    public static final class SyncReport {
        public final int observedDays;
        public final int packageRows;

        SyncReport(int observedDays, int packageRows) {
            this.observedDays = observedDays;
            this.packageRows = packageRows;
        }
    }

    /**
     * Syncs UsageEvents into the permanent history table.
     *
     * <p>The first call after authorization (or after the database was recreated)
     * walks back up to {@link #BACKFILL_DAYS} days in bounded chunks. Subsequent
     * calls only process the tail starting one day before the previous sync, so
     * regular refreshes stay cheap while stored days outside that tail are never
     * touched.</p>
     */
    public SyncReport syncHistory() {
        UsageStatsManager manager =
                (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (manager == null) {
            return new SyncReport(0, 0);
        }

        LocalDate today = LocalDate.now(zone);
        long now = System.currentTimeMillis();
        LocalDate earliest = today.minusDays(BACKFILL_DAYS - 1L);
        long lastSync = db.lastSyncMs();
        boolean firstHistorySync = !"1".equals(db.meta(UsageDb.META_HISTORY_SYNCED));
        LocalDate start = earliest;
        if (!firstHistorySync && lastSync > 0L) {
            LocalDate previousDay = Instant.ofEpochMilli(lastSync).atZone(zone).toLocalDate();
            LocalDate resume = previousDay.minusDays(1L);
            if (resume.isBefore(earliest)) {
                resume = earliest;
            } else if (resume.isAfter(today)) {
                resume = today;
            }
            start = resume;
        }

        Map<String, Map<String, Long>> dayPackages = new LinkedHashMap<>();
        Map<String, long[]> dayHours = new HashMap<>();

        LocalDate windowStart = start;
        while (!windowStart.isAfter(today)) {
            LocalDate windowEnd = windowStart.plusDays(CHUNK_DAYS - 1L);
            if (windowEnd.isAfter(today)) {
                windowEnd = today;
            }
            long rangeStart = windowStart.atStartOfDay(zone).toInstant().toEpochMilli();
            long rangeEnd = Math.min(now,
                    windowEnd.plusDays(1L).atStartOfDay(zone).toInstant().toEpochMilli());
            if (rangeEnd > rangeStart) {
                collect(manager,
                        rangeStart - TimeUnit.DAYS.toMillis(OVERLAP_DAYS),
                        rangeEnd, rangeStart, rangeEnd, dayPackages, dayHours);
            }
            windowStart = windowEnd.plusDays(1L);
        }

        long syncAt = System.currentTimeMillis();
        db.saveSync(dayPackages, dayHours, syncAt);
        if (firstHistorySync) {
            db.putMeta(UsageDb.META_HISTORY_SYNCED, "1");
        }

        int packageRows = 0;
        for (Map<String, Long> packages : dayPackages.values()) {
            packageRows += packages.size();
        }
        Log.i(TAG, "sync start=" + start + " backfill=" + firstHistorySync
                + " startMs=" + start.atStartOfDay(zone).toInstant().toEpochMilli()
                + " days=" + dayPackages.size() + " rows=" + packageRows);
        return new SyncReport(dayPackages.size(), packageRows);
    }

    private void collect(UsageStatsManager manager, long queryStart, long queryEnd,
                         long rangeStart, long rangeEnd,
                         Map<String, Map<String, Long>> dayPackages,
                         Map<String, long[]> dayHours) {
        UsageEvents events = manager.queryEvents(queryStart, queryEnd);
        if (events == null) {
            return;
        }
        UsageEvents.Event event = new UsageEvents.Event();
        String currentPkg = null;
        long currentStart = 0L;
        while (events.hasNextEvent()) {
            if (!events.getNextEvent(event)) {
                break;
            }
            String pkg = event.getPackageName();
            long ts = event.getTimeStamp();
            int type = event.getEventType();

            if (type == UsageEvents.Event.ACTIVITY_RESUMED) {
                if (pkg == null || pkg.isEmpty()) {
                    continue;
                }
                if (currentPkg != null && currentPkg.equals(pkg)) {
                    continue;
                }
                if (currentPkg != null) {
                    addInterval(currentPkg, currentStart, ts, rangeStart, rangeEnd,
                            dayPackages, dayHours);
                }
                currentPkg = pkg;
                currentStart = ts;
            } else if (type == UsageEvents.Event.ACTIVITY_PAUSED
                    || type == UsageEvents.Event.ACTIVITY_STOPPED) {
                if (currentPkg != null && currentPkg.equals(pkg)) {
                    addInterval(currentPkg, currentStart, ts, rangeStart, rangeEnd,
                            dayPackages, dayHours);
                    currentPkg = null;
                }
            } else if (type == UsageEvents.Event.SCREEN_NON_INTERACTIVE
                    || type == UsageEvents.Event.KEYGUARD_SHOWN
                    || type == UsageEvents.Event.DEVICE_SHUTDOWN) {
                if (currentPkg != null) {
                    addInterval(currentPkg, currentStart, ts, rangeStart, rangeEnd,
                            dayPackages, dayHours);
                    currentPkg = null;
                }
            }
        }
        if (currentPkg != null) {
            addInterval(currentPkg, currentStart, rangeEnd, rangeStart, rangeEnd,
                    dayPackages, dayHours);
        }
    }

    private void addInterval(String pkg, long rawStart, long rawEnd, long rangeStart,
                             long rangeEnd,
                             Map<String, Map<String, Long>> dayPackages,
                             Map<String, long[]> dayHours) {
        long start = Math.max(rawStart, rangeStart);
        long end = Math.min(rawEnd, rangeEnd);
        if (end <= start) {
            return;
        }
        long cursor = start;
        int guard = 0;
        while (cursor < end && guard++ < 200_000) {
            ZonedDateTime dateTime = Instant.ofEpochMilli(cursor).atZone(zone);
            long nextHour = dateTime.truncatedTo(ChronoUnit.HOURS).plusHours(1)
                    .toInstant().toEpochMilli();
            long sliceEnd = Math.min(end, nextHour);
            LocalDate date = dateTime.toLocalDate();
            long delta = sliceEnd - cursor;
            if (delta > 0L) {
                String day = date.toString();
                Map<String, Long> packages = dayPackages.get(day);
                if (packages == null) {
                    packages = new LinkedHashMap<>();
                    dayPackages.put(day, packages);
                }
                Long previous = packages.get(pkg);
                packages.put(pkg, (previous == null ? 0L : previous) + delta);

                long[] hours = dayHours.get(day);
                if (hours == null) {
                    hours = new long[24];
                    dayHours.put(day, hours);
                }
                hours[dateTime.getHour()] += delta;
            }
            cursor = sliceEnd;
        }
    }

    /**
     * Builds day / 7-day / 30-day / all-time snapshots. The all-time range is
     * aggregated in SQL; only the 30-day tail is read row by row.
     */
    public Snapshot loadSnapshot() {
        LocalDate today = LocalDate.now(zone);
        LocalDate oldest = today.minusDays(RECENT_READ_DAYS - 1L);
        UsageDb.Rows rows = db.readRows(oldest.toString(), today.toString());

        Map<LocalDate, Map<String, Long>> packagesByDay = new HashMap<>();
        for (UsageDb.PkgRow row : rows.packages) {
            LocalDate date = parseDate(row.day);
            if (date == null) {
                continue;
            }
            Map<String, Long> packages = packagesByDay.get(date);
            if (packages == null) {
                packages = new HashMap<>();
                packagesByDay.put(date, packages);
            }
            Long old = packages.get(row.pkg);
            packages.put(row.pkg, (old == null ? 0L : old) + row.ms);
        }

        Map<LocalDate, long[]> hoursByDay = new HashMap<>();
        for (UsageDb.HourRow row : rows.hours) {
            LocalDate date = parseDate(row.day);
            if (date == null || row.hour < 0 || row.hour > 23) {
                continue;
            }
            long[] hours = hoursByDay.get(date);
            if (hours == null) {
                hours = new long[24];
                hoursByDay.put(date, hours);
            }
            hours[row.hour] += row.ms;
        }

        RangeStats day = buildRange(today, today, packagesByDay, hoursByDay);
        RangeStats week = buildRange(today.minusDays(6), today, packagesByDay, hoursByDay);
        RangeStats month = buildRange(oldest, today, packagesByDay, hoursByDay);
        RangeStats all = buildAllRange();

        List<Integer> availableYears = db.readYears();
        Map<Integer, RangeStats> yearStats = new LinkedHashMap<>();
        for (Integer year : availableYears) {
            yearStats.put(year, buildYearRange(year, today));
        }
        int defaultYear = pickDefaultYear(yearStats, availableYears, today.getYear());

        long lastSync = db.lastSyncMs();
        boolean hasData = all.totalMs > 0L;
        Log.i(TAG, "snapshot all " + all.start + ".." + all.end + " days=" + all.dayCount
                + " activeDays=" + all.activeDays + " totalMs=" + all.totalMs
                + " years=" + availableYears + " defaultYear=" + defaultYear);
        return new Snapshot(today, day, week, month, all, yearStats, defaultYear,
                lastSync, hasData);
    }

    private RangeStats buildYearRange(int year, LocalDate today) {
        LocalDate nominalStart = LocalDate.of(year, 1, 1);
        LocalDate nominalEnd = year == today.getYear() ? today : LocalDate.of(year, 12, 31);
        UsageDb.Totals totals = db.readTotalsForYear(year);

        Map<LocalDate, Long> dayMs = new HashMap<>();
        for (UsageDb.DayTotal row : totals.days) {
            LocalDate date = parseDate(row.day);
            if (date == null) {
                continue;
            }
            dayMs.put(date, row.ms);
        }

        List<DayPoint> days = new ArrayList<>();
        for (LocalDate date = nominalStart; !date.isAfter(nominalEnd); date = date.plusDays(1)) {
            Long ms = dayMs.get(date);
            days.add(new DayPoint(date, ms == null ? 0L : ms));
        }

        List<AppUsage> apps = new ArrayList<>(totals.packages.size());
        for (UsageDb.PackageTotal row : totals.packages) {
            if (row.ms <= 0L) {
                continue;
            }
            Meta meta = resolveMeta(row.pkg);
            apps.add(new AppUsage(row.pkg, meta.label, meta.icon, row.ms, meta.installed));
        }

        // A year view names its whole calendar window; activeDays still records
        // how many of those days actually carried usage.
        return new RangeStats(nominalStart, nominalEnd, days.size(), totals.totalMs, apps,
                totals.hourMs, days, totals.activeDays);
    }

    private static int pickDefaultYear(Map<Integer, RangeStats> yearStats,
                                       List<Integer> years, int currentYear) {
        RangeStats current = yearStats.get(currentYear);
        if (current != null && current.totalMs > 0L) {
            return currentYear;
        }
        for (Integer year : years) {
            RangeStats stats = yearStats.get(year);
            if (stats != null && stats.totalMs > 0L) {
                return year;
            }
        }
        return currentYear;
    }

    private RangeStats buildRange(LocalDate start, LocalDate end,
                                  Map<LocalDate, Map<String, Long>> packagesByDay,
                                  Map<LocalDate, long[]> hoursByDay) {
        Map<String, Long> packageTotals = new HashMap<>();
        long[] hourTotals = new long[24];
        List<DayPoint> days = new ArrayList<>();
        long total = 0L;
        int activeDays = 0;
        int dayCount = 0;
        LocalDate firstActive = null;
        LocalDate lastActive = null;

        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            dayCount++;
            Map<String, Long> packages = packagesByDay.get(date);
            long dayTotal = 0L;
            if (packages != null) {
                for (Map.Entry<String, Long> entry : packages.entrySet()) {
                    long value = entry.getValue();
                    if (value <= 0L) {
                        continue;
                    }
                    dayTotal += value;
                    Long previous = packageTotals.get(entry.getKey());
                    packageTotals.put(entry.getKey(),
                            (previous == null ? 0L : previous) + value);
                }
            }
            long[] hours = hoursByDay.get(date);
            if (hours != null) {
                for (int i = 0; i < 24; i++) {
                    hourTotals[i] += Math.max(0L, hours[i]);
                }
            }
            days.add(new DayPoint(date, dayTotal));
            total += dayTotal;
            if (dayTotal > 0L) {
                activeDays++;
                if (firstActive == null) {
                    firstActive = date;
                }
                lastActive = date;
            }
        }

        List<AppUsage> apps = sortedApps(packageTotals);
        // Show the dates that actually carry data, not the nominal window edges.
        LocalDate displayStart = firstActive == null ? start : firstActive;
        LocalDate displayEnd = lastActive == null ? end : lastActive;
        return new RangeStats(displayStart, displayEnd, dayCount, total, apps, hourTotals, days,
                activeDays);
    }

    private RangeStats buildAllRange() {
        LocalDate today = LocalDate.now(zone);
        UsageDb.Totals totals = db.readTotals();

        Map<LocalDate, Long> dayMs = new HashMap<>();
        LocalDate first = null;
        LocalDate last = null;
        for (UsageDb.DayTotal row : totals.days) {
            LocalDate date = parseDate(row.day);
            if (date == null) {
                continue;
            }
            dayMs.put(date, row.ms);
            if (first == null) {
                first = date;
            }
            last = date;
        }

        List<DayPoint> days = new ArrayList<>();
        if (first != null && last != null && !last.isBefore(first)) {
            for (LocalDate date = first; !date.isAfter(last); date = date.plusDays(1)) {
                Long ms = dayMs.get(date);
                days.add(new DayPoint(date, ms == null ? 0L : ms));
            }
        }

        List<AppUsage> apps = new ArrayList<>(totals.packages.size());
        for (UsageDb.PackageTotal row : totals.packages) {
            if (row.ms <= 0L) {
                continue;
            }
            Meta meta = resolveMeta(row.pkg);
            apps.add(new AppUsage(row.pkg, meta.label, meta.icon, row.ms, meta.installed));
        }

        LocalDate start = first == null ? today : first;
        LocalDate end = last == null ? start : last;
        return new RangeStats(start, end, days.size(), totals.totalMs, apps, totals.hourMs, days,
                totals.activeDays);
    }

    private List<AppUsage> sortedApps(Map<String, Long> packageTotals) {
        List<Map.Entry<String, Long>> sorted = new ArrayList<>(packageTotals.entrySet());
        Collections.sort(sorted, new Comparator<Map.Entry<String, Long>>() {
            @Override
            public int compare(Map.Entry<String, Long> left, Map.Entry<String, Long> right) {
                int byValue = Long.compare(right.getValue(), left.getValue());
                if (byValue != 0) {
                    return byValue;
                }
                return left.getKey().compareTo(right.getKey());
            }
        });

        List<AppUsage> apps = new ArrayList<>(sorted.size());
        for (Map.Entry<String, Long> entry : sorted) {
            if (entry.getValue() <= 0L) {
                continue;
            }
            Meta meta = resolveMeta(entry.getKey());
            apps.add(new AppUsage(entry.getKey(), meta.label, meta.icon, entry.getValue(),
                    meta.installed));
        }
        return apps;
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static final class Meta {
        final String label;
        final Drawable icon;
        final boolean installed;

        Meta(String label, Drawable icon, boolean installed) {
            this.label = label;
            this.icon = icon;
            this.installed = installed;
        }
    }

    private Meta resolveMeta(String pkg) {
        Meta cached = metaCache.get(pkg);
        if (cached != null) {
            return cached;
        }
        String label = pkg;
        Drawable icon = null;
        boolean installed = false;
        try {
            ApplicationInfo info = packageManager.getApplicationInfo(pkg, 0);
            CharSequence loaded = info.loadLabel(packageManager);
            if (loaded != null && loaded.length() > 0) {
                label = loaded.toString();
            }
            icon = info.loadIcon(packageManager);
            installed = true;
        } catch (Throwable ignored) {
            // Uninstalled package from imported data: use the package identifier.
        }
        Meta meta = new Meta(label, icon, installed);
        metaCache.put(pkg, meta);
        return meta;
    }
}
