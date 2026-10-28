package com.shiji.tracker.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.Arrays;

/** Twenty-four hourly bars with a quiet axis and an animated growth pass. */
public final class HourBarsView extends View {

    private final Theme theme;
    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint guide = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint marker = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private long[] values = new long[24];
    private int currentHour = -1;
    private float progress;
    private ValueAnimator animator;

    public HourBarsView(Context context, Theme theme) {
        super(context);
        this.theme = theme;
        bar.setStyle(Paint.Style.FILL);
        guide.setStyle(Paint.Style.FILL);
        guide.setColor(theme.divider);
        label.setColor(theme.tertiary);
        label.setTextSize(Ui.dpf(context, 9.2f));
        label.setTypeface(android.graphics.Typeface.create("sans-serif-medium",
                android.graphics.Typeface.NORMAL));
        marker.setColor(theme.accent);
        marker.setStyle(Paint.Style.FILL);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setData(long[] source, int currentHour) {
        values = source != null && source.length == 24 ? source.clone() : new long[24];
        this.currentHour = currentHour;
        if (animator != null) {
            animator.cancel();
        }
        progress = 0f;
        if (!isAttachedToWindow()) {
            invalidate();
            return;
        }
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(780L);
        animator.setInterpolator(new DecelerateInterpolator(1.7f));
        animator.addUpdateListener(animation -> {
            progress = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (animator == null && progress == 0f) {
            progress = 0f;
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(760L);
            animator.setInterpolator(new DecelerateInterpolator(1.7f));
            animator.addUpdateListener(animation -> {
                progress = (float) animation.getAnimatedValue();
                invalidate();
            });
            animator.start();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float left = Ui.dpf(getContext(), 10f);
        float right = getWidth() - Ui.dpf(getContext(), 10f);
        float top = Ui.dpf(getContext(), 24f);
        float bottom = getHeight() - Ui.dpf(getContext(), 26f);
        float chartHeight = Math.max(1f, bottom - top);
        float width = Math.max(1f, right - left);

        long max = 0L;
        int peak = -1;
        for (int i = 0; i < values.length; i++) {
            if (values[i] > max) {
                max = values[i];
                peak = i;
            }
        }

        // Three barely-there horizontal guides.
        for (int i = 1; i <= 3; i++) {
            float y = bottom - chartHeight * i / 4f;
            canvas.drawRect(left, y, right, y + Ui.dpf(getContext(), 0.7f), guide);
        }
        canvas.drawRect(left, bottom, right, bottom + Ui.dpf(getContext(), 0.8f), guide);

        float step = width / 24f;
        float barWidth = Math.min(Ui.dpf(getContext(), 9f), step * 0.54f);
        float radius = barWidth / 2f;

        if (max > 0L) {
            for (int i = 0; i < 24; i++) {
                float fraction = values[i] / (float) max;
                float h = Math.max(values[i] <= 0L ? Ui.dpf(getContext(), 2f)
                        : Ui.dpf(getContext(), 4f), chartHeight * 0.88f * fraction * progress);
                float cx = left + step * (i + 0.5f);
                float barTop = bottom - h;
                int alpha;
                if (i == peak) {
                    alpha = 255;
                } else if (i == currentHour) {
                    alpha = 180;
                } else {
                    alpha = theme.dark ? 96 : 105;
                }
                bar.setColor(Ui.alpha(theme.accent, alpha));
                rect.set(cx - barWidth / 2f, barTop, cx + barWidth / 2f, bottom);
                canvas.drawRoundRect(rect, radius, radius, bar);
            }

            if (progress > 0.75f && peak >= 0) {
                float cx = left + step * (peak + 0.5f);
                float h = chartHeight * 0.88f * (values[peak] / (float) max);
                float cy = Math.max(top + Ui.dpf(getContext(), 3f),
                        bottom - h - Ui.dpf(getContext(), 10f));
                marker.setColor(theme.accent);
                canvas.drawCircle(cx, cy, Ui.dpf(getContext(), 2.2f), marker);
                marker.setAlpha(38);
                canvas.drawCircle(cx, cy, Ui.dpf(getContext(), 6f), marker);
                marker.setAlpha(255);
            }
        } else {
            canvas.drawRect(left, bottom - Ui.dpf(getContext(), 2f), right, bottom,
                    guide);
        }

        if (currentHour >= 0 && currentHour < 24 && max > 0L) {
            float cx = left + step * (currentHour + 0.5f);
            marker.setColor(Ui.alpha(theme.accent2, 130));
            canvas.drawCircle(cx, bottom + Ui.dpf(getContext(), 4f),
                    Ui.dpf(getContext(), 1.5f), marker);
        }

        int[] marks = {0, 6, 12, 18, 23};
        for (int mark : marks) {
            float x = left + step * (mark + 0.5f);
            String text = String.valueOf(mark);
            float tw = label.measureText(text);
            float adjusted = x - tw / 2f;
            if (mark == 0) {
                adjusted = left;
            } else if (mark == 23) {
                adjusted = right - tw;
            }
            canvas.drawText(text, adjusted, getHeight() - Ui.dpf(getContext(), 8f), label);
        }
    }
}
