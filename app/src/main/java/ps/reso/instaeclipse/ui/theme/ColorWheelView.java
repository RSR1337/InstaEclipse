package ps.reso.instaeclipse.ui.theme;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class ColorWheelView extends View {

    public interface OnHueSaturationChangeListener {
        void onHueSaturationChanged(float hue, float saturation);
    }

    private static final int[] HUE_SWEEP = {
            Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED
    };

    private final Paint wheelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float centerX;
    private float centerY;
    private float wheelRadius;
    private float hue = 0.0f;
    private float saturation = 0.0f;
    private OnHueSaturationChangeListener listener;

    public ColorWheelView(Context context) {
        super(context);
        init();
    }

    public ColorWheelView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        thumbPaint.setStyle(Paint.Style.STROKE);
        thumbPaint.setStrokeWidth(dp(3.0f));
        thumbPaint.setColor(Color.WHITE);
    }

    public void setHueSaturation(float h, float s) {
        hue = h;
        saturation = Math.max(0.0f, Math.min(1.0f, s));
        invalidate();
    }

    public float getHue() {
        return hue;
    }

    public float getSaturation() {
        return saturation;
    }

    public void setOnHueSaturationChangeListener(OnHueSaturationChangeListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerX = w / 2.0f;
        centerY = h / 2.0f;
        wheelRadius = Math.min(w, h) / 2.0f - dp(12.0f);
        if (wheelRadius <= 0.0f) return;
        Shader hueShader = new SweepGradient(centerX, centerY, HUE_SWEEP, null);
        Shader whiteoutShader = new RadialGradient(centerX, centerY, wheelRadius, Color.WHITE, Color.TRANSPARENT, Shader.TileMode.CLAMP);
        wheelPaint.setShader(new ComposeShader(hueShader, whiteoutShader, PorterDuff.Mode.SRC_OVER));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (wheelRadius <= 0.0f) return;
        canvas.drawCircle(centerX, centerY, wheelRadius, wheelPaint);

        double angleRad = Math.toRadians(hue);
        float r = saturation * wheelRadius;
        float thumbX = centerX + (float) (r * Math.cos(angleRad));
        float thumbY = centerY + (float) (r * Math.sin(angleRad));

        thumbPaint.setStyle(Paint.Style.FILL);
        thumbPaint.setColor(Color.HSVToColor(new float[]{hue, saturation, 1.0f}));
        canvas.drawCircle(thumbX, thumbY, dp(11.0f), thumbPaint);
        thumbPaint.setStyle(Paint.Style.STROKE);
        thumbPaint.setColor(Color.WHITE);
        canvas.drawCircle(thumbX, thumbY, dp(11.0f), thumbPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (wheelRadius <= 0.0f) return super.onTouchEvent(event);
        int action = event.getAction();
        if (action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_MOVE) {
            return super.onTouchEvent(event);
        }
        float dx = event.getX() - centerX;
        float dy = event.getY() - centerY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        float clampedDistance = Math.min(distance, wheelRadius);

        double angleRad = Math.atan2(dy, dx);
        float angleDeg = (float) Math.toDegrees(angleRad);
        if (angleDeg < 0.0f) angleDeg += 360.0f;

        hue = angleDeg;
        saturation = clampedDistance / wheelRadius;
        notifyChanged();
        invalidate();
        getParent().requestDisallowInterceptTouchEvent(true);
        return true;
    }

    private void notifyChanged() {
        if (listener != null) listener.onHueSaturationChanged(hue, saturation);
    }

    private float dp(float v) {
        return getResources().getDisplayMetrics().density * v;
    }
}
