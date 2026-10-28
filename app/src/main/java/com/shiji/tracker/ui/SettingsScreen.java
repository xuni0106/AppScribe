package com.shiji.tracker.ui;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.shiji.tracker.R;

/** Single-screen settings: permission, theme and local backup actions. */
public final class SettingsScreen extends LinearLayout {

    private final Context context;
    private final Theme theme;

    public SettingsScreen(Context context, Theme theme, int themeMode, boolean hasPermission,
                          ScreenHost host) {
        super(context);
        this.context = context;
        this.theme = theme;
        setOrientation(VERTICAL);
        setPadding(Ui.dp(context, 20f), Ui.dp(context, 4f),
                Ui.dp(context, 20f), Ui.dp(context, 16f));

        addView(permissionCard(hasPermission, host), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 66f)));

        addSectionLabel(R.string.section_appearance);
        String[] themeLabels = {
                context.getString(R.string.theme_system),
                context.getString(R.string.theme_light),
                context.getString(R.string.theme_dark)
        };
        SegmentedControl themes = new SegmentedControl(context, theme, themeLabels,
                Math.max(0, Math.min(2, themeMode)), host::setThemeMode);
        LinearLayout.LayoutParams themeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 38f));
        themeParams.topMargin = Ui.dp(context, 8f);
        addView(themes, themeParams);

        addSectionLabel(R.string.section_backup);

        LinearLayout backup = new LinearLayout(context);
        backup.setOrientation(VERTICAL);
        backup.setBackground(Ui.surface(context, theme, 23f));
        backup.setClipToOutline(true);
        LinearLayout.LayoutParams backupParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        backupParams.topMargin = Ui.dp(context, 8f);
        addView(backup, backupParams);

        View export = actionRow(context, theme, R.drawable.ic_export,
                context.getString(R.string.action_export), host::exportData);
        backup.addView(export, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 56f)));
        View divider = new View(context);
        divider.setBackgroundColor(theme.divider);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(context, 0.7f)));
        dividerParams.leftMargin = Ui.dp(context, 60f);
        backup.addView(divider, dividerParams);
        View importRow = actionRow(context, theme, R.drawable.ic_import,
                context.getString(R.string.action_import), host::importData);
        backup.addView(importRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 56f)));

        View spacer = new View(context);
        addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));

        TextView version = Ui.caption(context, versionLabel(), theme.tertiary);
        version.setGravity(Gravity.CENTER);
        addView(version, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void addSectionLabel(int resId) {
        TextView label = Ui.sectionLabel(context, context.getString(resId), theme.secondary);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = Ui.dp(context, 22f);
        addView(label, params);
    }

    private View permissionCard(boolean hasPermission, ScreenHost host) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(Ui.dp(context, 14f), 0, Ui.dp(context, 14f), 0);
        GradientDrawable bg = Ui.surface(context, theme, 23f);
        card.setBackground(new RippleDrawable(ColorStateList.valueOf(theme.ripple), bg,
                Ui.fill(context, Color.WHITE, 23f)));
        card.setClipToOutline(true);
        card.setClickable(true);
        card.setFocusable(true);
        card.setContentDescription(context.getString(R.string.access_usage) + "，"
                + context.getString(hasPermission ? R.string.status_on : R.string.status_off));
        card.setOnClickListener(v -> host.openUsageAccess());
        Ui.pressScale(card);

        FrameLayout iconHolder = new FrameLayout(context);
        iconHolder.setBackground(Ui.fill(context, theme.accentSoft, 12f));
        ImageView icon = Ui.icon(context, R.drawable.ic_access, theme.accent, 18);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                Ui.dp(context, 18f), Ui.dp(context, 18f));
        iconParams.gravity = Gravity.CENTER;
        iconHolder.addView(icon, iconParams);
        card.addView(iconHolder, new LinearLayout.LayoutParams(
                Ui.dp(context, 36f), Ui.dp(context, 36f)));

        TextView label = Ui.text(context, context.getString(R.string.access_usage), 13f,
                theme.text, true);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelParams.leftMargin = Ui.dp(context, 12f);
        card.addView(label, labelParams);

        TextView status = Ui.text(context,
                context.getString(hasPermission ? R.string.status_on : R.string.status_off),
                10f, hasPermission ? theme.accent2 : theme.accent, true);
        status.setSingleLine(true);
        card.addView(status);

        ImageView chevron = Ui.icon(context, R.drawable.ic_chevron, theme.tertiary, 16);
        LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(
                Ui.dp(context, 16f), Ui.dp(context, 16f));
        chevronParams.leftMargin = Ui.dp(context, 5f);
        card.addView(chevron, chevronParams);
        return card;
    }

    private View actionRow(Context context, Theme theme, int iconRes, String label,
                           Runnable action) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(context, 14f), 0, Ui.dp(context, 14f), 0);
        row.setBackground(new RippleDrawable(ColorStateList.valueOf(theme.ripple), null,
                Ui.fill(context, Color.WHITE, 1f)));
        row.setClickable(true);
        row.setFocusable(true);
        row.setContentDescription(label);
        row.setOnClickListener(v -> action.run());
        Ui.pressScale(row);

        FrameLayout iconHolder = new FrameLayout(context);
        iconHolder.setBackground(Ui.fill(context, theme.field, 10f));
        ImageView icon = Ui.icon(context, iconRes, theme.accent, 17);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                Ui.dp(context, 17f), Ui.dp(context, 17f));
        iconParams.gravity = Gravity.CENTER;
        iconHolder.addView(icon, iconParams);
        row.addView(iconHolder, new LinearLayout.LayoutParams(
                Ui.dp(context, 32f), Ui.dp(context, 32f)));

        TextView labelView = Ui.text(context, label, 13f, theme.text, true);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelParams.leftMargin = Ui.dp(context, 12f);
        row.addView(labelView, labelParams);

        ImageView chevron = Ui.icon(context, R.drawable.ic_chevron, theme.tertiary, 16);
        LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(
                Ui.dp(context, 16f), Ui.dp(context, 16f));
        chevronParams.leftMargin = Ui.dp(context, 5f);
        row.addView(chevron, chevronParams);
        return row;
    }

    private String versionLabel() {
        try {
            PackageInfo info = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            long code = android.os.Build.VERSION.SDK_INT >= 28
                    ? info.getLongVersionCode() : info.versionCode;
            return context.getString(R.string.version_format, info.versionName, code);
        } catch (Throwable ignored) {
            return "v1.0.0 · 1";
        }
    }
}
