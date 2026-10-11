package ps.reso.instaeclipse.utils.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class EclipseRingDrawable extends Drawable {

    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint corePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float glowPx;
    private int ringColor;
    private boolean glow;
    private Drawable logo;

    public EclipseRingDrawable(int coreColor, int ringColor, float ringWidthPx, float glowPx, boolean glow) {
        this.glowPx = glowPx;
        this.ringColor = ringColor;
        this.glow = glow;
        corePaint.setColor(coreColor);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(ringWidthPx);
        ringPaint.setColor(ringColor);
    }

    public EclipseRingDrawable setLogo(Drawable logo) {
        this.logo = logo == null ? null : logo.mutate();
        tintLogo();
        invalidateSelf();
        return this;
    }

    public void setState(int ringColor, boolean glow) {
        this.ringColor = ringColor;
        this.glow = glow;
        ringPaint.setColor(ringColor);
        tintLogo();
        updateGlow(getBounds());
        invalidateSelf();
    }

    private void tintLogo() {
        if (logo != null) logo.setColorFilter(new PorterDuffColorFilter(ringColor, PorterDuff.Mode.SRC_IN));
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        updateGlow(bounds);
    }

    private void updateGlow(Rect bounds) {
        float outer = Math.min(bounds.width(), bounds.height()) / 2f;
        if (outer <= 0f) return;
        float inner = Math.max(1f, outer - glowPx);
        int base = ringColor & 0x00FFFFFF;
        glowPaint.setShader(new RadialGradient(bounds.exactCenterX(), bounds.exactCenterY(), outer,
                new int[]{0x73000000 | base, 0x73000000 | base, 0x2E000000 | base, base},
                new float[]{0f, inner / outer, inner / outer + (1f - inner / outer) * 0.45f, 1f},
                Shader.TileMode.CLAMP));
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect b = getBounds();
        float cx = b.exactCenterX();
        float cy = b.exactCenterY();
        float outer = Math.min(b.width(), b.height()) / 2f;
        float inner = Math.max(1f, outer - glowPx);
        if (glow) canvas.drawCircle(cx, cy, outer, glowPaint);
        canvas.drawCircle(cx, cy, inner, corePaint);
        canvas.drawCircle(cx, cy, inner - ringPaint.getStrokeWidth() / 2f, ringPaint);
        if (logo != null) {
            int half = Math.round(inner * 0.8f);
            logo.setBounds(Math.round(cx) - half, Math.round(cy) - half, Math.round(cx) + half, Math.round(cy) + half);
            logo.draw(canvas);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        corePaint.setAlpha(alpha);
        ringPaint.setAlpha(alpha);
        glowPaint.setAlpha(alpha);
        if (logo != null) logo.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        ringPaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
