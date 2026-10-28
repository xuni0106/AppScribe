package com.shiji.tracker.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.shiji.tracker.model.UsageModel.AppUsage;

import java.util.Locale;

/** Rounded app icon with a colored monogram fallback for imported packages. */
public final class AppIconView extends FrameLayout {

    public AppIconView(Context context, Theme theme, AppUsage app, int sizeDp) {
        super(context);
        int size = Ui.dp(context, sizeDp);
        setBackground(Ui.fill(context, theme.field, sizeDp * 0.3f));
        Ui.clipRounded(this, sizeDp * 0.3f);
        if (app != null && app.icon != null) {
            ImageView image = new ImageView(context);
            image.setImageDrawable(app.icon);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int pad = Ui.dp(context, 1.5f);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
            params.setMargins(pad, pad, pad, pad);
            image.setLayoutParams(params);
            image.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(image);
        } else {
            String initial = "?";
            if (app != null) {
                String label = app.label == null || app.label.isEmpty() ? app.pkg : app.label;
                if (label != null && !label.isEmpty()) {
                    initial = label.substring(0, 1).toUpperCase(Locale.getDefault());
                }
            }
            TextView letter = new TextView(context);
            letter.setText(initial);
            letter.setTextColor(app == null ? theme.secondary
                    : theme.colorForIndex(Math.abs(app.pkg.hashCode())));
            letter.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeDp * 0.4f);
            letter.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            letter.setGravity(Gravity.CENTER);
            letter.setIncludeFontPadding(false);
            letter.setLayoutParams(new FrameLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            letter.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(letter);
        }
    }
}
