package com.shiji.tracker.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.shiji.tracker.R;

import java.util.ArrayList;
import java.util.List;

/** Compact horizontal year chips used by the one-year ranges. */
public final class YearPickerView extends HorizontalScrollView {

    public interface Listener {
        void onYearSelected(int year);
    }

    private final Context context;
    private final Theme theme;
    private final LinearLayout row;
    private final List<TextView> chips = new ArrayList<>();
    private final List<Integer> values = new ArrayList<>();
    private final Listener listener;
    private int selectedYear;

    public YearPickerView(Context context, Theme theme, List<Integer> years,
                          int selectedYear, boolean includeAll, Listener listener) {
        super(context);
        this.context = context;
        this.theme = theme;
        this.listener = listener;
        setHorizontalScrollBarEnabled(false);
        setFillViewport(false);
        setClipToPadding(false);
        setOverScrollMode(View.OVER_SCROLL_NEVER);

        row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        addView(row, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));

        for (Integer year : years) {
            addChip(String.valueOf(year), year);
        }
        if (includeAll) {
            addChip(context.getString(R.string.year_all), -1);
        }
        setSelectedYear(selectedYear);
    }

    private void addChip(String label, int value) {
        final int year = value;
        TextView chip = Ui.text(context, label, 11f, theme.secondary, true);
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setIncludeFontPadding(false);
        chip.setPadding(Ui.dp(context, 9f), 0, Ui.dp(context, 9f), 0);
        chip.setClickable(true);
        chip.setFocusable(true);
        chip.setContentDescription(label);
        chip.setOnClickListener(v -> {
            if (selectedYear == year) {
                return;
            }
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            setSelectedYear(year);
            if (listener != null) {
                listener.onYearSelected(year);
            }
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, Ui.dp(context, 30f));
        params.rightMargin = Ui.dp(context, 6f);
        row.addView(chip, params);
        chips.add(chip);
        values.add(value);
    }

    public void setSelectedYear(int year) {
        selectedYear = year;
        for (int i = 0; i < chips.size(); i++) {
            TextView chip = chips.get(i);
            boolean active = values.get(i) == year;
            chip.setSelected(active);
            chip.setTextColor(active ? theme.onAccent : theme.secondary);
            chip.setTypeface(Typeface.create(active ? "sans-serif-medium" : "sans-serif",
                    Typeface.NORMAL));
            chip.setBackground(active
                    ? Ui.fill(context, theme.accent, 15f)
                    : Ui.outlined(context, theme.field, theme.stroke, 15f));
        }
    }
}
