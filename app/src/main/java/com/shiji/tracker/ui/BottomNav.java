package com.shiji.tracker.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Floating four-tab navigation, built with standard accessible click targets. */
public final class BottomNav extends FrameLayout {

    public interface OnTabSelectedListener {
        void onTabSelected(int index);
    }

    private final Theme theme;
    private final int[] icons;
    private final String[] labels;
    private final NavItem[] items;
    private int selected;

    public BottomNav(Context context, Theme theme, int initialSelected,
                     OnTabSelectedListener listener) {
        super(context);
        this.theme = theme;
        this.selected = initialSelected;
        this.labels = new String[]{
                context.getString(com.shiji.tracker.R.string.tab_today),
                context.getString(com.shiji.tracker.R.string.tab_rank),
                context.getString(com.shiji.tracker.R.string.tab_hours),
                context.getString(com.shiji.tracker.R.string.tab_trend)
        };
        this.icons = new int[]{
                com.shiji.tracker.R.drawable.ic_calendar,
                com.shiji.tracker.R.drawable.ic_rank,
                com.shiji.tracker.R.drawable.ic_clock,
                com.shiji.tracker.R.drawable.ic_trend
        };
        this.items = new NavItem[4];

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setPadding(Ui.dp(context, 5f), Ui.dp(context, 5f),
                Ui.dp(context, 5f), Ui.dp(context, 5f));
        card.setBackground(Ui.surface(context, theme, 25f));
        Ui.clipRounded(card, 25f);
        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, Ui.dp(context, 58f));
        cardParams.gravity = Gravity.BOTTOM;
        cardParams.leftMargin = Ui.dp(context, 16f);
        cardParams.rightMargin = Ui.dp(context, 16f);
        cardParams.bottomMargin = Ui.dp(context, 7f);
        cardParams.topMargin = Ui.dp(context, 5f);
        addView(card, cardParams);

        for (int i = 0; i < items.length; i++) {
            final int index = i;
            NavItem item = new NavItem(context, i);
            item.setOnClickListener(v -> {
                if (selected == index) {
                    return;
                }
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                listener.onTabSelected(index);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    LayoutParams.MATCH_PARENT, 1f);
            card.addView(item, lp);
            items[i] = item;
        }
        setSelectedTab(initialSelected);
    }

    public void setSelectedTab(int index) {
        selected = index;
        for (int i = 0; i < items.length; i++) {
            items[i].setActive(i == index);
        }
    }

    private final class NavItem extends FrameLayout {
        private final View pill;
        private final ImageView icon;
        private final TextView label;

        NavItem(Context context, int index) {
            super(context);
            setClickable(true);
            setFocusable(true);
            setContentDescription(labels[index]);
            setBackground(null);

            pill = new View(context);
            pill.setBackground(Ui.fill(context, theme.accentSoft, 16f));
            FrameLayout.LayoutParams pillParams = new FrameLayout.LayoutParams(
                    Ui.dp(context, 58f), Ui.dp(context, 42f));
            pillParams.gravity = Gravity.CENTER;
            addView(pill, pillParams);

            LinearLayout content = new LinearLayout(context);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setGravity(Gravity.CENTER);
            content.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            FrameLayout.LayoutParams contentParams = new FrameLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            contentParams.gravity = Gravity.CENTER;
            addView(content, contentParams);

            icon = new ImageView(context);
            icon.setImageResource(icons[index]);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                    Ui.dp(context, 20f), Ui.dp(context, 20f));
            iconParams.bottomMargin = Ui.dp(context, 2f);
            content.addView(icon, iconParams);

            label = new TextView(context);
            label.setText(labels[index]);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
            label.setIncludeFontPadding(false);
            label.setSingleLine(true);
            label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            label.setLetterSpacing(0.02f);
            label.setGravity(Gravity.CENTER);
            label.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            content.addView(label, new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        }

        void setActive(boolean active) {
            setSelected(active);
            int color = active ? theme.accent : theme.tertiary;
            icon.setImageTintList(ColorStateList.valueOf(color));
            label.setTextColor(color);
            label.setTypeface(Typeface.create(active ? "sans-serif-medium" : "sans-serif",
                    Typeface.NORMAL));
            pill.animate().cancel();
            pill.animate().alpha(active ? 1f : 0f)
                    .scaleX(active ? 1f : 0.9f)
                    .scaleY(active ? 1f : 0.9f)
                    .setDuration(220L).start();
            icon.animate().scaleX(active ? 1.06f : 1f)
                    .scaleY(active ? 1.06f : 1f).setDuration(220L).start();
        }
    }
}
