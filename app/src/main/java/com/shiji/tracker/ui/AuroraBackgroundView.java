package com.shiji.tracker.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/** Static, GPU-friendly backdrop: two glows and a faint time orbit. */
public final class AuroraBackgroundView extends View {

    private final Theme theme;
    private final Paint base = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint orbit = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path orbitPath = new Path();

    public AuroraBackgroundView(Context context, Theme theme) {
        super(context);
        this.theme = theme;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        base.setColor(theme.background);
        orbit.setStyle(Paint.Style.STROKE);
        orbit.setStrokeWidth(Ui.dpf(context, 0.7f));
        orbit.setColor(Ui.alpha(theme.dark ? theme.text : theme.text, 15));
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        float radius = Math.max(width, height) * 0.62f;
        glow.setShader(new RadialGradient(width * 0.92f, height * 0.055f, radius,
                new int[]{theme.glowA, 0x00000000}, new float[]{0f, 1f},
                Shader.TileMode.CLAMP));
        orbitPath.reset();
        orbitPath.moveTo(-width * 0.18f, height * 0.22f);
        orbitPath.cubicTo(width * 0.16f, height * 0.08f,
                width * 0.62f, height * 0.54f, width * 1.16f, height * 0.28f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawRect(0, 0, getWidth(), getHeight(), base);

        glow.setShader(new RadialGradient(getWidth() * 0.92f, getHeight() * 0.055f,
                Math.max(getWidth(), getHeight()) * 0.62f,
                new int[]{theme.glowA, 0x00000000}, new float[]{0f, 1f},
                Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, getWidth(), getHeight(), glow);

        glow.setShader(new RadialGradient(getWidth() * 0.08f, getHeight() * 0.88f,
                Math.max(getWidth(), getHeight()) * 0.58f,
                new int[]{theme.glowB, 0x00000000}, new float[]{0f, 1f},
                Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, getWidth(), getHeight(), glow);

        canvas.drawPath(orbitPath, orbit);

        // Fine horizon wash keeps the lower half quiet around the navigation bar.
        Paint wash = base;
        wash.setShader(new LinearGradient(0, getHeight() * 0.72f, 0, getHeight(),
                new int[]{0x00000000, Ui.alpha(theme.background, 225)},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, getHeight() * 0.72f, getWidth(), getHeight(), wash);
        wash.setShader(null);
    }
}
