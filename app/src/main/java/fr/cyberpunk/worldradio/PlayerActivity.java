package fr.cyberpunk.worldradio;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.audiofx.Visualizer;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

public class PlayerActivity extends Activity {

    private VisualizerView viz, spectrum;
    private ImageView art, ring;
    private TextView name, sub, stateTv, volVal;
    private ImageButton btnPlay;
    private SeekBar vol;
    private View root, liveDot;

    private RadioService radio;
    private boolean bound = false;
    private Visualizer visualizer;
    private AudioManager audioManager;

    private ObjectAnimator ringAnim, pulseAnim, blinkAnim;
    private ValueAnimator bgAnim;
    private boolean askedPermission = false;

    private static final float[] BANDS = new float[44];

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName cn, IBinder binder) {
            radio = ((RadioService.LocalBinder) binder).getService();
            bound = true;
            radio.setListener((st, station, message) -> render(st, message));
            render(radio.getState(), null);
        }
        @Override public void onServiceDisconnected(ComponentName cn) { radio = null; bound = false; }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        root = findViewById(R.id.root);
        spectrum = findViewById(R.id.spectrum);
        art = findViewById(R.id.art);
        name = findViewById(R.id.name);
        sub = findViewById(R.id.sub);
        stateTv = findViewById(R.id.state);
        btnPlay = findViewById(R.id.btnPlay);
        vol = findViewById(R.id.vol);
        volVal = findViewById(R.id.volVal);
        liveDot = findViewById(R.id.liveDot);

        name.setText(AudioState.stationName.isEmpty() ? "Aucune station" : AudioState.stationName);
        name.setSelected(true); // lance le défilement LCD

        StringBuilder sb = new StringBuilder();
        if (!AudioState.stationCountry.isEmpty()) sb.append(AudioState.stationCountry.toUpperCase());
        if (AudioState.stationBitrate > 0) {
            if (sb.length() > 0) sb.append("  ");
            sb.append(AudioState.stationBitrate).append(" kbps  STEREO");
        }
        sub.setText(sb.toString());
        if (art != null) ImageLoader.load(AudioState.stationFavicon, art);

        applySystemInsets(root);

        startAnimations();
        initVolume();

        btnPlay.setOnClickListener(v -> { bounce(v); if (radio != null) radio.togglePause(); });
        findViewById(R.id.btnStop).setOnClickListener(v -> { bounce(v); if (radio != null) radio.stopPlayback(); });
        findViewById(R.id.btnEq).setOnClickListener(v -> {
            bounce(v);
            Intent i = new Intent(this, EqualizerActivity.class);
            i.putExtra("sessionId", AudioState.sessionId);
            startActivity(i);
        });
        findViewById(R.id.btnClose).setOnClickListener(v -> finish());

        bindService(new Intent(this, RadioService.class), connection, Context.BIND_AUTO_CREATE);
        askAudioPermission();
    }


    /** Évite que le contenu passe sous la barre d'état et la barre de navigation. */
    private void applySystemInsets(View rootView) {
        rootView.setOnApplyWindowInsetsListener((v, insets) -> {
            int top, bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars =
                        insets.getInsets(android.view.WindowInsets.Type.systemBars());
                top = bars.top;
                bottom = bars.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(0, top, 0, bottom);
            return insets;
        });
        rootView.requestApplyInsets();
    }

    // ------------------------------------------------------------ animations

    private void startAnimations() {
        if (ring != null) {
            ringAnim = ObjectAnimator.ofFloat(ring, View.ROTATION, 0f, 360f);
            ringAnim.setDuration(16000);
            ringAnim.setRepeatCount(ValueAnimator.INFINITE);
            ringAnim.setInterpolator(new LinearInterpolator());
            ringAnim.start();
        }
        if (art == null) { startBackground(); return; }
        pulseAnim = ObjectAnimator.ofFloat(art, View.SCALE_X, 1f, 1.05f);
        pulseAnim.setDuration(1800);
        pulseAnim.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnim.setRepeatMode(ValueAnimator.REVERSE);
        ObjectAnimator sy = ObjectAnimator.ofFloat(art, View.SCALE_Y, 1f, 1.05f);
        sy.setDuration(1800);
        sy.setRepeatCount(ValueAnimator.INFINITE);
        sy.setRepeatMode(ValueAnimator.REVERSE);
        pulseAnim.start();
        sy.start();

        if (liveDot != null) {
            blinkAnim = ObjectAnimator.ofFloat(liveDot, View.ALPHA, 1f, 0.2f);
            blinkAnim.setDuration(850);
            blinkAnim.setRepeatCount(ValueAnimator.INFINITE);
            blinkAnim.setRepeatMode(ValueAnimator.REVERSE);
            blinkAnim.start();
        }

        startBackground();
    }

    /** Fond « bureau » gris du skin classique. */
    private void startBackground() {
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF4A4A4A, 0xFF303030});
        root.setBackground(gd);
    }

    private static int blend(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * t);
        int g = (int) (ag + (bg - ag) * t);
        int bl = (int) (ab + (bb - ab) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private void bounce(View v) {
        v.animate().scaleX(0.93f).scaleY(0.93f).setDuration(80)
                .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(130).start())
                .start();
    }

    // ------------------------------------------------------------ volume

    private void initVolume() {
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        final int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        vol.setMax(max);
        vol.setProgress(current);
        volVal.setText(String.valueOf(Math.round(current * 100f / Math.max(1, max))));
        vol.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                volVal.setText(String.valueOf(Math.round(p * 100f / Math.max(1, max))));
                if (fromUser) audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, p, 0);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { }
            @Override public void onStopTrackingTouch(SeekBar sb) { }
        });
    }

    // ------------------------------------------------------------ visualiseur

    private boolean canRecord() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void askAudioPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !askedPermission && !canRecord()) {
            askedPermission = true;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 20);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == 20 && results.length > 0
                && results[0] == PackageManager.PERMISSION_GRANTED) {
            attachVisualizer();
        }
    }

    private void attachVisualizer() {
        if (visualizer != null) return;
        if (AudioState.sessionId == 0 || !canRecord()) {
            if (viz != null) viz.useSimulation();
            if (spectrum != null) spectrum.useSimulation();
            return;
        }
        try {
            Visualizer v = new Visualizer(AudioState.sessionId);
            v.setCaptureSize(Math.min(Visualizer.getCaptureSizeRange()[1], 1024));
            v.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override public void onWaveFormDataCapture(Visualizer vis, byte[] wave, int rate) { }
                @Override public void onFftDataCapture(Visualizer vis, byte[] fft, int rate) {
                    computeFft(fft);
                }
            }, Visualizer.getMaxCaptureRate() / 2, false, true);
            v.setEnabled(true);
            visualizer = v;
            if (viz != null) viz.setSimulate(false);
            if (spectrum != null) spectrum.setSimulate(false);
        } catch (Throwable t) {
            if (viz != null) viz.useSimulation();
            if (spectrum != null) spectrum.useSimulation();
        }
    }

    private void computeFft(byte[] fft) {
        if (fft == null || fft.length < 4) return;
        int n = fft.length / 2;
        double[] mag = new double[n];
        for (int i = 0; i < n; i++) {
            double re = fft[2 * i];
            double im = fft[2 * i + 1];
            mag[i] = Math.sqrt(re * re + im * im);
        }
        float[] out = new float[BANDS.length];
        for (int b = 0; b < BANDS.length; b++) {
            int lo = (int) Math.floor(Math.pow(n, b / (double) BANDS.length));
            int hi = (int) Math.floor(Math.pow(n, (b + 1) / (double) BANDS.length));
            if (hi <= lo) hi = lo + 1;
            if (hi > n) hi = n;
            double m = 0;
            for (int i = lo; i < hi; i++) m = Math.max(m, mag[i]);
            out[b] = (float) Math.min(1.0, m / 90.0);
        }
        if (viz != null) viz.setBands(out);
        if (spectrum != null) spectrum.setBands(out);
    }

    // ------------------------------------------------------------ état

    private void render(int state, String message) {
        if (stateTv != null) stateTv.setText(message == null ? "" : message);
        boolean playing = state == RadioService.STATE_PLAYING;
        if (btnPlay != null) {
            btnPlay.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        }
        if (pulseAnim != null && art != null) {
            if (playing) {
                if (!pulseAnim.isStarted()) pulseAnim.start();
            } else {
                pulseAnim.cancel();
                art.setScaleX(1f);
                art.setScaleY(1f);
            }
        }
        if (state == RadioService.STATE_PLAYING) attachVisualizer();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (visualizer != null) {
            try { visualizer.setEnabled(false); } catch (Throwable ignored) { }
            try { visualizer.release(); } catch (Throwable ignored) { }
            visualizer = null;
        }
        if (ringAnim != null) ringAnim.cancel();
        if (pulseAnim != null) pulseAnim.cancel();
        if (blinkAnim != null) blinkAnim.cancel();
        if (bgAnim != null) bgAnim.cancel();
        if (bound) {
            try { unbindService(connection); } catch (Exception ignored) { }
            bound = false;
        }
    }
}
