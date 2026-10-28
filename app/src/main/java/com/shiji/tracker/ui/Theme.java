package com.shiji.tracker.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

/**
 * A deliberately small color system shared by every programmatic view.
 */
public final class Theme {

    public static final String PREFS = "shiji_prefs";
    public static final String KEY_THEME = "theme_mode";
    public static final int MODE_SYSTEM = 0;
    public static final int MODE_LIGHT = 1;
    public static final int MODE_DARK = 2;

    public final boolean dark;
    public final int background;
    public final int surfaceTop;
    public final int surfaceBottom;
    public final int surfaceSolid;
    public final int field;
    public final int stroke;
    public final int divider;
    public final int text;
    public final int secondary;
    public final int tertiary;
    public final int accent;
    public final int accent2;
    public final int accent3;
    public final int onAccent;
    public final int accentSoft;
    public final int track;
    public final int ripple;
    public final int glowA;
    public final int glowB;
    public final int[] chart;

    private Theme(boolean dark, int background, int surfaceTop, int surfaceBottom,
                  int surfaceSolid, int field, int stroke, int divider, int text,
                  int secondary, int tertiary, int accent, int accent2, int accent3,
                  int onAccent, int accentSoft, int track, int ripple, int glowA,
                  int glowB, int[] chart) {
        this.dark = dark;
        this.background = background;
        this.surfaceTop = surfaceTop;
        this.surfaceBottom = surfaceBottom;
        this.surfaceSolid = surfaceSolid;
        this.field = field;
        this.stroke = stroke;
        this.divider = divider;
        this.text = text;
        this.secondary = secondary;
        this.tertiary = tertiary;
        this.accent = accent;
        this.accent2 = accent2;
        this.accent3 = accent3;
        this.onAccent = onAccent;
        this.accentSoft = accentSoft;
        this.track = track;
        this.ripple = ripple;
        this.glowA = glowA;
        this.glowB = glowB;
        this.chart = chart;
    }

    public static Theme resolve(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int mode = prefs.getInt(KEY_THEME, MODE_SYSTEM);
        int night = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        boolean dark = mode == MODE_DARK
                || (mode == MODE_SYSTEM && night == Configuration.UI_MODE_NIGHT_YES);
        return dark ? dark() : light();
    }

    public static int readMode(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_THEME, MODE_SYSTEM);
    }

    public static void writeMode(Context context, int mode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putInt(KEY_THEME, mode).apply();
    }

    private static Theme dark() {
        return new Theme(true,
                0xFF07090E,
                0xF7141822,
                0xF70D1017,
                0xFF141822,
                0xFF1B202B,
                0x26FFFFFF,
                0x1AFFFFFF,
                0xFFF5F3ED,
                0xFFA8ACB8,
                0xFF858C99,
                0xFFF2B56B,
                0xFF6FD9C4,
                0xFF9E8CFF,
                0xFF171107,
                0x2EF2B56B,
                0xFF25303C,
                0x32FFFFFF,
                0x3B3B235F,
                0x2EF2B56B,
                new int[]{
                        0xFFF2B56B, 0xFF6FD9C4, 0xFF9E8CFF,
                        0xFFF08BA8, 0xFF8BB8FF, 0xFFC9DB7A
                });
    }

    private static Theme light() {
        return new Theme(false,
                0xFFF4F1E9,
                0xF9FFFEFA,
                0xF9FFFAF1,
                0xFFFFFFFF,
                0xFFECE6DA,
                0x1C2B2419,
                0x142B2419,
                0xFF1C1A16,
                0xFF69645A,
                0xFF726A5D,
                0xFFA4530E,
                0xFF0E746B,
                0xFF5B4B9E,
                0xFFFFFFFF,
                0x22A4530E,
                0xFFE4DED1,
                0x1F2B2419,
                0x4DF0C49A,
                0x3D9BD8C6,
                new int[]{
                        0xFFB4651C, 0xFF0E746B, 0xFF5B4B9E,
                        0xFFA33A5B, 0xFF2F5DA8, 0xFF5C6F20
                });
    }

    public int colorForIndex(int index) {
        return chart[Math.floorMod(index, chart.length)];
    }
}
