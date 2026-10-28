package com.shiji.tracker.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Local-only persistence. No network code exists in the application.
 *
 * <p>{@code day_pkg} and {@code day_hour} are permanent history: rows are only
 * ever merged or replaced for a day that was actually re-observed, never removed
 * because a screen shows a shorter range.</p>
 */
public final class UsageDb extends SQLiteOpenHelper {

    private static final String NAME = "shiji.db";
    private static final int VERSION = 1;

    public static final String META_LAST_SYNC_MS = "last_sync_ms";
    public static final String META_HISTORY_SYNCED = "history_synced";

    public UsageDb(Context context) {
        super(context, NAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE day_pkg ("
                + "day TEXT NOT NULL,"
                + "pkg TEXT NOT NULL,"
                + "total_ms INTEGER NOT NULL,"
                + "PRIMARY KEY(day, pkg))");
        db.execSQL("CREATE TABLE day_hour ("
                + "day TEXT NOT NULL,"
                + "hour INTEGER NOT NULL,"
                + "total_ms INTEGER NOT NULL,"
                + "PRIMARY KEY(day, hour))");
        db.execSQL("CREATE TABLE meta ("
                + "key TEXT PRIMARY KEY,"
                + "value TEXT NOT NULL)");
        db.execSQL("CREATE INDEX idx_day_pkg_day ON day_pkg(day)");
        db.execSQL("CREATE INDEX idx_day_hour_day ON day_hour(day)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Schema 1 is the first public schema. Any future migration must keep
        // existing day_pkg / day_hour rows intact; history is never trimmed.
    }

    public static final class PkgRow {
        public final String day;
        public final String pkg;
        public final long ms;

        public PkgRow(String day, String pkg, long ms) {
            this.day = day;
            this.pkg = pkg;
            this.ms = ms;
        }
    }

    public static final class HourRow {
        public final String day;
        public final int hour;
        public final long ms;

        public HourRow(String day, int hour, long ms) {
            this.day = day;
            this.hour = hour;
            this.ms = ms;
        }
    }

    public static final class Rows {
        public final List<PkgRow> packages;
        public final List<HourRow> hours;

        public Rows(List<PkgRow> packages, List<HourRow> hours) {
            this.packages = packages;
            this.hours = hours;
        }
    }

    public static final class PackageTotal {
        public final String pkg;
        public final long ms;

        public PackageTotal(String pkg, long ms) {
            this.pkg = pkg;
            this.ms = ms;
        }
    }

    public static final class DayTotal {
        public final String day;
        public final long ms;

        public DayTotal(String day, long ms) {
            this.day = day;
            this.ms = ms;
        }
    }

    /** SQL-aggregated totals over the entire retained history. */
    public static final class Totals {
        public final long totalMs;
        public final int activeDays;
        public final String firstDay;
        public final String lastDay;
        public final List<PackageTotal> packages;
        public final List<DayTotal> days;
        public final long[] hourMs;

        Totals(long totalMs, int activeDays, String firstDay, String lastDay,
               List<PackageTotal> packages, List<DayTotal> days, long[] hourMs) {
            this.totalMs = totalMs;
            this.activeDays = activeDays;
            this.firstDay = firstDay;
            this.lastDay = lastDay;
            this.packages = packages;
            this.days = days;
            this.hourMs = hourMs;
        }
    }

    public synchronized void saveSync(Map<String, Map<String, Long>> dayPackages,
                                      Map<String, long[]> dayHours, long syncAt) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (Map.Entry<String, Map<String, Long>> dayEntry : dayPackages.entrySet()) {
                String day = dayEntry.getKey();
                Map<String, Long> packages = dayEntry.getValue();
                if (packages.isEmpty()) {
                    continue;
                }
                // A day observed locally is authoritative for that day. Only that
                // day is rewritten; every other historic day is left untouched.
                db.delete("day_pkg", "day=?", new String[]{day});
                db.delete("day_hour", "day=?", new String[]{day});
                for (Map.Entry<String, Long> entry : packages.entrySet()) {
                    long ms = entry.getValue();
                    if (ms <= 0L) {
                        continue;
                    }
                    ContentValues values = new ContentValues();
                    values.put("day", day);
                    values.put("pkg", entry.getKey());
                    values.put("total_ms", ms);
                    db.insertWithOnConflict("day_pkg", null, values, SQLiteDatabase.CONFLICT_REPLACE);
                }
                long[] hours = dayHours.get(day);
                if (hours != null) {
                    for (int hour = 0; hour < 24; hour++) {
                        if (hours[hour] <= 0L) {
                            continue;
                        }
                        ContentValues values = new ContentValues();
                        values.put("day", day);
                        values.put("hour", hour);
                        values.put("total_ms", hours[hour]);
                        db.insertWithOnConflict("day_hour", null, values,
                                SQLiteDatabase.CONFLICT_REPLACE);
                    }
                }
            }
            putMetaRow(db, META_LAST_SYNC_MS, String.valueOf(syncAt));
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public synchronized Rows readRows(String oldestDay, String newestDay) {
        SQLiteDatabase db = getReadableDatabase();
        List<PkgRow> packages = new ArrayList<>();
        List<HourRow> hours = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(
                "SELECT day,pkg,total_ms FROM day_pkg WHERE day>=? AND day<=? ORDER BY day,pkg",
                new String[]{oldestDay, newestDay})) {
            while (cursor.moveToNext()) {
                packages.add(new PkgRow(cursor.getString(0), cursor.getString(1), cursor.getLong(2)));
            }
        }
        try (Cursor cursor = db.rawQuery(
                "SELECT day,hour,total_ms FROM day_hour WHERE day>=? AND day<=? ORDER BY day,hour",
                new String[]{oldestDay, newestDay})) {
            while (cursor.moveToNext()) {
                hours.add(new HourRow(cursor.getString(0), cursor.getInt(1), cursor.getLong(2)));
            }
        }
        return new Rows(packages, hours);
    }

    public synchronized Rows readAllRows() {
        SQLiteDatabase db = getReadableDatabase();
        List<PkgRow> packages = new ArrayList<>();
        List<HourRow> hours = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(
                "SELECT day,pkg,total_ms FROM day_pkg ORDER BY day,pkg", null)) {
            while (cursor.moveToNext()) {
                packages.add(new PkgRow(cursor.getString(0), cursor.getString(1), cursor.getLong(2)));
            }
        }
        try (Cursor cursor = db.rawQuery(
                "SELECT day,hour,total_ms FROM day_hour ORDER BY day,hour", null)) {
            while (cursor.moveToNext()) {
                hours.add(new HourRow(cursor.getString(0), cursor.getInt(1), cursor.getLong(2)));
            }
        }
        return new Rows(packages, hours);
    }

    /**
     * Aggregate the full retained history in SQL so that an all-time screen never
     * has to materialise every day/package row in memory.
     */
    public synchronized Totals readTotals() {
        return readTotalsInternal(null, null);
    }

    /** SQL aggregation restricted to one calendar year. */
    public synchronized Totals readTotalsForYear(int year) {
        return readTotalsInternal(year + "-01-01", year + "-12-31");
    }

    /**
     * Years that actually contain stored package rows, newest first. Used by the
     * year selector; years with only hour rows are not offered.
     */
    public synchronized List<Integer> readYears() {
        List<Integer> years = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.rawQuery(
                "SELECT DISTINCT substr(day,1,4) AS y FROM day_pkg ORDER BY y DESC", null)) {
            int maxYear = LocalDate.now().getYear() + 1;
            while (cursor.moveToNext()) {
                try {
                    int year = Integer.parseInt(cursor.getString(0));
                    if (year >= 2000 && year <= maxYear) {
                        years.add(year);
                    }
                } catch (NumberFormatException ignored) {
                    // Ignore malformed imported day keys.
                }
            }
        }
        return years;
    }

    private Totals readTotalsInternal(String start, String end) {
        SQLiteDatabase db = getReadableDatabase();
        List<PackageTotal> packages = new ArrayList<>();
        List<DayTotal> days = new ArrayList<>();
        long[] hours = new long[24];
        long total = 0L;
        boolean bounded = start != null && end != null;
        String where = bounded ? " WHERE day>=? AND day<=?" : "";
        String[] args = bounded ? new String[]{start, end} : null;

        try (Cursor cursor = db.rawQuery(
                "SELECT pkg,SUM(total_ms) AS ms FROM day_pkg" + where
                        + " GROUP BY pkg HAVING SUM(total_ms)>0 ORDER BY ms DESC,pkg ASC",
                args)) {
            while (cursor.moveToNext()) {
                packages.add(new PackageTotal(cursor.getString(0), cursor.getLong(1)));
            }
        }
        try (Cursor cursor = db.rawQuery(
                "SELECT day,SUM(total_ms) AS ms FROM day_pkg" + where
                        + " GROUP BY day HAVING SUM(total_ms)>0 ORDER BY day", args)) {
            while (cursor.moveToNext()) {
                String day = cursor.getString(0);
                long ms = cursor.getLong(1);
                days.add(new DayTotal(day, ms));
                total += ms;
            }
        }
        try (Cursor cursor = db.rawQuery(
                "SELECT hour,SUM(total_ms) FROM day_hour" + where + " GROUP BY hour", args)) {
            while (cursor.moveToNext()) {
                int hour = cursor.getInt(0);
                if (hour >= 0 && hour < 24) {
                    hours[hour] += Math.max(0L, cursor.getLong(1));
                }
            }
        }

        String firstDay = days.isEmpty() ? null : days.get(0).day;
        String lastDay = days.isEmpty() ? null : days.get(days.size() - 1).day;
        return new Totals(total, days.size(), firstDay, lastDay, packages, days, hours);
    }

    /**
     * Merge backup rows. Same day/package or day/hour keys are replaced.
     *
     * @return amount of distinct days present in the imported file
     */
    public synchronized int importRows(List<PkgRow> packages, List<HourRow> hours) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            Map<String, Boolean> days = new LinkedHashMap<>();
            for (PkgRow row : packages) {
                ContentValues values = new ContentValues();
                values.put("day", row.day);
                values.put("pkg", row.pkg);
                values.put("total_ms", row.ms);
                db.insertWithOnConflict("day_pkg", null, values, SQLiteDatabase.CONFLICT_REPLACE);
                days.put(row.day, Boolean.TRUE);
            }
            for (HourRow row : hours) {
                ContentValues values = new ContentValues();
                values.put("day", row.day);
                values.put("hour", row.hour);
                values.put("total_ms", row.ms);
                db.insertWithOnConflict("day_hour", null, values, SQLiteDatabase.CONFLICT_REPLACE);
                days.put(row.day, Boolean.TRUE);
            }
            db.setTransactionSuccessful();
            return days.size();
        } finally {
            db.endTransaction();
        }
    }

    public synchronized String meta(String key) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT value FROM meta WHERE key=?",
                new String[]{key})) {
            if (cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        }
        return null;
    }

    public synchronized void putMeta(String key, String value) {
        SQLiteDatabase db = getWritableDatabase();
        putMetaRow(db, key, value);
    }

    private void putMetaRow(SQLiteDatabase db, String key, String value) {
        ContentValues meta = new ContentValues();
        meta.put("key", key);
        meta.put("value", value);
        db.insertWithOnConflict("meta", null, meta, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized long lastSyncMs() {
        String value = meta(META_LAST_SYNC_MS);
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    public synchronized Map<String, Map<String, Long>> snapshotPackages(String oldest,
                                                                        String newest) {
        Map<String, Map<String, Long>> result = new HashMap<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.rawQuery(
                "SELECT day,pkg,total_ms FROM day_pkg WHERE day>=? AND day<=?",
                new String[]{oldest, newest})) {
            while (cursor.moveToNext()) {
                String day = cursor.getString(0);
                Map<String, Long> map = result.get(day);
                if (map == null) {
                    map = new HashMap<>();
                    result.put(day, map);
                }
                map.put(cursor.getString(1), cursor.getLong(2));
            }
        }
        return result;
    }

    public synchronized Map<String, long[]> snapshotHours(String oldest, String newest) {
        Map<String, long[]> result = new HashMap<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.rawQuery(
                "SELECT day,hour,total_ms FROM day_hour WHERE day>=? AND day<=?",
                new String[]{oldest, newest})) {
            while (cursor.moveToNext()) {
                String day = cursor.getString(0);
                long[] hours = result.get(day);
                if (hours == null) {
                    hours = new long[24];
                    result.put(day, hours);
                }
                int hour = cursor.getInt(1);
                if (hour >= 0 && hour < 24) {
                    hours[hour] = cursor.getLong(2);
                }
            }
        }
        return result;
    }
}
