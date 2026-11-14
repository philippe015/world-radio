package fr.cyberpunk.worldradio;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/** Pluie de caractères « Matrix » dessinée en fond, avec rémanence et scanlines. */
public class MatrixRainView extends View {

    private static final String CHARS =
            "アイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホ0123456789";

    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fadePaint = new Paint();
    private final Paint scanPaint = new Paint();
    private final Random rnd = new Random();

    private int[] head;
    private float[] speed;
    private int columns = 0;
    private int charSize = 16;
    private long last = 0;
    private boolean running = true;

    public MatrixRainView(Context c) { this(c, null); }

    public MatrixRainView(Context c, AttributeSet a) {
        super(c, a);
        textPaint.setTypeface(android.graphics.Typeface.MONOSPACE);
        textPaint.setColor(0xFF00E5FF);
        fadePaint.setColor(0xCC000000);
        scanPaint.setColor(0x22000000);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        columns = Math.max(1, w / charSize);
        head = new int[columns];
        speed = new float[columns];
        for (int i = 0; i < columns; i++) {
            head[i] = -rnd.nextInt(h / charSize + 1);
            speed[i] = 0.25f + rnd.nextFloat() * 0.9f;
        }
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
        if (w == 0 || h == 0 || head == null) return;

        // rémanence
        canvas.drawRect(0, 0, w, h, fadePaint);

        textPaint.setTextSize(charSize - 2);
        int rowH = charSize;

        for (int i = 0; i < columns; i++) {
            head[i] += Math.round(speed[i] * dt);
            if (head[i] * rowH > h + rowH * 4) {
                head[i] = -rnd.nextInt(20);
                speed[i] = 0.25f + rnd.nextFloat() * 0.9f;
            }
            int row = head[i];
            if (row < 0) continue;
            float y = row * rowH;
            char ch = CHARS.charAt(rnd.nextInt(CHARS.length()));
            textPaint.setColor(0xFFB8F7FF);
            canvas.drawText(String.valueOf(ch), i * charSize, y, textPaint);
            textPaint.setColor(0x9900B8D4);
            canvas.drawText(String.valueOf(ch), i * charSize, y - rowH, textPaint);
        }

        for (int y = 0; y < h; y += 4) {
            canvas.drawRect(0, y, w, y + 1, scanPaint);
        }

        if (running) postInvalidateOnAnimation();
    }
}
