package fr.cyberpunk.worldradio;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

/**
 * Service de lecture en arrière-plan : la radio continue quand l'application
 * passe en tâche de fond, avec reconnexion automatique et gestion du focus audio.
 */
public class RadioService extends Service {

    public static final String ACTION_PLAY = "fr.cyberpunk.worldradio.PLAY";
    public static final String ACTION_STOP = "fr.cyberpunk.worldradio.STOP";

    public static final int STATE_IDLE = 0;
    public static final int STATE_LOADING = 1;
    public static final int STATE_PLAYING = 2;
    public static final int STATE_ERROR = 3;

    public interface Listener {
        void onStateChanged(int state, String station, String message);
    }

    private final IBinder binder = new LocalBinder();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private MediaPlayer player;
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private Listener listener;

    private String stationName = "";
    private String stationUrl = "";
    private int bitrate = 0;
    private int retries = 0;
    private int state = STATE_IDLE;
    private boolean pausedByFocus = false;
    private Runnable guard;

    public class LocalBinder extends Binder {
        public RadioService getService() { return RadioService.this; }
    }

    @Override
    public IBinder onBind(Intent intent) { return binder; }

    public void setListener(Listener l) {
        this.listener = l;
        if (l != null) l.onStateChanged(state, stationName, null);
    }

    public int getState() { return state; }
    public String getStationName() { return stationName; }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_STOP.equals(action)) {
                stopPlayback();
                stopSelf();
                return START_NOT_STICKY;
            }
            if (ACTION_PLAY.equals(action)) {
                String name = intent.getStringExtra("name");
                String url = intent.getStringExtra("url");
                bitrate = intent.getIntExtra("bitrate", 0);
                if (url != null && !url.isEmpty()) {
                    stationName = name == null ? "" : name;
                    stationUrl = url;
                    retries = 0;
                    startPlayback();
                }
            }
        }
        return START_NOT_STICKY;
    }

    // ------------------------------------------------------------- lecture

    private void startPlayback() {
        stopPlayerOnly();
        setState(STATE_LOADING, "Connexion…");
        startForeground(1, buildNotification("Connexion…", false));

        final MediaPlayer mp = new MediaPlayer();
        player = mp;
        try {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            mp.setAudioAttributes(attrs);
            mp.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
            mp.setDataSource(stationUrl);
            mp.setOnPreparedListener(p -> {
                cancelGuard();
                requestFocus();
                p.start();
                AudioState.sessionId = p.getAudioSessionId();
                AudioState.currentStation = stationName;
                setState(STATE_PLAYING, bitrate > 0 ? bitrate + " kbps" : null);
                updateNotification(true);
                EqualizerActivity.applySaved(this, AudioState.sessionId);
            });
            mp.setOnErrorListener((p, what, extra) -> {
                cancelGuard();
                onStreamError();
                return true;
            });
            mp.setOnCompletionListener(p -> {
                setState(STATE_IDLE, "Flux terminé");
                stopForegroundCompat();
            });
            mp.prepareAsync();
            startGuard();
        } catch (Exception e) {
            cancelGuard();
            onStreamError();
        }
    }

    /** Garde-fou : si la connexion n'aboutit pas, on considère le flux mort. */
    private void startGuard() {
        cancelGuard();
        guard = () -> onStreamError();
        handler.postDelayed(guard, 25000);
    }

    private void cancelGuard() {
        if (guard != null) {
            handler.removeCallbacks(guard);
            guard = null;
        }
    }

    private void onStreamError() {
        if (retries < 2) {
            retries++;
            setState(STATE_LOADING, "Reconnexion " + retries + "/2…");
            stopPlayerOnly();
            handler.postDelayed(this::startPlayback, 1500L * retries);
        } else {
            setState(STATE_ERROR, "Flux indisponible");
            AudioState.sessionId = 0;
            stopPlayerOnly();
            stopForegroundCompat();
        }
    }

    public void stopPlayback() {
        cancelGuard();
        handler.removeCallbacksAndMessages(null);
        stopPlayerOnly();
        abandonFocus();
        AudioState.sessionId = 0;
        AudioState.currentStation = "";
        retries = 0;
        setState(STATE_IDLE, "Arrêté");
        stopForegroundCompat();
    }

    private void stopPlayerOnly() {
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) { }
            try { player.release(); } catch (Exception ignored) { }
            player = null;
        }
    }

    @Override
    public void onDestroy() {
        cancelGuard();
        stopPlayerOnly();
        abandonFocus();
        super.onDestroy();
    }

    // ------------------------------------------------------------- focus audio

    private AudioAttributes attrs() {
        return new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
    }

    private void requestFocus() {
        if (audioManager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (focusRequest == null) {
                focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(attrs())
                        .setOnAudioFocusChangeListener(this::onFocusChange)
                        .build();
            }
            audioManager.requestAudioFocus(focusRequest);
        } else {
            audioManager.requestAudioFocus(this::onFocusChange,
                    AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        }
    }

    private void abandonFocus() {
        if (audioManager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
            audioManager.abandonAudioFocusRequest(focusRequest);
        } else {
            audioManager.abandonAudioFocus(null);
        }
    }

    private void onFocusChange(int change) {
        if (player == null) return;
        switch (change) {
            case AudioManager.AUDIOFOCUS_LOSS:
                stopPlayback();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                if (player.isPlaying()) {
                    pausedByFocus = true;
                    try { player.pause(); } catch (Exception ignored) { }
                    setState(STATE_LOADING, "En pause (audio occupé)");
                    updateNotification(false);
                }
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                if (pausedByFocus) {
                    pausedByFocus = false;
                    try { player.start(); } catch (Exception ignored) { }
                    setState(STATE_PLAYING, null);
                    updateNotification(true);
                }
                break;
            default:
                break;
        }
    }

    /** Pause / reprise de la lecture en cours. */
    public void togglePause() {
        if (player == null) return;
        try {
            if (player.isPlaying()) {
                player.pause();
                pausedByFocus = false;
                setState(STATE_LOADING, "En pause");
                updateNotification(false);
            } else {
                player.start();
                setState(STATE_PLAYING, null);
                updateNotification(true);
            }
        } catch (Exception ignored) { }
    }

    public boolean isPlaying() {
        try { return player != null && player.isPlaying(); } catch (Exception e) { return false; }
    }

    public int getBitrate() { return bitrate; }

    // ------------------------------------------------------------- notification

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel("radio", "Lecture radio",
                NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
    }

    private Notification buildNotification(String text, boolean playing) {
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stop = new Intent(this, RadioService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? new Notification.Builder(this, "radio")
                : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_stat_radio)
                .setContentTitle(stationName.isEmpty() ? "World Radio" : stationName)
                .setContentText(text)
                .setContentIntent(pi)
                .setOngoing(playing)
                .addAction(new Notification.Action.Builder(
                        null, "Stop", stopPi).build());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            b.setColor(0xFF22D3EE);
        }
        return b.build();
    }

    private void updateNotification(boolean playing) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.notify(1, buildNotification(playing ? "En lecture" : "En pause", playing));
        } catch (Throwable ignored) { }
    }

    private void stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
    }

    private void setState(int newState, String message) {
        state = newState;
        if (listener != null) listener.onStateChanged(newState, stationName, message);
    }
}
