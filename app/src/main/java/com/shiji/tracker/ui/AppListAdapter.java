package com.shiji.tracker.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.shiji.tracker.model.UsageModel.AppUsage;

import java.util.ArrayList;
import java.util.List;

/** Recycled ranking rows: icon, name, share bar, duration and percentage. */
public final class AppListAdapter extends BaseAdapter {

    private final Context context;
    private final Theme theme;
    private final List<AppUsage> apps = new ArrayList<>();
    private long total;

    public AppListAdapter(Context context, Theme theme) {
        this.context = context;
        this.theme = theme;
    }

    public void setData(List<AppUsage> data, long total) {
        apps.clear();
        if (data != null) {
            apps.addAll(data);
        }
        this.total = Math.max(1L, total);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return apps.size();
    }

    @Override
    public Object getItem(int position) {
        return apps.get(position);
    }

    @Override
    public long getItemId(int position) {
        return apps.get(position).pkg.hashCode();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Holder holder;
        if (convertView == null) {
            holder = createHolder(parent);
            convertView = holder.root;
            convertView.setTag(holder);
        } else {
            holder = (Holder) convertView.getTag();
        }
        AppUsage app = apps.get(position);
        holder.iconHost.removeAllViews();
        AppIconView icon = new AppIconView(context, theme, app, 34);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                Ui.dp(context, 34f), Ui.dp(context, 34f));
        holder.iconHost.addView(icon, iconParams);
        holder.name.setText(app.label);
        holder.name.setContentDescription(app.label);
        holder.duration.setText(Ui.duration(app.ms));
        holder.percent.setText(Ui.percent(app.ms, total));
        holder.bar.setFraction(app.ms / (float) total, theme.colorForIndex(position));
        holder.root.setContentDescription(app.label + "，"
                + Ui.durationSpoken(app.ms) + "，" + Ui.percent(app.ms, total));
        return convertView;
    }

    private Holder createHolder(ViewGroup parent) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Ui.dp(context, 16f);
        root.setPadding(pad, 0, pad, 0);
        root.setLayoutParams(new AbsListView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 60f)));

        FrameLayout iconHost = new FrameLayout(context);
        LinearLayout.LayoutParams iconHostParams = new LinearLayout.LayoutParams(
                Ui.dp(context, 34f), Ui.dp(context, 34f));
        root.addView(iconHost, iconHostParams);

        LinearLayout center = new LinearLayout(context);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams centerParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        centerParams.leftMargin = Ui.dp(context, 12f);
        centerParams.rightMargin = Ui.dp(context, 12f);
        root.addView(center, centerParams);

        TextView name = new TextView(context);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        name.setTextColor(theme.text);
        name.setIncludeFontPadding(false);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        center.addView(name, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FractionBarView bar = new FractionBarView(context, theme.track, theme.accent);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 3f));
        barParams.topMargin = Ui.dp(context, 8f);
        center.addView(bar, barParams);

        LinearLayout right = new LinearLayout(context);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);
        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        root.addView(right, rightParams);

        TextView duration = new TextView(context);
        duration.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        duration.setTextColor(theme.text);
        duration.setIncludeFontPadding(false);
        duration.setGravity(Gravity.END);
        duration.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        duration.setFontFeatureSettings("tnum");
        duration.setSingleLine(true);
        right.addView(duration, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView percent = new TextView(context);
        percent.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f);
        percent.setTextColor(theme.tertiary);
        percent.setIncludeFontPadding(false);
        percent.setGravity(Gravity.END);
        percent.setFontFeatureSettings("tnum");
        LinearLayout.LayoutParams percentParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        percentParams.topMargin = Ui.dp(context, 3f);
        right.addView(percent, percentParams);

        return new Holder(root, iconHost, name, bar, duration, percent);
    }

    private static final class Holder {
        final LinearLayout root;
        final FrameLayout iconHost;
        final TextView name;
        final FractionBarView bar;
        final TextView duration;
        final TextView percent;

        Holder(LinearLayout root, FrameLayout iconHost, TextView name, FractionBarView bar,
               TextView duration, TextView percent) {
            this.root = root;
            this.iconHost = iconHost;
            this.name = name;
            this.bar = bar;
            this.duration = duration;
            this.percent = percent;
        }
    }
}
