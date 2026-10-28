package com.shiji.tracker.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** A restrained two-arc spinner used while the first local read completes. */
public final class LoadingView extends View {

    private final Theme theme;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private float angle;
    private ValueAnimator animator;

    public LoadingView(Context context, Theme theme) {
        super(context);
        this.theme = theme;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (animator != null) {
            return;
        }
        animator = ValueAnimator.ofFloat(0f, 360f);
        animator.setDuration(1100L);
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
        float stroke = Ui.dpf(getContext(), 2.2f);
        float size = Math.min(getWidth(), getHeight()) - stroke * 3f;
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        oval.set(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f);
        paint.setStrokeWidth(stroke);
        paint.setColor(theme.track);
        canvas.drawArc(oval, 0f, 360f, false, paint);
        paint.setColor(theme.accent);
        canvas.drawArc(oval, angle, 104f, false, paint);
        paint.setColor(Ui.alpha(theme.accent2, 180));
        paint.setStrokeWidth(stroke * 0.8f);
        canvas.drawArc(oval, angle + 160f, 42f, false, paint);
    }
}
