package com.shiji.tracker.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.shiji.tracker.R;

/** Single-screen permission, empty and loading states without explanatory copy. */
public final class StateViews {

    private StateViews() {
    }

    public static View permission(Context context, Theme theme, Runnable authorize) {
        return state(context, theme, true,
                context.getString(R.string.access_usage),
                context.getString(R.string.action_authorize), authorize);
    }

    public static View empty(Context context, Theme theme, boolean needsPermission,
                             Runnable refresh, Runnable authorize) {
        return state(context, theme, false, context.getString(R.string.empty_data),
                context.getString(needsPermission ? R.string.action_authorize
                        : R.string.action_refresh),
                needsPermission ? authorize : refresh);
    }

    private static View state(Context context, Theme theme, boolean animated,
                              String label, String action, Runnable callback) {
        FrameLayout root = new FrameLayout(context);
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER);
        column.setPadding(Ui.dp(context, 24f), 0, Ui.dp(context, 24f), 0);
        FrameLayout.LayoutParams columnParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        columnParams.gravity = Gravity.CENTER;
        root.addView(column, columnParams);

        OrbitBadgeView badge = new OrbitBadgeView(context, theme, animated);
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                Ui.dp(context, 106f), Ui.dp(context, 106f));
        column.addView(badge, badgeParams);

        TextView title = Ui.text(context, label, 15f, theme.text, true);
        title.setGravity(Gravity.CENTER);
        title.setLetterSpacing(0.02f);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = Ui.dp(context, 20f);
        column.addView(title, titleParams);

        TextView button = Ui.button(context, theme, action, true, v -> callback.run());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                Ui.dp(context, 156f), Ui.dp(context, 48f));
        buttonParams.topMargin = Ui.dp(context, 22f);
        column.addView(button, buttonParams);
        return root;
    }

    public static View loading(Context context, Theme theme) {
        FrameLayout root = new FrameLayout(context);
        LoadingView spinner = new LoadingView(context, theme);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                Ui.dp(context, 38f), Ui.dp(context, 38f));
        params.gravity = Gravity.CENTER;
        root.addView(spinner, params);
        return root;
    }
}
