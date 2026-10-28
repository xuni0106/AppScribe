package com.shiji.tracker.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** A 3dp usage bar that can be used inside recycled list rows. */
public final class FractionBarView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final int trackColor;
    private int fillColor;
    private float fraction;

    public FractionBarView(Context context, int trackColor, int fillColor) {
        super(context);
        this.trackColor = trackColor;
        this.fillColor = fillColor;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setFraction(float fraction, int fillColor) {
        this.fraction = Math.max(0f, Math.min(1f, fraction));
        this.fillColor = fillColor;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float h = getHeight();
        float r = h / 2f;
        paint.setColor(trackColor);
        rect.set(0f, 0f, getWidth(), h);
        canvas.drawRoundRect(rect, r, r, paint);
        if (fraction > 0f) {
            paint.setColor(fillColor);
            rect.set(0f, 0f, Math.max(h, getWidth() * fraction), h);
            canvas.drawRoundRect(rect, r, r, paint);
        }
    }
}
