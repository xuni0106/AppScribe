package com.shiji.tracker.ui;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.shiji.tracker.R;
import com.shiji.tracker.model.UsageModel.AppUsage;
import com.shiji.tracker.model.UsageModel.Snapshot;

/** Today overview. It is deliberately not scrollable and always fits one screen. */
public final class TodayScreen extends FrameLayout {

    public TodayScreen(Context context, Theme theme, Snapshot snapshot, boolean hasPermission,
                       boolean loading, ScreenHost host) {
        super(context);
        if (snapshot == null) {
            if (loading && hasPermission) {
                addView(StateViews.loading(context, theme), match());
            } else {
                addView(StateViews.permission(context, theme, host::openUsageAccess), match());
            }
            return;
        }
        boolean permissionState = !hasPermission && snapshot.day.totalMs <= 0L;
        if (permissionState) {
            addView(StateViews.permission(context, theme, host::openUsageAccess), match());
            return;
        }
        if (snapshot.day.totalMs <= 0L) {
            addView(StateViews.empty(context, theme, !hasPermission, host::refresh,
                    host::openUsageAccess), match());
            return;
        }
        addView(buildDashboard(context, theme, snapshot, host), match());
    }

    private LayoutParams match() {
        return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    }

    private View buildDashboard(Context context, Theme theme, Snapshot snapshot,
                                ScreenHost host) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(context, 20f), Ui.dp(context, 2f),
                Ui.dp(context, 20f), Ui.dp(context, 6f));

        root.addView(buildHero(context, theme, snapshot),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        Ui.dp(context, 164f)));

        TextView section = Ui.sectionLabel(context,
                context.getString(R.string.label_apps), theme.secondary);
        LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sectionParams.topMargin = Ui.dp(context, 14f);
        sectionParams.bottomMargin = Ui.dp(context, 8f);
        root.addView(section, sectionParams);

        LinearLayout appsCard = new LinearLayout(context);
        appsCard.setOrientation(LinearLayout.VERTICAL);
        appsCard.setBackground(Ui.surface(context, theme, 24f));
        appsCard.setClipToOutline(true);
        appsCard.setMinimumHeight(Ui.dp(context, 144f));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(appsCard, cardParams);

        int visible = Math.min(3, snapshot.day.apps.size());
        for (int i = 0; i < 3; i++) {
            if (i > 0) {
                View divider = new View(context);
                divider.setBackgroundColor(theme.divider);
                appsCard.addView(divider, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(context, 0.7f))));
            }
            if (i < visible) {
                AppUsage app = snapshot.day.apps.get(i);
                View row = buildAppRow(context, theme, snapshot, app, i);
                appsCard.addView(row, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            } else {
                View placeholder = new View(context);
                placeholder.setVisibility(View.INVISIBLE);
                appsCard.addView(placeholder, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            }
        }
        return root;
    }

    private View buildHero(Context context, Theme theme, Snapshot snapshot) {
        LinearLayout hero = new LinearLayout(context);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(Ui.dp(context, 16f), Ui.dp(context, 16f),
                Ui.dp(context, 16f), Ui.dp(context, 16f));
        hero.setBackground(Ui.surface(context, theme, 26f));
        hero.setClipToOutline(true);

        FrameLayout ringWrap = new FrameLayout(context);
        RingChartView ring = new RingChartView(context, theme);
        ring.setData(snapshot.day.apps);
        ringWrap.addView(ring, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout center = new LinearLayout(context);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);
        TextView total = Ui.number(context, Ui.duration(snapshot.day.totalMs), 17.5f,
                theme.text);
        total.setGravity(Gravity.CENTER);
        total.setSingleLine(true);
        TextView caption = Ui.caption(context,
                context.getString(R.string.label_today), theme.tertiary);
        caption.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams totalParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        LinearLayout.LayoutParams captionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        captionParams.topMargin = Ui.dp(context, 3f);
        center.addView(total, totalParams);
        center.addView(caption, captionParams);
        FrameLayout.LayoutParams centerParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        centerParams.gravity = Gravity.CENTER;
        ringWrap.addView(center, centerParams);
        ringWrap.setContentDescription(context.getString(R.string.cd_ring) + "，"
                + Ui.durationSpoken(snapshot.day.totalMs));

        LinearLayout.LayoutParams ringParams = new LinearLayout.LayoutParams(
                Ui.dp(context, 124f), Ui.dp(context, 124f));
        ringParams.gravity = Gravity.CENTER_VERTICAL;
        hero.addView(ringWrap, ringParams);

        LinearLayout stats = new LinearLayout(context);
        stats.setOrientation(LinearLayout.VERTICAL);
        stats.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams statsParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        statsParams.leftMargin = Ui.dp(context, 14f);
        hero.addView(stats, statsParams);

        int peak = snapshot.day.peakHour();
        AppUsage top = snapshot.day.top();
        addStat(context, theme, stats, context.getString(R.string.label_apps),
                String.valueOf(snapshot.day.apps.size()), false);
        addStat(context, theme, stats, context.getString(R.string.label_peak),
                peak < 0 ? "—" : Ui.hourLabel(peak), false);
        addStat(context, theme, stats, context.getString(R.string.label_longest),
                top == null ? "—" : Ui.duration(top.ms), true);
        return hero;
    }

    private void addStat(Context context, Theme theme, LinearLayout parent, String label,
                         String value, boolean last) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView labelView = Ui.text(context, label, 9.5f, theme.secondary);
        labelView.setLetterSpacing(0.06f);
        labelView.setSingleLine(true);
        row.addView(labelView, new LinearLayout.LayoutParams(
                Ui.dp(context, 43f), ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView valueView = Ui.text(context, value, 13f, theme.text, true);
        valueView.setSingleLine(true);
        valueView.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(valueView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (!last) {
            params.bottomMargin = Ui.dp(context, 11f);
        }
        parent.addView(row, params);
    }

    private View buildAppRow(Context context, Theme theme, Snapshot snapshot, AppUsage app,
                             int index) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(context, 16f), Ui.dp(context, 8f),
                Ui.dp(context, 16f), Ui.dp(context, 8f));
        row.setContentDescription(app.label + "，" + Ui.durationSpoken(app.ms) + "，"
                + Ui.percent(app.ms, snapshot.day.totalMs));

        AppIconView icon = new AppIconView(context, theme, app, 34);
        row.addView(icon, new LinearLayout.LayoutParams(
                Ui.dp(context, 34f), Ui.dp(context, 34f)));

        LinearLayout center = new LinearLayout(context);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams centerParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        centerParams.leftMargin = Ui.dp(context, 12f);
        centerParams.rightMargin = Ui.dp(context, 12f);
        row.addView(center, centerParams);

        TextView name = Ui.text(context, app.label, 13f, theme.text, true);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        center.addView(name, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        FractionBarView bar = new FractionBarView(context, theme.track,
                theme.colorForIndex(index));
        bar.setFraction(app.ms / (float) Math.max(1L, snapshot.day.totalMs),
                theme.colorForIndex(index));
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 3f));
        barParams.topMargin = Ui.dp(context, 8f);
        center.addView(bar, barParams);

        TextView duration = Ui.text(context, Ui.duration(app.ms), 12f,
                theme.secondary, true);
        duration.setSingleLine(true);
        duration.setFontFeatureSettings("tnum");
        row.addView(duration);
        return row;
    }
}
