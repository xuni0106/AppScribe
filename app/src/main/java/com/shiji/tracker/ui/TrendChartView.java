package com.shiji.tracker.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import com.shiji.tracker.model.UsageModel.DayPoint;

import java.util.ArrayList;
import java.util.List;

/** A soft Catmull-Rom line with a gradient area, revealed left to right. */
public final class TrendChartView extends View {

    private final Theme theme;
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axis = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path linePath = new Path();
    private final Path fillPath = new Path();
    private List<DayPoint> points = new ArrayList<>();
    private boolean longSeries;
    private float reveal;
    private ValueAnimator animator;

    public TrendChartView(Context context, Theme theme) {
        super(context);
        this.theme = theme;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(Ui.dpf(context, 2.4f));
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setColor(theme.accent);
        fill.setStyle(Paint.Style.FILL);
        grid.setColor(theme.divider);
        dot.setStyle(Paint.Style.FILL);
        axis.setColor(theme.tertiary);
        axis.setTextSize(Ui.dpf(context, 9.2f));
        axis.setTypeface(android.graphics.Typeface.create("sans-serif-medium",
                android.graphics.Typeface.NORMAL));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setData(List<DayPoint> source, boolean longSeries) {
        points = source == null ? new ArrayList<>() : new ArrayList<>(source);
        this.longSeries = longSeries;
        reveal = 0f;
        maximumComputed = false;
        if (animator != null) {
            animator.cancel();
        }
        if (!isAttachedToWindow()) {
            invalidate();
            return;
        }
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(900L);
        animator.setInterpolator(new DecelerateInterpolator(1.6f));
        animator.addUpdateListener(animation -> {
            reveal = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (animator == null && reveal == 0f && !points.isEmpty()) {
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(860L);
            animator.setInterpolator(new DecelerateInterpolator(1.6f));
            animator.addUpdateListener(animation -> {
                reveal = (float) animation.getAnimatedValue();
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
        if (points.size() < 2) {
            return;
        }
        float left = Ui.dpf(getContext(), 12f);
        float right = getWidth() - Ui.dpf(getContext(), 12f);
        float top = Ui.dpf(getContext(), 22f);
        float bottom = getHeight() - Ui.dpf(getContext(), 30f);
        float width = Math.max(1f, right - left);
        float height = Math.max(1f, bottom - top);

        long max = 1L;
        for (DayPoint point : points) {
            max = Math.max(max, point.ms);
        }
        float headroom = max * 1.14f;
        int count = points.size();
        float[] xs = new float[count];
        float[] ys = new float[count];
        for (int i = 0; i < count; i++) {
            xs[i] = left + width * i / (count - 1f);
            ys[i] = bottom - height * (points.get(i).ms / headroom);
        }

        for (int i = 1; i <= 3; i++) {
            float y = bottom - height * i / 4f;
            canvas.drawRect(left, y, right, y + Ui.dpf(getContext(), 0.65f), grid);
        }
        grid.setColor(Ui.alpha(theme.stroke, 70));
        canvas.drawRect(left, bottom, right, bottom + Ui.dpf(getContext(), 0.8f), grid);
        grid.setColor(theme.divider);

        buildPaths(xs, ys, bottom);

        float revealX = left + width * reveal;
        int save = canvas.save();
        canvas.clipRect(0f, 0f, revealX + Ui.dpf(getContext(), 8f), getHeight());
        canvas.drawPath(fillPath, fill);
        canvas.drawPath(linePath, line);
        for (int i = 0; i < count; i++) {
            if (xs[i] > revealX) {
                continue;
            }
            if (longSeries) {
                if (!isMaximum(i) && i != count - 1) {
                    continue;
                }
            }
            dot.setColor(Ui.alpha(theme.accent, 45));
            float radius = isMaximum(i) ? Ui.dpf(getContext(), 8f) : Ui.dpf(getContext(), 5f);
            canvas.drawCircle(xs[i], ys[i], radius, dot);
            dot.setColor(isMaximum(i) ? theme.accent : theme.accent2);
            canvas.drawCircle(xs[i], ys[i],
                    isMaximum(i) ? Ui.dpf(getContext(), 3.4f) : Ui.dpf(getContext(), 2.2f), dot);
        }
        canvas.restoreToCount(save);

        drawAxis(canvas, xs, left, right);
    }

    private boolean maximumComputed;
    private int maximumIndex;

    private boolean isMaximum(int index) {
        if (!maximumComputed) {
            long max = -1L;
            int best = 0;
            for (int i = 0; i < points.size(); i++) {
                if (points.get(i).ms > max) {
                    max = points.get(i).ms;
                    best = i;
                }
            }
            maximumIndex = best;
            maximumComputed = true;
        }
        return index == maximumIndex && points.get(index).ms > 0L;
    }

    private void buildPaths(float[] xs, float[] ys, float bottom) {
        linePath.reset();
        fillPath.reset();
        int count = xs.length;
        linePath.moveTo(xs[0], ys[0]);
        for (int i = 0; i < count - 1; i++) {
            float p0x = xs[i == 0 ? 0 : i - 1];
            float p0y = ys[i == 0 ? 0 : i - 1];
            float p1x = xs[i];
            float p1y = ys[i];
            float p2x = xs[i + 1];
            float p2y = ys[i + 1];
            float p3x = xs[i + 2 < count ? i + 2 : count - 1];
            float p3y = ys[i + 2 < count ? i + 2 : count - 1];
            float c1x = p1x + (p2x - p0x) / 6f;
            float c1y = p1y + (p2y - p0y) / 6f;
            float c2x = p2x - (p3x - p1x) / 6f;
            float c2y = p2y - (p3y - p1y) / 6f;
            // Keep the spline inside the plot: an unclamped Catmull-Rom curve
            // can dip under the zero baseline when adjacent days are empty.
            c1y = Math.max(Ui.dpf(getContext(), 20f), Math.min(bottom, c1y));
            c2y = Math.max(Ui.dpf(getContext(), 20f), Math.min(bottom, c2y));
            linePath.cubicTo(c1x, c1y, c2x, c2y, p2x, p2y);
        }
        fillPath.addPath(linePath);
        fillPath.lineTo(xs[count - 1], bottom);
        fillPath.lineTo(xs[0], bottom);
        fillPath.close();
        fill.setShader(new LinearGradient(0f, Ui.dpf(getContext(), 18f),
                0f, bottom, new int[]{Ui.alpha(theme.accent, 70),
                Ui.alpha(theme.accent, 0)}, new float[]{0f, 1f}, Shader.TileMode.CLAMP));
    }

    private void drawAxis(Canvas canvas, float[] xs, float left, float right) {
        int count = points.size();
        if (!longSeries) {
            for (int i = 0; i < count; i++) {
                boolean today = i == count - 1;
                axis.setColor(today ? theme.accent : theme.tertiary);
                String text = Ui.weekdayChar(points.get(i).date);
                float tw = axis.measureText(text);
                float x = Math.max(left, Math.min(right - tw, xs[i] - tw / 2f));
                canvas.drawText(text, x, getHeight() - Ui.dpf(getContext(), 10f), axis);
            }
        } else {
            boolean withYear = points.get(0).date.getYear()
                    != points.get(count - 1).date.getYear();
            int[] indices = {0, (count - 1) / 4, (count - 1) / 2,
                    (count - 1) * 3 / 4, count - 1};
            int previous = -1;
            for (int index : indices) {
                if (index < 0 || index >= count || index == previous) {
                    continue;
                }
                previous = index;
                axis.setColor(index == count - 1 ? theme.accent : theme.tertiary);
                String text = Ui.chartDate(points.get(index).date, withYear);
                float tw = axis.measureText(text);
                float x = xs[index] - tw / 2f;
                if (index == 0) {
                    x = left;
                } else if (index == count - 1) {
                    x = right - tw;
                }
                canvas.drawText(text, x, getHeight() - Ui.dpf(getContext(), 10f), axis);
            }
        }
        maximumComputed = false;
    }
}
