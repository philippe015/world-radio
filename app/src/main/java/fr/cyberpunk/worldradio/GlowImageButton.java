package fr.cyberpunk.worldradio;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageButton;

/** Bouton icône avec halo néon. */
public class GlowImageButton extends ImageButton {

    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private int accent = 0xFF00E5FF;
    private boolean filled = false;

    public GlowImageButton(Context c) { this(c, null); }

    public GlowImageButton(Context c, AttributeSet a) {
        super(c, a);
        setWillNotDraw(false);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        glow.setStyle(Paint.Style.STROKE);
        glow.setStrokeWidth(dp(2));
        glow.setColor(0x9900E5FF);
        glow.setMaskFilter(new BlurMaskFilter(dp(8), BlurMaskFilter.Blur.NORMAL));
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(0xFF03090B);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(dp(1.4f));
        border.setColor(accent);
        setBackground(null);
    }

    public void setFilled(boolean f) { filled = f; invalidate(); }

    public void setAccent(int color) {
        accent = color;
        border.setColor(color);
        glow.setColor((color & 0x00FFFFFF) | 0x99000000);
        invalidate();
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }

    @Override
    protected void onDraw(Canvas canvas) {
        float r = dp(4);
        rect.set(dp(6), dp(6), getWidth() - dp(6), getHeight() - dp(6));
        canvas.drawRoundRect(rect, r, r, glow);
        fill.setColor(isPressed() ? 0xFF0A3038 : (filled ? 0xFF063038 : 0xFF03090B));
        canvas.drawRoundRect(rect, r, r, fill);
        canvas.drawRoundRect(rect, r, r, border);
        super.onDraw(canvas);
    }
}
