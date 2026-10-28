package com.shiji.tracker.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Permission/empty-state mark: a small clock inside two slow orbit arcs. */
public final class OrbitBadgeView extends View {

    private final Theme theme;
    private final boolean animated;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private float angle;
    private ValueAnimator animator;

    public OrbitBadgeView(Context context, Theme theme, boolean animated) {
        super(context);
        this.theme = theme;
        this.animated = animated;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!animated || animator != null) {
            return;
        }
        animator = ValueAnimator.ofFloat(0f, 360f);
        animator.setDuration(7000L);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            angle = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
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
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.min(cx, cy) - Ui.dpf(getContext(), 3f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(Ui.dpf(getContext(), 1.2f));
        paint.setColor(theme.stroke);
        oval.set(cx - radius, cy - radius, cx + radius, cy + radius);
        canvas.drawArc(oval, 0f, 360f, false, paint);

        float orbitRadius = radius - Ui.dpf(getContext(), 2f);
        oval.set(cx - orbitRadius, cy - orbitRadius, cx + orbitRadius, cy + orbitRadius);
        paint.setStrokeWidth(Ui.dpf(getContext(), 2.2f));
        paint.setColor(theme.accent);
        canvas.drawArc(oval, angle, 64f, false, paint);
        paint.setColor(theme.accent2);
        canvas.drawArc(oval, angle + 176f, 28f, false, paint);

        float inner = radius * 0.58f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(theme.surfaceSolid);
        canvas.drawCircle(cx, cy, inner, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Ui.dpf(getContext(), 1f));
        paint.setColor(theme.stroke);
        canvas.drawCircle(cx, cy, inner, paint);

        float clockRadius = inner * 0.5f;
        paint.setColor(theme.accent);
        paint.setStrokeWidth(Ui.dpf(getContext(), 1.8f));
        canvas.drawCircle(cx, cy, clockRadius, paint);
        paint.setColor(theme.text);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(Ui.dpf(getContext(), 2f));
        canvas.drawLine(cx, cy, cx, cy - clockRadius * 0.56f, paint);
        canvas.drawLine(cx, cy, cx + clockRadius * 0.42f, cy + clockRadius * 0.22f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(theme.accent2);
        canvas.drawCircle(cx, cy, Ui.dpf(getContext(), 1.8f), paint);
    }
}
