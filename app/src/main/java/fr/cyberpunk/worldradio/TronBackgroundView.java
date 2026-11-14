package fr.cyberpunk.worldradio;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

/** Fond « Tron » : grille cyan fine + ligne de balayage lente. */
public class TronBackgroundView extends View {

    private final Paint gridPaint = new Paint();
    private final Paint glowPaint = new Paint();
    private final Paint scanPaint = new Paint();

    private float scanY = 0f;
    private long last = 0;
    private boolean running = true;

    public TronBackgroundView(Context c) { this(c, null); }

    public TronBackgroundView(Context c, AttributeSet a) {
        super(c, a);
        gridPaint.setColor(0x3300E5FF);
        gridPaint.setStrokeWidth(1f);
        glowPaint.setColor(0x5500E5FF);
        glowPaint.setStrokeWidth(3f);
        scanPaint.setColor(0x8800E5FF);
        scanPaint.setStrokeWidth(2f);
    }

    public void setRunning(boolean r) {
        running = r;
        if (r) postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = SystemClock.uptimeMillis();
        float dt = last == 0 ? 0 : Math.min(now - last, 50) / 16f;
        last = now;

        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        canvas.drawColor(0xFF000000);

        // grille verticale
        int step = Math.max(44, w / 10);
        for (int x = 0; x <= w; x += step) {
            canvas.drawLine(x, 0, x, h, gridPaint);
        }
        // sol en perspective (lignes qui convergent vers un point de fuite)
        float horizon = h * 0.62f;
        float vpx = w / 2f;
        for (int i = -8; i <= 8; i++) {
            float bottomX = vpx + i * step;
            canvas.drawLine(vpx, horizon, bottomX, h, gridPaint);
        }
        for (int k = 1; k <= 6; k++) {
            float t = k / 6f;
            float y = horizon + (h - horizon) * t * t;
            canvas.drawLine(0, y, w, y, gridPaint);
        }
        // ligne d'horizon lumineuse
        glowPaint.setAlpha(90);
        canvas.drawLine(0, horizon, w, horizon, glowPaint);
        glowPaint.setAlpha(255);

        scanY += dt * 3f;
        if (scanY > h + 20) scanY = -20;
        canvas.drawLine(0, scanY, w, scanY, glowPaint);
        canvas.drawLine(0, scanY, w, scanY, scanPaint);

        if (running) postInvalidateOnAnimation();
    }
}
