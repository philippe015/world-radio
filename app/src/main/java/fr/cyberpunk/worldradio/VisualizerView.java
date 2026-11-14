package fr.cyberpunk.worldradio;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

/**
 * Analyseur de spectre.
 * - mode classique : vert → jaune → rouge, crêtes blanches (Winamp 2.x)
 * - mode néon : cyan → indigo → magenta, halo doux
 */
public class VisualizerView extends View {

    private static final int BARS = 44;

    private final float[] values = new float[BARS];
    private final float[] peaks = new float[BARS];
    private final RectF rect = new RectF();

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint peakPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private boolean classic = false;
    private boolean simulate = true;
    private long lastFrame = 0;
    private long start = SystemClock.uptimeMillis();

    public VisualizerView(Context c) { super(c); init(); }
    public VisualizerView(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        barPaint.setStyle(Paint.Style.FILL);
        peakPaint.setStyle(Paint.Style.FILL);
        glowPaint.setStyle(Paint.Style.FILL);
        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setColor(0xFF101010);
    }

    public void setClassic(boolean c) {
        classic = c;
        applyPaints();
        invalidate();
    }

    public void setSimulate(boolean s) { simulate = s; }

    public void setBands(float[] data) {
        if (data == null) return;
        simulate = false;
        int n = Math.min(BARS, data.length);
        for (int i = 0; i < BARS; i++) values[i] = i < n ? clamp(data[i]) : 0f;
        postInvalidateOnAnimation();
    }

    public void useSimulation() {
        simulate = true;
        postInvalidateOnAnimation();
    }

    private static float clamp(float v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }

    private void applyPaints() {
        int h = getHeight() > 0 ? getHeight() : 200;
        if (classic) {
            barPaint.setShader(new LinearGradient(0, h, 0, 0,
                    new int[]{0xFF00C853, 0xFFAEEA00, 0xFFFFD600, 0xFFFF3B30},
                    new float[]{0f, 0.55f, 0.78f, 1f}, Shader.TileMode.CLAMP));
            peakPaint.setColor(0xFFE8E8E8);
            glowPaint.setColor(0x00000000);
        } else {
            barPaint.setShader(new LinearGradient(0, h, 0, 0,
                    new int[]{0xFF22D3EE, 0xFF6366F1, 0xFFE879F9},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            peakPaint.setColor(0xFFE879F9);
            glowPaint.setColor(0x3322D3EE);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        applyPaints();
    }

    private void updateSimulation() {
        long t = SystemClock.uptimeMillis() - start;
        for (int i = 0; i < BARS; i++) {
            double phase = t * 0.0016 + i * 0.22;
            double base = 0.5 + 0.42 * Math.sin(phase);
            double wobble = 0.18 * Math.sin(phase * 2.3 + i * 0.7);
            double noise = (Math.random() - 0.5) * 0.12;
            double falloff = 1.0 - (i / (double) BARS) * 0.35;
            values[i] = clamp((float) ((base + wobble + noise) * falloff));
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = SystemClock.uptimeMillis();
        float dt = lastFrame == 0 ? 16f : Math.min(now - lastFrame, 60f);
        lastFrame = now;

        if (simulate) updateSimulation();

        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        if (classic) {
            canvas.drawRect(0, 0, w, h, bgPaint);
        }

        float slot = w / (float) BARS;
        float bw = slot * (classic ? 0.66f : 0.62f);
        float radius = classic ? 0f : Math.min(bw / 2f, 6f);
        float peakDecay = dt / (classic ? 200f : 260f);

        for (int i = 0; i < BARS; i++) {
            float v = clamp(values[i]);
            float bh = Math.max(h * 0.02f, h * v);
            float x = i * slot + (slot - bw) / 2f;
            float top = h - bh;

            if (!classic) {
                // halo : barre élargie semi-transparente, puis barre nette
                rect.set(x - 2f, top - 2f, x + bw + 2f, h);
                glowPaint.setAlpha(70);
                canvas.drawRoundRect(rect, radius + 3, radius + 3, glowPaint);
                rect.set(x - 1f, top - 1f, x + bw + 1f, h);
                glowPaint.setAlpha(130);
                canvas.drawRoundRect(rect, radius + 1, radius + 1, glowPaint);
            }

            rect.set(x, top, x + bw, h);
            canvas.drawRoundRect(rect, radius, radius, barPaint);

            peaks[i] = Math.max(peaks[i] - peakDecay, v);
            float py = h - h * peaks[i];
            rect.set(x, py - (classic ? 2f : 3f), x + bw, py);
            canvas.drawRect(rect, peakPaint);
        }

        if (simulate) postInvalidateOnAnimation();
    }
}
