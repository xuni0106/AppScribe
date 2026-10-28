package com.shiji.tracker.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Programmatic view helpers and formatting for the minimal visual language. */
public final class Ui {

    private static final String[] WEEKDAYS = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
    private static final String[] WEEKDAY_CHARS = {"一", "二", "三", "四", "五", "六", "日"};

    private Ui() {
    }

    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static float dpf(Context context, float value) {
        return value * context.getResources().getDisplayMetrics().density;
    }

    public static TextView text(Context context, CharSequence value, float sp, int color) {
        return text(context, value, sp, color, false);
    }

    public static TextView text(Context context, CharSequence value, float sp, int color,
                                boolean medium) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTypeface(Typeface.create(medium ? "sans-serif-medium" : "sans-serif",
                Typeface.NORMAL));
        view.setIncludeFontPadding(false);
        view.setLineSpacing(0f, 1.08f);
        return view;
    }

    public static TextView number(Context context, CharSequence value, float sp, int color) {
        TextView view = text(context, value, sp, color, false);
        view.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        view.setLetterSpacing(-0.015f);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            view.setFontFeatureSettings("tnum");
        }
        return view;
    }

    public static TextView caption(Context context, CharSequence value, int color) {
        TextView view = text(context, value, 10f, color, false);
        view.setLetterSpacing(0.05f);
        return view;
    }

    public static TextView sectionLabel(Context context, CharSequence value, int color) {
        TextView view = text(context, value, 10.5f, color, true);
        view.setLetterSpacing(0.07f);
        return view;
    }

    public static ImageView icon(Context context, int resId, int color, int sizeDp) {
        ImageView image = new ImageView(context);
        image.setImageResource(resId);
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        image.setImageTintList(ColorStateList.valueOf(color));
        image.setLayoutParams(new LinearLayout.LayoutParams(dp(context, sizeDp),
                dp(context, sizeDp)));
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return image;
    }

    public static ImageView touchIcon(Context context, Theme theme, int resId,
                                      int color, String description, View.OnClickListener click) {
        ImageView image = new ImageView(context);
        image.setImageResource(resId);
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        image.setImageTintList(ColorStateList.valueOf(color));
        int pad = dp(context, 12f);
        image.setPadding(pad, pad, pad, pad);
        image.setBackground(rippleCircle(context, theme, dp(context, 24f)));
        image.setContentDescription(description);
        image.setClickable(true);
        image.setFocusable(true);
        image.setOnClickListener(click);
        image.setLayoutParams(new LinearLayout.LayoutParams(dp(context, 48f), dp(context, 48f)));
        pressScale(image);
        return image;
    }

    public static GradientDrawable surface(Context context, Theme theme, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{theme.surfaceTop, theme.surfaceBottom});
        drawable.setCornerRadius(dpf(context, radiusDp));
        drawable.setStroke(Math.max(1, dp(context, 0.75f)), theme.stroke);
        return drawable;
    }

    public static GradientDrawable fill(Context context, int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dpf(context, radiusDp));
        return drawable;
    }

    public static GradientDrawable outlined(Context context, int fill, int stroke,
                                            float radiusDp) {
        GradientDrawable drawable = fill(context, fill, radiusDp);
        drawable.setStroke(Math.max(1, dp(context, 0.8f)), stroke);
        return drawable;
    }

    public static RippleDrawable ripple(View content, Theme theme, float radiusDp) {
        GradientDrawable mask = fill(content.getContext(), Color.WHITE, radiusDp);
        return new RippleDrawable(ColorStateList.valueOf(theme.ripple), content.getBackground(),
                mask);
    }

    public static RippleDrawable rippleCircle(Context context, Theme theme, float sizeDp) {
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.OVAL);
        mask.setColor(Color.WHITE);
        return new RippleDrawable(ColorStateList.valueOf(theme.ripple), null, mask);
    }

    public static void pressScale(final View view) {
        view.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(110L).start();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.animate().scaleX(1f).scaleY(1f).setDuration(160L).start();
                        break;
                    default:
                        break;
                }
                return false;
            }
        });
    }

    public static TextView button(Context context, Theme theme, String label, boolean primary,
                                  View.OnClickListener click) {
        TextView button = text(context, label, 13f,
                primary ? theme.onAccent : theme.text, true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(context, 24f), 0, dp(context, 24f), 0);
        button.setMinWidth(dp(context, 132f));
        button.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(context, 48f)));
        GradientDrawable content = primary
                ? fill(context, theme.accent, 23f)
                : outlined(context, Color.TRANSPARENT, theme.stroke, 23f);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(theme.ripple),
                content, fill(context, Color.WHITE, 23f)));
        button.setClickable(true);
        button.setFocusable(true);
        button.setOnClickListener(click);
        pressScale(button);
        return button;
    }

    public static View divider(Context context, Theme theme) {
        View divider = new View(context);
        divider.setBackgroundColor(theme.divider);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(context, 0.7f))));
        return divider;
    }

    public static void margins(ViewGroup.LayoutParams params, int left, int top, int right,
                               int bottom) {
        if (params instanceof ViewGroup.MarginLayoutParams) {
            ((ViewGroup.MarginLayoutParams) params).setMargins(left, top, right, bottom);
        }
    }

    public static void clipRounded(View view, final float radiusDp) {
        final Context context = view.getContext();
        view.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dpf(context, radiusDp));
            }
        });
        view.setClipToOutline(true);
    }

    public static int alpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    public static String duration(long ms) {
        if (ms <= 0L) {
            return "0m";
        }
        long totalMinutes = ms / 60_000L;
        if (totalMinutes == 0L) {
            return "<1m";
        }
        long hours = totalMinutes / 60L;
        long minutes = totalMinutes % 60L;
        if (hours == 0L) {
            return minutes + "m";
        }
        if (minutes == 0L) {
            return hours + "h";
        }
        return hours + "h " + minutes + "m";
    }

    public static String durationCompact(long ms) {
        if (ms <= 0L) {
            return "0m";
        }
        long totalMinutes = ms / 60_000L;
        if (totalMinutes == 0L) {
            return "<1m";
        }
        long hours = totalMinutes / 60L;
        long minutes = totalMinutes % 60L;
        return hours > 0L ? hours + "h" : minutes + "m";
    }

    public static String durationSpoken(long ms) {
        if (ms <= 0L) {
            return "0 分钟";
        }
        long totalMinutes = ms / 60_000L;
        if (totalMinutes == 0L) {
            return "不到 1 分钟";
        }
        long hours = totalMinutes / 60L;
        long minutes = totalMinutes % 60L;
        if (hours == 0L) {
            return minutes + " 分钟";
        }
        if (minutes == 0L) {
            return hours + " 小时";
        }
        return hours + " 小时 " + minutes + " 分钟";
    }

    public static String hourLabel(int hour) {
        return String.format(Locale.CHINA, "%02d:00", hour);
    }

    public static String dateLine(LocalDate date) {
        return String.format(Locale.CHINA, "%d月%d日 %s", date.getMonthValue(),
                date.getDayOfMonth(), weekday(date));
    }

    public static String shortDate(LocalDate date) {
        return String.format(Locale.CHINA, "%d/%d", date.getMonthValue(), date.getDayOfMonth());
    }

    public static String dateWithYear(LocalDate date) {
        return String.format(Locale.CHINA, "%d/%d/%d", date.getYear(),
                date.getMonthValue(), date.getDayOfMonth());
    }

    /** Date shown on its own: today's year stays compact, older years keep the year. */
    public static String dateLabel(LocalDate date) {
        if (date.getYear() == LocalDate.now().getYear()) {
            return monthDay(date);
        }
        return dateWithYear(date);
    }

    /** Actual first/last data date for a range summary. */
    public static String dateRange(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            return "";
        }
        if (start.equals(end)) {
            return dateLabel(start);
        }
        if (start.getYear() == end.getYear()
                && start.getYear() == LocalDate.now().getYear()) {
            return shortDate(start) + "  —  " + shortDate(end);
        }
        return dateWithYear(start) + "  —  " + dateWithYear(end);
    }

    /** Compact chart axis date; the year is added when the series crosses a year. */
    public static String chartDate(LocalDate date, boolean withYear) {
        if (!withYear) {
            return shortDate(date);
        }
        return String.format(Locale.CHINA, "%02d/%d/%d", date.getYear() % 100,
                date.getMonthValue(), date.getDayOfMonth());
    }

    public static String weekday(LocalDate date) {
        return WEEKDAYS[date.getDayOfWeek().getValue() - 1];
    }

    public static String weekdayChar(LocalDate date) {
        return WEEKDAY_CHARS[date.getDayOfWeek().getValue() - 1];
    }

    public static String monthDay(LocalDate date) {
        return DateTimeFormatter.ofPattern("M月d日", Locale.CHINA).format(date);
    }

    public static String percent(long part, long total) {
        if (total <= 0L) {
            return "0%";
        }
        return Math.round(part * 100d / total) + "%";
    }

    public static void setTopMargin(View view, int marginPx) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params instanceof ViewGroup.MarginLayoutParams) {
            ((ViewGroup.MarginLayoutParams) params).topMargin = marginPx;
        }
    }

    public static View spacer(Context context, int heightDp) {
        View view = new View(context);
        view.setLayoutParams(new LinearLayout.LayoutParams(1, dp(context, heightDp)));
        return view;
    }
}
