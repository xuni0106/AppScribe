package com.shiji.tracker.data;

import android.content.ContentResolver;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Versioned JSON backup. Files are exchanged only through the Storage Access
 * Framework; no permission or network access is involved.
 */
public final class BackupManager {

    private static final String FORMAT = "shiji-usage";
    // Schema 1 remains the wire format after 1.0.2: field names, row shapes and
    // date validation are unchanged, so 1.0.1 backups import into 1.0.2 and
    // 1.0.2 backups still import into 1.0.1 (older builds keep days they do not
    // show in their 30-day views). Every stored day is exported.
    private static final int SCHEMA = 1;
    private static final int MAX_FILE_BYTES = 32 * 1024 * 1024;
    private static final int MAX_ROWS = 300_000;
    private static final long MAX_PKG_MS = 172_800_000L;
    private static final long MAX_HOUR_MS = 86_400_000L;

    private BackupManager() {
    }

    public static final class ImportResult {
        public final int days;
        public final int packageRows;
        public final int hourRows;

        ImportResult(int days, int packageRows, int hourRows) {
            this.days = days;
            this.packageRows = packageRows;
            this.hourRows = hourRows;
        }
    }

    public static void exportJson(ContentResolver resolver, Uri uri, UsageDb db,
                                  String versionName) throws IOException, JSONException {
        UsageDb.Rows rows = db.readAllRows();
        JSONObject root = new JSONObject();
        root.put("format", FORMAT);
        root.put("schema", SCHEMA);
        root.put("version", versionName);
        root.put("exportedAt", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));

        JSONArray packages = new JSONArray();
        for (UsageDb.PkgRow row : rows.packages) {
            JSONObject item = new JSONObject();
            item.put("day", row.day);
            item.put("pkg", row.pkg);
            item.put("ms", row.ms);
            packages.put(item);
        }
        JSONArray hours = new JSONArray();
        for (UsageDb.HourRow row : rows.hours) {
            JSONObject item = new JSONObject();
            item.put("day", row.day);
            item.put("hour", row.hour);
            item.put("ms", row.ms);
            hours.put(item);
        }
        root.put("dayPkg", packages);
        root.put("hours", hours);

        try (OutputStream output = resolver.openOutputStream(uri, "wt")) {
            if (output == null) {
                throw new IOException("Cannot open destination");
            }
            output.write(root.toString().getBytes(StandardCharsets.UTF_8));
            output.flush();
        }
    }

    public static ImportResult importJson(ContentResolver resolver, Uri uri, UsageDb db)
            throws IOException {
        byte[] data;
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) {
                throw new IOException("Cannot open source");
            }
            data = readLimited(input, MAX_FILE_BYTES);
        }
        if (data.length == 0) {
            throw new IOException("Empty file");
        }

        try {
            JSONObject root = new JSONObject(new String(data, StandardCharsets.UTF_8));
            if (!FORMAT.equals(root.optString("format"))) {
                throw new IOException("Unknown backup format");
            }
            int schema = root.optInt("schema", -1);
            if (schema < 1 || schema > SCHEMA) {
                throw new IOException("Unsupported schema");
            }
            if (!root.has("dayPkg") && !root.has("hours")) {
                throw new IOException("No usage payload");
            }

            List<UsageDb.PkgRow> packageRows = new ArrayList<>();
            List<UsageDb.HourRow> hourRows = new ArrayList<>();
            Set<String> days = new HashSet<>();

            JSONArray packages = root.optJSONArray("dayPkg");
            if (packages != null) {
                if (packages.length() > MAX_ROWS) {
                    throw new IOException("Too many rows");
                }
                for (int i = 0; i < packages.length(); i++) {
                    JSONObject item = packages.optJSONObject(i);
                    if (item == null) {
                        continue;
                    }
                    String day = parseDay(item.optString("day", ""));
                    String pkg = item.optString("pkg", "").trim();
                    long ms = item.optLong("ms", 0L);
                    if (day == null || pkg.isEmpty() || pkg.length() > 300
                            || ms <= 0L || ms > MAX_PKG_MS) {
                        continue;
                    }
                    packageRows.add(new UsageDb.PkgRow(day, pkg, ms));
                    days.add(day);
                }
            }

            JSONArray hours = root.optJSONArray("hours");
            if (hours != null) {
                if (hours.length() > MAX_ROWS) {
                    throw new IOException("Too many rows");
                }
                for (int i = 0; i < hours.length(); i++) {
                    JSONObject item = hours.optJSONObject(i);
                    if (item == null) {
                        continue;
                    }
                    String day = parseDay(item.optString("day", ""));
                    int hour = item.optInt("hour", -1);
                    long ms = item.optLong("ms", 0L);
                    if (day == null || hour < 0 || hour > 23
                            || ms <= 0L || ms > MAX_HOUR_MS) {
                        continue;
                    }
                    hourRows.add(new UsageDb.HourRow(day, hour, ms));
                    days.add(day);
                }
            }

            int importedDays = db.importRows(packageRows, hourRows);
            return new ImportResult(importedDays, packageRows.size(), hourRows.size());
        } catch (JSONException | IllegalArgumentException error) {
            throw new IOException("Malformed backup", error);
        }
    }

    private static String parseDay(String value) {
        try {
            LocalDate date = LocalDate.parse(value);
            if (date.getYear() < 2000 || date.getYear() > LocalDate.now().getYear() + 1) {
                return null;
            }
            return date.toString();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static byte[] readLimited(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > limit) {
                throw new IOException("File too large");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }
}
