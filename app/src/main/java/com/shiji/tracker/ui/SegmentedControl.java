package com.shiji.tracker.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Accessible pill segment control with an instant, quiet selection state. */
public final class SegmentedControl extends LinearLayout {

    public interface OnSelectListener {
        void onSelect(int index);
    }

    private final Theme theme;
    private final String[] labels;
    private final TextView[] items;
    private int selected;
    private OnSelectListener listener;

    public SegmentedControl(Context context, Theme theme, String[] labels, int initialSelected,
                            OnSelectListener listener) {
        super(context);
        this.theme = theme;
        this.labels = labels;
        this.selected = initialSelected;
        this.listener = listener;
        setOrientation(HORIZONTAL);
        setPadding(Ui.dp(context, 3f), Ui.dp(context, 3f),
                Ui.dp(context, 3f), Ui.dp(context, 3f));
        GradientDrawable background = Ui.outlined(context, theme.field, theme.stroke, 18f);
        setBackground(background);
        setClipToOutline(true);
        items = new TextView[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView item = new TextView(context);
            item.setText(labels[i]);
            item.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
            item.setGravity(Gravity.CENTER);
            item.setIncludeFontPadding(false);
            item.setSingleLine(true);
            item.setContentDescription(labels[i]);
            item.setFocusable(true);
            item.setClickable(true);
            LayoutParams params = new LayoutParams(0,
                    Ui.dp(context, 32f), 1f);
            item.setLayoutParams(params);
            item.setOnClickListener(v -> {
                if (selected == index) {
                    return;
                }
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                applySelection(index, true);
            });
            Ui.pressScale(item);
            addView(item);
            items[i] = item;
        }
        applySelection(initialSelected, false);
    }

    public int getSelected() {
        return selected;
    }

    public void setSelectedSilently(int index) {
        applySelection(index, false);
    }

    private void applySelection(int index, boolean notify) {
        if (index < 0 || index >= items.length) {
            return;
        }
        selected = index;
        for (int i = 0; i < items.length; i++) {
            TextView item = items[i];
            boolean active = i == index;
            item.setSelected(active);
            item.setTextColor(active ? theme.onAccent : theme.secondary);
            item.setTypeface(Typeface.create(active ? "sans-serif-medium" : "sans-serif",
                    Typeface.NORMAL));
            if (active) {
                GradientDrawable fill = Ui.fill(getContext(), theme.accent, 14f);
                item.setBackground(fill);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    item.setStateListAnimator(null);
                }
            } else {
                item.setBackgroundColor(Color.TRANSPARENT);
            }
        }
        if (notify && listener != null) {
            listener.onSelect(index);
        }
    }
}
