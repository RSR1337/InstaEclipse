package ps.reso.instaeclipse.ui.theme;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class GradientSlider extends View {

    public interface OnProgressChangeListener {
        void onProgressChanged(float progress);
    }

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint checkerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF trackRect = new RectF();
    private final Path clipPath = new Path();

    private int startColor = Color.BLACK;
    private int endColor = Color.WHITE;
    private float progress = 1.0f;
    private boolean showChecker;
    private OnProgressChangeListener listener;

    public GradientSlider(Context context) {
        super(context);
        init();
    }

    public GradientSlider(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        checkerPaint.setColor(0xFFCCCCCC);
        thumbFillPaint.setStyle(Paint.Style.FILL);
        thumbStrokePaint.setStyle(Paint.Style.STROKE);
        thumbStrokePaint.setStrokeWidth(dp(3.0f));
        thumbStrokePaint.setColor(Color.WHITE);
    }

    public void setColors(int startColor, int endColor) {
        this.startColor = startColor;
        this.endColor = endColor;
        showChecker = Color.alpha(startColor) < 255 || Color.alpha(endColor) < 255;
        updateShader();
        invalidate();
    }

    public void setProgress(float progress) {
        this.progress = Math.max(0.0f, Math.min(1.0f, progress));
        invalidate();
    }

    public float getProgress() {
        return progress;
    }

    public void setOnProgressChangeListener(OnProgressChangeListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float trackHeight = dp(16.0f);
        float top = (h - trackHeight) / 2.0f;
        trackRect.set(dp(11.0f), top, w - dp(11.0f), top + trackHeight);
        clipPath.reset();
        clipPath.addRoundRect(trackRect, trackHeight / 2.0f, trackHeight / 2.0f, Path.Direction.CW);
        updateShader();
    }

    private void updateShader() {
        if (trackRect.width() <= 0.0f) return;
        trackPaint.setShader(new LinearGradient(trackRect.left, trackRect.top, trackRect.right, trackRect.top,
                startColor, endColor, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (trackRect.width() <= 0.0f) return;

        if (showChecker) {
            canvas.save();
            canvas.clipPath(clipPath);
            float cell = dp(8.0f);
            boolean toggle = false;
            for (float x = trackRect.left; x < trackRect.right; x += cell) {
                boolean rowToggle = toggle;
                for (float y = trackRect.top; y < trackRect.bottom; y += cell) {
                    if (rowToggle) canvas.drawRect(x, y, x + cell, y + cell, checkerPaint);
                    rowToggle = !rowToggle;
                }
                toggle = !toggle;
            }
            canvas.restore();
        }

        float radius = trackRect.height() / 2.0f;
        canvas.drawRoundRect(trackRect, radius, radius, trackPaint);

        float thumbX = trackRect.left + (progress * trackRect.width());
        float thumbY = trackRect.centerY();
        int thumbColor = mixColor(startColor, endColor, progress);
        thumbFillPaint.setColor(thumbColor);
        canvas.drawCircle(thumbX, thumbY, dp(13.0f), thumbFillPaint);
        canvas.drawCircle(thumbX, thumbY, dp(13.0f), thumbStrokePaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getAction();
        if (action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_MOVE) {
            return super.onTouchEvent(event);
        }
        if (trackRect.width() <= 0.0f) return super.onTouchEvent(event);
        float x = event.getX();
        progress = Math.max(0.0f, Math.min(1.0f, (x - trackRect.left) / trackRect.width()));
        if (listener != null) listener.onProgressChanged(progress);
        invalidate();
        return true;
    }

    private static int mixColor(int a, int b, float t) {
        return Color.argb(
                Math.round(Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * t),
                Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    private float dp(float v) {
        return getResources().getDisplayMetrics().density * v;
    }
}
