package com.shiji.tracker.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.shiji.tracker.R;
import com.shiji.tracker.model.UsageModel.RangeStats;
import com.shiji.tracker.model.UsageModel.Snapshot;

/** Scrollable content list with fixed range controls and a single-screen summary. */
public final class RankingScreen extends LinearLayout {

    private static final int RANGE_YEAR = 3;

    private final Context context;
    private final Theme theme;
    private final Snapshot snapshot;
    private final ScreenHost host;
    private final boolean hasPermission;
    private final FrameLayout contentArea;
    private final YearPickerView yearPicker;
    private int range;
    private int year;

    public RankingScreen(Context context, Theme theme, Snapshot snapshot, boolean hasPermission,
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
                context.getString(R.string.range_day),
                context.getString(R.string.range_week),
                context.getString(R.string.range_month),
                context.getString(R.string.range_year)
        };
        SegmentedControl segmented = new SegmentedControl(context, theme, labels, range,
                index -> {
                    range = index;
                    host.setRankingRange(index);
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
        if (range == 2) {
            return snapshot.month;
        }
        if (range == 1) {
            return snapshot.week;
        }
        return snapshot.day;
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
        if (stats.totalMs <= 0L || stats.apps.isEmpty()) {
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

        page.addView(buildSummary(stats), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout listCard = new FrameLayout(context);
        listCard.setBackground(Ui.surface(context, theme, 24f));
        listCard.setClipToOutline(true);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        cardParams.topMargin = Ui.dp(context, 14f);
        page.addView(listCard, cardParams);

        ListView list = new ListView(context);
        list.setBackgroundColor(Color.TRANSPARENT);
        list.setCacheColorHint(Color.TRANSPARENT);
        list.setDivider(new ColorDrawable(theme.divider));
        list.setDividerHeight(Math.max(1, Ui.dp(context, 0.7f)));
        list.setSelector(new ColorDrawable(Color.TRANSPARENT));
        list.setVerticalScrollBarEnabled(false);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list.setClipToPadding(false);
        list.setPadding(0, Ui.dp(context, 5f), 0, Ui.dp(context, 5f));
        AppListAdapter adapter = new AppListAdapter(context, theme);
        adapter.setData(stats.apps, stats.totalMs);
        list.setAdapter(adapter);
        listCard.addView(list, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        contentArea.setAlpha(0f);
        contentArea.animate().alpha(1f).setDuration(200L).start();
    }

    private View buildSummary(RangeStats stats) {
        LinearLayout block = new LinearLayout(context);
        block.setOrientation(VERTICAL);
        block.setPadding(0, Ui.dp(context, 17f), 0, 0);

        TextView totalLabel = Ui.sectionLabel(context,
                context.getString(R.string.label_total), theme.secondary);
        block.addView(totalLabel);

        LinearLayout valueRow = new LinearLayout(context);
        valueRow.setOrientation(HORIZONTAL);
        valueRow.setGravity(Gravity.BOTTOM);
        LinearLayout.LayoutParams valueRowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        valueRowParams.topMargin = Ui.dp(context, 2f);
        block.addView(valueRow, valueRowParams);

        TextView total = Ui.number(context, Ui.duration(stats.totalMs), 29f, theme.text);
        total.setFontFeatureSettings("tnum");
        valueRow.addView(total, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView count = Ui.text(context,
                stats.apps.size() + " " + context.getString(R.string.label_apps),
                11f, theme.secondary, true);
        count.setGravity(Gravity.END);
        count.setPadding(0, 0, 0, Ui.dp(context, 5f));
        valueRow.addView(count, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        String rangeText = Ui.dateRange(stats.start, stats.end);
        TextView rangeView = Ui.text(context, rangeText, 9.5f, theme.tertiary);
        rangeView.setLetterSpacing(0.04f);
        LinearLayout.LayoutParams rangeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rangeParams.topMargin = Ui.dp(context, 4f);
        block.addView(rangeView, rangeParams);
        return block;
    }
}
