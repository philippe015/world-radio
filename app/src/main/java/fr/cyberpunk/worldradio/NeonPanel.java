package fr.cyberpunk.worldradio;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

/** Conteneur avec bordure néon floutée (châssis Tron). */
public class NeonPanel extends LinearLayout {

    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    public NeonPanel(Context c) { this(c, null); }

    public NeonPanel(Context c, AttributeSet a) {
        super(c, a);
        setWillNotDraw(false);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        glow.setStyle(Paint.Style.STROKE);
        glow.setStrokeWidth(dp(3));
        glow.setColor(0xAA00E5FF);
        glow.setMaskFilter(new BlurMaskFilter(dp(10), BlurMaskFilter.Blur.NORMAL));
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(dp(1.5f));
        border.setColor(0xFF00E5FF);
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }

    @Override
    protected void onDraw(Canvas canvas) {
        float inset = dp(10);
        float r = dp(10);
        rect.set(inset, inset, getWidth() - inset, getHeight() - inset);
        canvas.drawRoundRect(rect, r, r, glow);
        canvas.drawRoundRect(rect, r, r, border);
    }
}
