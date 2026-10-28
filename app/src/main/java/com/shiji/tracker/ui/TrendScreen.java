package com.shiji.tracker.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.shiji.tracker.R;
import com.shiji.tracker.model.UsageModel.DayPoint;
import com.shiji.tracker.model.UsageModel.RangeStats;
import com.shiji.tracker.model.UsageModel.Snapshot;

/** One-screen daily trend with a fixed chart area and small companion metrics. */
public final class TrendScreen extends LinearLayout {

    private static final int RANGE_YEAR = 2;

    private final Context context;
    private final Theme theme;
    private final Snapshot snapshot;
    private final ScreenHost host;
    private final boolean hasPermission;
    private final FrameLayout contentArea;
    private final YearPickerView yearPicker;
    private int range;
    private int year;

    public TrendScreen(Context context, Theme theme, Snapshot snapshot, boolean hasPermission,
                       ScreenHost host, int initialRange, int initialYear) {
        super(context);
        this.context = context;
        this.theme = theme;
        this.snapshot = snapshot;
        this.hasPermission = hasPermission;
        this.host = host;
        this.range = Math.max(0, Math.min(RANGE_YEAR, initialRange));
        this.year = resolveYear(initialYear);
        setOrientation(VERTICAL);
        setPadding(Ui.dp(context, 20f), Ui.dp(context, 2f),
                Ui.dp(context, 20f), Ui.dp(context, 8f));

        String[] labels = {
                context.getString(R.string.range_week),
                context.getString(R.string.range_month),
                context.getString(R.string.range_year)
        };
        SegmentedControl segmented = new SegmentedControl(context, theme, labels, range,
                index -> {
                    range = index;
                    host.setTrendRange(index);
                    syncYearPicker();
                    renderRange();
                });
        addView(segmented, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 38f)));

        yearPicker = new YearPickerView(context, theme, snapshot.availableYears, year,
                snapshot.availableYears.size() > 1, selected -> {
                    year = selected;
                    host.setSelectedYear(selected);
                    renderRange();
                });
        LinearLayout.LayoutParams pickerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 34f));
        pickerParams.topMargin = Ui.dp(context, 8f);
        addView(yearPicker, pickerParams);

        contentArea = new FrameLayout(context);
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        contentParams.topMargin = Ui.dp(context, 2f);
        addView(contentArea, contentParams);

        syncYearPicker();
        renderRange();
    }

    private int resolveYear(int candidate) {
        if (candidate == -1) {
            return snapshot.availableYears.size() > 1 ? -1 : snapshot.defaultYear;
        }
        if (candidate > 0 && snapshot.hasYear(candidate)) {
            return candidate;
        }
        return snapshot.defaultYear;
    }

    private RangeStats stats() {
        if (range == RANGE_YEAR) {
            return year == -1 ? snapshot.all : snapshot.yearOrEmpty(year);
        }
        if (range == 1) {
            return snapshot.month;
        }
        return snapshot.week;
    }

    private void syncYearPicker() {
        boolean show = range == RANGE_YEAR && !snapshot.availableYears.isEmpty();
        yearPicker.setVisibility(show ? VISIBLE : GONE);
        if (show) {
            yearPicker.setSelectedYear(year);
        }
    }

    private void renderRange() {
        contentArea.removeAllViews();
        RangeStats stats = stats();
        if (stats.totalMs <= 0L) {
            contentArea.addView(StateViews.empty(context, theme, !hasPermission,
                    host::refresh, host::openUsageAccess), new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            contentArea.setAlpha(0f);
            contentArea.animate().alpha(1f).setDuration(180L).start();
            return;
        }

        LinearLayout page = new LinearLayout(context);
        page.setOrientation(VERTICAL);
        contentArea.addView(page, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        FrameLayout card = new FrameLayout(context);
        card.setBackground(Ui.surface(context, theme, 25f));
        card.setClipToOutline(true);
        int pad = Ui.dp(context, 4f);
        card.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        cardParams.topMargin = Ui.dp(context, 16f);
        page.addView(card, cardParams);

        TrendChartView chart = new TrendChartView(context, theme);
        chart.setData(stats.days, range > 0);
        chart.setContentDescription(context.getString(R.string.cd_chart_trend) + "，"
                + context.getString(R.string.label_total) + " " + Ui.durationSpoken(stats.totalMs));
        chart.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        card.addView(chart, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout metrics = new LinearLayout(context);
        metrics.setOrientation(HORIZONTAL);
        metrics.setGravity(Gravity.CENTER_VERTICAL);
        metrics.setBackground(Ui.surface(context, theme, 20f));
        metrics.setClipToOutline(true);
        LinearLayout.LayoutParams metricParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 68f));
        metricParams.topMargin = Ui.dp(context, 12f);
        page.addView(metrics, metricParams);

        long average = stats.totalMs / Math.max(1, stats.dayCount);
        long max = 0L;
        for (DayPoint point : stats.days) {
            max = Math.max(max, point.ms);
        }
        addMetric(metrics, context.getString(R.string.label_average), Ui.duration(average));
        addDivider(metrics);
        addMetric(metrics, context.getString(R.string.label_maximum), Ui.duration(max));
        addDivider(metrics);
        addMetric(metrics, context.getString(R.string.label_total), Ui.duration(stats.totalMs));

        contentArea.setAlpha(0f);
        contentArea.animate().alpha(1f).setDuration(200L).start();
    }

    private void addMetric(LinearLayout parent, String label, String value) {
        LinearLayout block = new LinearLayout(context);
        block.setOrientation(VERTICAL);
        block.setGravity(Gravity.CENTER);
        TextView valueView = Ui.number(context, value, 15f, theme.text);
        valueView.setGravity(Gravity.CENTER);
        TextView labelView = Ui.caption(context, label, theme.secondary);
        labelView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = Ui.dp(context, 3f);
        block.addView(valueView);
        block.addView(labelView, labelParams);
        parent.addView(block, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));
    }

    private void addDivider(LinearLayout parent) {
        View divider = new View(context);
        divider.setBackgroundColor(theme.divider);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                Math.max(1, Ui.dp(context, 0.7f)), Ui.dp(context, 24f));
        params.gravity = Gravity.CENTER_VERTICAL;
        parent.addView(divider, params);
    }
}
