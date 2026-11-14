package fr.cyberpunk.worldradio;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.audiofx.Equalizer;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Égaliseur 8 bandes.
 *
 * L'API Android (android.media.audiofx.Equalizer) expose un nombre de bandes
 * variable selon l'appareil (souvent 5). L'interface présente 8 bandes ; chacune
 * est appliquée à la bande matérielle la plus proche, et les bandes qui tombent
 * sur la même bande matérielle sont moyennées.
 */
public class EqualizerActivity extends Activity {

    static final int[] FREQS = {60, 150, 400, 1000, 2400, 6000, 12000, 16000};
    static final String[] LABELS = {"60 Hz", "150 Hz", "400 Hz", "1 kHz", "2.4 kHz", "6 kHz", "12 kHz", "16 kHz"};

    static final float[] FLAT  = {0, 0, 0, 0, 0, 0, 0, 0};
    static final float[] BASS  = {9, 7, 4, 1, 0, -1, -2, -2};
    static final float[] VOCAL = {-3, -2, 0, 3, 5, 4, 1, -2};
    static final float[] ROCK  = {6, 4, -1, -2, 0, 2, 4, 5};

    static final String PREFS = "eq";

    private final SeekBar[] bars = new SeekBar[8];
    private final TextView[] values = new TextView[8];
    private Equalizer eq;
    private TextView info;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_equalizer);

        LinearLayout bands = findViewById(R.id.bands);
        info = findViewById(R.id.eqInfo);

        int sessionId = getIntent().getIntExtra("sessionId", AudioState.sessionId);

        for (int i = 0; i < 8; i++) {
            bands.addView(buildRow(i));
        }

        Button flat = findViewById(R.id.btnFlat);
        Button bass = findViewById(R.id.btnBass);
        Button vocal = findViewById(R.id.btnVocal);
        Button rock = findViewById(R.id.btnRock);

        flat.setOnClickListener(v -> setPreset(FLAT));
        bass.setOnClickListener(v -> setPreset(BASS));
        vocal.setOnClickListener(v -> setPreset(VOCAL));
        rock.setOnClickListener(v -> setPreset(ROCK));

        if (sessionId <= 0) {
            info.setText("Aucune radio en lecture. Lancez une station puis revenez ici : "
                    + "l'égaliseur s'applique au flux en cours.");
            setEnabledBars(false);
        } else {
            attach(sessionId);
            loadSaved();
        }
    }

    private LinearLayout buildRow(final int index) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = dp(6);
        row.setLayoutParams(rowLp);

        TextView label = new TextView(this);
        label.setText(LABELS[index]);
        label.setTextColor(0xFF94A3B8);
        label.setTextSize(12f);
        label.setWidth(dp(64));
        row.addView(label);

        SeekBar bar = new SeekBar(this);
        bar.setMax(24);
        bar.setProgress(12);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        bar.setLayoutParams(barLp);
        row.addView(bar);

        TextView val = new TextView(this);
        val.setText("0 dB");
        val.setTextColor(0xFF22D3EE);
        val.setTextSize(12f);
        val.setWidth(dp(60));
        val.setGravity(Gravity.END);
        row.addView(val);

        bars[index] = bar;
        values[index] = val;

        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int db = progress - 12;
                values[index].setText((db > 0 ? "+" : "") + db + " dB");
                if (fromUser) applyToHardware();
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { }
            @Override public void onStopTrackingTouch(SeekBar sb) { save(); }
        });

        return row;
    }

    private void attach(int sessionId) {
        try {
            eq = new Equalizer(0, sessionId);
            eq.setEnabled(true);
            StringBuilder sb = new StringBuilder("Égaliseur matériel : ");
            sb.append(eq.getNumberOfBands()).append(" bandes. ");
            sb.append("8 curseurs répartis sur ces bandes.");
            info.setText(sb.toString());
            setEnabledBars(true);
        } catch (Throwable t) {
            eq = null;
            info.setText("Égaliseur non disponible sur cet appareil : " + t.getClass().getSimpleName());
            setEnabledBars(false);
        }
    }

    private void setEnabledBars(boolean enabled) {
        for (int i = 0; i < 8; i++) {
            if (bars[i] != null) bars[i].setEnabled(enabled);
        }
    }

    private void setPreset(float[] preset) {
        for (int i = 0; i < 8; i++) {
            int progress = Math.round(12 + preset[i]);
            if (progress < 0) progress = 0;
            if (progress > 24) progress = 24;
            bars[i].setProgress(progress);
        }
        applyToHardware();
        save();
    }

    private void applyToHardware() {
        if (eq == null) return;
        try {
            int hwCount = eq.getNumberOfBands();
            int[] hwFreqs = new int[hwCount];
            for (int j = 0; j < hwCount; j++) hwFreqs[j] = eq.getCenterFreq((short) j);
            double[] sum = new double[hwCount];
            int[] count = new int[hwCount];
            for (int i = 0; i < 8; i++) {
                int uiFreq = FREQS[i] * 1000;
                int best = 0;
                int bestDiff = Integer.MAX_VALUE;
                for (int j = 0; j < hwCount; j++) {
                    int diff = Math.abs(hwFreqs[j] - uiFreq);
                    if (diff < bestDiff) { bestDiff = diff; best = j; }
                }
                sum[best] += (bars[i].getProgress() - 12);
                count[best]++;
            }
            short[] range = eq.getBandLevelRange();
            for (int j = 0; j < hwCount; j++) {
                if (count[j] == 0) continue;
                short level = (short) Math.round((sum[j] / count[j]) * 100.0);
                if (level < range[0]) level = range[0];
                if (level > range[1]) level = range[1];
                eq.setBandLevel((short) j, level);
            }
        } catch (Throwable ignored) { }
    }

    private void save() {
        SharedPreferences.Editor e = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        for (int i = 0; i < 8; i++) e.putInt("b" + i, bars[i].getProgress());
        e.apply();
    }

    private void loadSaved() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        for (int i = 0; i < 8; i++) {
            bars[i].setProgress(p.getInt("b" + i, 12));
        }
        applyToHardware();
    }

    /** Réapplique les réglages sauvegardés à une nouvelle session audio. */
    public static void applySaved(Context ctx, int sessionId) {
        if (sessionId <= 0) return;
        SharedPreferences p = ctx.getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean any = false;
        for (int i = 0; i < 8; i++) {
            if (p.getInt("b" + i, 12) != 12) { any = true; break; }
        }
        if (!any) return;
        try {
            Equalizer e = new Equalizer(0, sessionId);
            e.setEnabled(true);
            int hwCount = e.getNumberOfBands();
            int[] hwFreqs = new int[hwCount];
            for (int j = 0; j < hwCount; j++) hwFreqs[j] = e.getCenterFreq((short) j);
            double[] sum = new double[hwCount];
            int[] count = new int[hwCount];
            for (int i = 0; i < 8; i++) {
                int uiFreq = FREQS[i] * 1000;
                int best = 0, bestDiff = Integer.MAX_VALUE;
                for (int j = 0; j < hwCount; j++) {
                    int diff = Math.abs(hwFreqs[j] - uiFreq);
                    if (diff < bestDiff) { bestDiff = diff; best = j; }
                }
                sum[best] += (p.getInt("b" + i, 12) - 12);
                count[best]++;
            }
            short[] range = e.getBandLevelRange();
            for (int j = 0; j < hwCount; j++) {
                if (count[j] == 0) continue;
                short level = (short) Math.round((sum[j] / count[j]) * 100.0);
                if (level < range[0]) level = range[0];
                if (level > range[1]) level = range[1];
                e.setBandLevel((short) j, level);
            }
        } catch (Throwable ignored) { }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
