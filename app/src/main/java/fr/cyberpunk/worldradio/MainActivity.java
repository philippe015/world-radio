package fr.cyberpunk.worldradio;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private ListView list;
    private StationAdapter adapter;
    private EditText search;
    private TextView status;
    private Spinner spCountry, spStyle;

    private RadioService radio;
    private View miniPlayer, miniViz;
    private TextView miniName;
    private boolean bound = false;
    private boolean notificationAsked = false;

    private final ExecutorService net = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private final List<Country> countries = new ArrayList<>();
    private final List<String> styles = new ArrayList<>();
    private String selectedCountry = "";
    private String selectedTag = "";
    private boolean ready = false;
    private long loadToken = 0;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            radio = ((RadioService.LocalBinder) binder).getService();
            bound = true;
            radio.setListener((state, station, message) -> onRadioState(state, station, message));
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            radio = null;
            bound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        list = findViewById(R.id.list);
        search = findViewById(R.id.search);
        status = findViewById(R.id.status);
        spCountry = findViewById(R.id.spCountry);
        spStyle = findViewById(R.id.spStyle);

        TextView btnSearch = findViewById(R.id.btnSearch);
        ImageButton btnMiniStop = findViewById(R.id.btnMiniStop);
        TextView btnMiniEq = findViewById(R.id.btnMiniEq);

        adapter = new StationAdapter(this);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> play(adapter.station(position)));

        btnSearch.setOnClickListener(v -> loadStations());
        search.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { loadStations(); return true; }
            return false;
        });

        btnMiniStop.setOnClickListener(v -> {
            if (radio != null) radio.stopPlayback();
            adapter.setPlaying(null);
        });

        btnMiniEq.setOnClickListener(v -> {
            Intent i = new Intent(this, EqualizerActivity.class);
            i.putExtra("sessionId", AudioState.sessionId);
            startActivity(i);
        });

        miniPlayer = findViewById(R.id.miniPlayer);
        miniViz = findViewById(R.id.miniViz);
        miniName = findViewById(R.id.miniName);
        if (miniViz instanceof VisualizerView) {
            VisualizerView v = (VisualizerView) miniViz;
            v.setSimulate(true);
        }
        miniName.setOnClickListener(v -> {
            if (AudioState.stationName.isEmpty()) return;
            startActivity(new Intent(this, PlayerActivity.class));
        });
        findViewById(R.id.btnMiniPlay).setOnClickListener(v -> {
            if (radio != null) radio.togglePause();
        });

        applySystemInsets(findViewById(R.id.root));

        setupSpinners();
        loadFilters();
        loadStations();
        bindService(new Intent(this, RadioService.class), connection, Context.BIND_AUTO_CREATE);
        askNotificationPermission();
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

    // --------------------------------------------------------------- permis

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationAsked) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                notificationAsked = true;
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
            }
        }
    }

    // --------------------------------------------------------------- filtres

    private void setupSpinners() {
        countries.add(new Country("Tous les pays", "", 0));
        styles.add("Tous les styles");

        ArrayAdapter<Country> ca = new ArrayAdapter<>(this,
                R.layout.spinner_item, countries);
        ca.setDropDownViewResource(R.layout.spinner_dropdown);
        spCountry.setAdapter(ca);

        ArrayAdapter<String> sa = new ArrayAdapter<>(this,
                R.layout.spinner_item, styles);
        sa.setDropDownViewResource(R.layout.spinner_dropdown);
        spStyle.setAdapter(sa);

        spCountry.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                selectedCountry = countries.get(pos).code;
                if (ready) loadStations();
            }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        });

        spStyle.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                selectedTag = pos == 0 ? "" : styles.get(pos);
                if (ready) loadStations();
            }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        });
    }

    private void loadFilters() {
        net.execute(() -> {
            List<Country> c = null;
            List<String> t = null;
            try { c = RadioApi.countries(); } catch (Exception ignored) { }
            try { t = RadioApi.tags(); } catch (Exception ignored) { }
            final List<Country> fc = c;
            final List<String> ft = t;
            ui.post(() -> {
                if (fc != null && !fc.isEmpty()) {
                    countries.clear();
                    countries.add(new Country("Tous les pays", "", 0));
                    countries.addAll(fc);
                    ((ArrayAdapter<?>) spCountry.getAdapter()).notifyDataSetChanged();
                }
                if (ft != null && !ft.isEmpty()) {
                    styles.clear();
                    styles.add("Tous les styles");
                    styles.addAll(ft);
                    ((ArrayAdapter<?>) spStyle.getAdapter()).notifyDataSetChanged();
                }
                ready = true;
            });
        });
    }

    // --------------------------------------------------------------- données

    private void loadStations() {
        final long token = ++loadToken;
        final String q = search.getText().toString();
        status.setText(R.string.loading);
        net.execute(() -> {
            try {
                final List<RadioStation> stations =
                        RadioApi.search(q, selectedCountry, selectedTag);
                ui.post(() -> {
                    if (token != loadToken) return;
                    adapter.setItems(stations);
                    status.setText(buildStatus(stations.size(), q));
                });
            } catch (final Exception e) {
                ui.post(() -> {
                    if (token != loadToken) return;
                    status.setText("Erreur : " + e.getMessage());
                });
            }
        });
    }

    private String buildStatus(int count, String query) {
        StringBuilder sb = new StringBuilder();
        sb.append(count).append(" stations");
        if (!query.trim().isEmpty()) sb.append(" • « ").append(query.trim()).append(" »");
        if (!selectedCountry.isEmpty()) {
            for (Country c : countries) {
                if (c.code.equals(selectedCountry)) { sb.append(" • ").append(c.name); break; }
            }
        }
        if (!selectedTag.isEmpty()) sb.append(" • ").append(selectedTag);
        if (selectedCountry.isEmpty() && selectedTag.isEmpty() && query.trim().isEmpty()) {
            sb.append(" • les plus écoutées");
        }
        return sb.toString();
    }

    // --------------------------------------------------------------- lecture

    private void play(RadioStation s) {
        AudioState.stationName = s.name;
        AudioState.stationCountry = s.country == null ? "" : s.country;
        AudioState.stationFavicon = s.favicon == null ? "" : s.favicon;
        AudioState.stationBitrate = s.bitrate;
        if (miniName != null) miniName.setText(s.name);
        if (miniPlayer != null) miniPlayer.setVisibility(View.VISIBLE);

        Intent i = new Intent(this, RadioService.class);
        i.setAction(RadioService.ACTION_PLAY);
        i.putExtra("name", s.name);
        i.putExtra("url", s.url);
        i.putExtra("bitrate", s.bitrate);
        startService(i);
        adapter.setPlaying(s.url);
        status.setText("Connexion : " + s.name);
    }

    private void onRadioState(int state, String station, String message) {
        if (miniName != null && station != null && !station.isEmpty()) miniName.setText(station);
        switch (state) {
            case RadioService.STATE_PLAYING:
                status.setText("▶ " + station + (message != null ? "  •  " + message : ""));
                break;
            case RadioService.STATE_LOADING:
                status.setText(station + "  •  " + (message == null ? "connexion…" : message));
                break;
            case RadioService.STATE_ERROR:
                status.setText("⚠ " + station + " : " + (message == null ? "flux indisponible" : message));
                adapter.setPlaying(null);
                break;
            default:
                status.setText(message == null ? "Arrêté" : message);
                adapter.setPlaying(null);
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bound) {
            try { unbindService(connection); } catch (Exception ignored) { }
            bound = false;
        }
        net.shutdownNow();
    }
}
