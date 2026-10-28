package com.shiji.tracker.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import com.shiji.tracker.model.UsageModel.AppUsage;

import java.util.ArrayList;
import java.util.List;

/** Animated multi-application donut. Center content is supplied by the caller. */
public final class RingChartView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final Theme theme;
    private final List<Long> values = new ArrayList<>();
    private final List<Integer> colors = new ArrayList<>();
    private long total;
    private float progress;
    private ValueAnimator animator;

    public RingChartView(Context context, Theme theme) {
        super(context);
        this.theme = theme;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setData(List<AppUsage> apps) {
        values.clear();
        colors.clear();
        total = 0L;
        long top = 0L;
        int limit = Math.min(5, apps.size());
        for (int i = 0; i < limit; i++) {
            AppUsage app = apps.get(i);
            values.add(app.ms);
            colors.add(theme.colorForIndex(i));
            total += app.ms;
            top += app.ms;
        }
        if (apps.size() > limit) {
            long rest = 0L;
            for (int i = limit; i < apps.size(); i++) {
                rest += apps.get(i).ms;
            }
            if (rest > 0L) {
                values.add(rest);
                colors.add(theme.colorForIndex(5));
                total += rest;
            }
        }
        animateRing();
    }

    private void animateRing() {
        if (animator != null) {
            animator.cancel();
        }
        progress = 0f;
        if (!isAttachedToWindow()) {
            invalidate();
            return;
        }
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(820L);
        animator.setInterpolator(new DecelerateInterpolator(1.8f));
        animator.addUpdateListener(animation -> {
            progress = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (values.isEmpty()) {
            progress = 1f;
        } else if (animator == null) {
            animateRing();
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
        float stroke = Ui.dpf(getContext(), 12f);
        float inset = stroke * 1.6f;
        bounds.set(inset, inset, getWidth() - inset, getHeight() - inset);

        paint.setStrokeWidth(stroke);
        paint.setColor(theme.track);
        canvas.drawArc(bounds, 0f, 360f, false, paint);

        if (total <= 0L || values.isEmpty()) {
            return;
        }

        float gap = values.size() > 1 ? Math.max(2.2f, 18f / values.size()) : 0f;
        float cursor = -90f;
        for (int i = 0; i < values.size(); i++) {
            float sweep = 360f * values.get(i) / total;
            float shown = Math.max(0f, (sweep - gap) * progress);
            int color = colors.get(i);
            if (shown > 0.2f) {
                paint.setColor(Ui.alpha(color, 55));
                paint.setStrokeWidth(stroke + Ui.dpf(getContext(), 3.5f));
                canvas.drawArc(bounds, cursor + gap / 2f, shown, false, paint);
                paint.setColor(color);
                paint.setStrokeWidth(stroke);
                canvas.drawArc(bounds, cursor + gap / 2f, shown, false, paint);
            }
            cursor += sweep;
        }
    }
}
