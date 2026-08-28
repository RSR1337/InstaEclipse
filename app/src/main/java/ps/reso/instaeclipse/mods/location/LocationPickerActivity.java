package ps.reso.instaeclipse.mods.location;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ps.reso.instaeclipse.R;
import ps.reso.instaeclipse.utils.core.CommonUtils;
import ps.reso.instaeclipse.utils.core.SettingsManager;
import ps.reso.instaeclipse.providers.SettingsProvider;

/**
 * Full-screen OpenStreetMap picker used to choose the coordinates GPS-spoofing reports to
 * Instagram. Search uses Nominatim's public geocoding API; picked coordinates are persisted
 * directly (both prefs stores) and broadcast to Instagram's process so the change takes
 * effect immediately without an app restart.
 */
public class LocationPickerActivity extends AppCompatActivity {

    public static final String EXTRA_LAT = "lat";
    public static final String EXTRA_LNG = "lng";
    public static final String RESULT_LAT = "result_lat";
    public static final String RESULT_LNG = "result_lng";

    private MapView map;
    private Chip coordChip;
    private TextInputEditText searchInput;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            SharedPreferences prefs = getApplicationContext()
                    .getSharedPreferences(getApplicationContext().getPackageName() + "_preferences", MODE_PRIVATE);
            Configuration.getInstance().load(getApplicationContext(), prefs);
            Configuration.getInstance().setUserAgentValue(getPackageName());
        } catch (Throwable ignored) {}

        setContentView(R.layout.activity_location_picker);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        map = findViewById(R.id.map);
        coordChip = findViewById(R.id.coord_chip);
        searchInput = findViewById(R.id.search_input);
        MaterialButton useBtn = findViewById(R.id.btn_use);
        ImageView pin = findViewById(R.id.center_pin);
        pin.setColorFilter(0xFFEB6D24);

        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) return false;
            hideKeyboard();
            String q = v.getText() != null ? v.getText().toString().trim() : "";
            if (!q.isEmpty()) searchPlace(q);
            return true;
        });

        ImageButton clearBtn = findViewById(R.id.search_clear);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                clearBtn.setVisibility((s == null || s.length() <= 0) ? View.GONE : View.VISIBLE);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        clearBtn.setOnClickListener(v -> {
            searchInput.setText("");
            searchInput.requestFocus();
        });

        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getController().setZoom(13.0);

        double startLat = getIntent().getDoubleExtra(EXTRA_LAT, 0.0);
        double startLng = getIntent().getDoubleExtra(EXTRA_LNG, 0.0);
        if (startLat == 0.0 && startLng == 0.0) {
            startLat = 40.7128;
            startLng = -74.006;
        }
        GeoPoint start = new GeoPoint(startLat, startLng);
        map.getController().setCenter(start);
        updateCoordText(startLat, startLng);

        map.addMapListener(new MapListener() {
            @Override public boolean onScroll(ScrollEvent e) { onMapMoved(); return false; }
            @Override public boolean onZoom(ZoomEvent e) { onMapMoved(); return false; }
        });

        useBtn.setOnClickListener(v -> {
            GeoPoint c = (GeoPoint) map.getMapCenter();
            double lat = c.getLatitude();
            double lng = c.getLongitude();
            persistCoords(lat, lng);
            Intent data = new Intent();
            data.putExtra(RESULT_LAT, lat);
            data.putExtra(RESULT_LNG, lng);
            setResult(RESULT_OK, data);
            finish();
        });
    }

    private void onMapMoved() {
        GeoPoint c = (GeoPoint) map.getMapCenter();
        updateCoordText(c.getLatitude(), c.getLongitude());
    }

    private void updateCoordText(double lat, double lng) {
        coordChip.setText(String.format(Locale.US, "%.5f, %.5f", lat, lng));
    }

    private void persistCoords(double lat, double lng) {
        SharedPreferences.Editor prefs = getSharedPreferences("instaeclipse_prefs", MODE_PRIVATE).edit();
        prefs.putString("spoofLat", String.valueOf(lat));
        prefs.putString("spoofLng", String.valueOf(lng));
        long updatedAt = SettingsManager.stampUpdatedAt(prefs);
        prefs.commit();

        SharedPreferences.Editor cache = getSharedPreferences("instaeclipse_cache", MODE_PRIVATE).edit();
        cache.putString("spoofLat", String.valueOf(lat));
        cache.putString("spoofLng", String.valueOf(lng));
        cache.putLong(SettingsProvider.KEY_UPDATED_AT, updatedAt);
        cache.commit();

        Intent latIntent = new Intent("ps.reso.instaeclipse.ACTION_UPDATE_PREF_STRING");
        latIntent.putExtra("key", "spoofLat");
        latIntent.putExtra("value", String.valueOf(lat));
        latIntent.putExtra(SettingsProvider.KEY_UPDATED_AT, updatedAt);
        CommonUtils.broadcastToInstagram(this, latIntent);

        Intent lngIntent = new Intent("ps.reso.instaeclipse.ACTION_UPDATE_PREF_STRING");
        lngIntent.putExtra("key", "spoofLng");
        lngIntent.putExtra("value", String.valueOf(lng));
        lngIntent.putExtra(SettingsProvider.KEY_UPDATED_AT, updatedAt);
        CommonUtils.broadcastToInstagram(this, lngIntent);
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null && searchInput != null) {
                imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
            }
        } catch (Throwable ignored) {}
    }

    private void searchPlace(String query) {
        io.execute(() -> {
            HttpURLConnection conn = null;
            try {
                String url = "https://nominatim.openstreetmap.org/search?format=json&limit=1&q="
                        + URLEncoder.encode(query, "UTF-8");
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestProperty("User-Agent", getPackageName() + "/1.0 (LocationPicker)");
                conn.setRequestProperty("Accept-Language", "en");

                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                }
                JSONArray arr = new JSONArray(sb.toString());

                if (arr.length() == 0) {
                    main.post(() -> Toast.makeText(this, R.string.loc_picker_search_failed, Toast.LENGTH_SHORT).show());
                    return;
                }
                JSONObject first = arr.getJSONObject(0);
                double lat = first.getDouble("lat");
                double lng = first.getDouble("lon");
                main.post(() -> {
                    map.getController().animateTo(new GeoPoint(lat, lng));
                    map.getController().setZoom(15.0);
                    updateCoordText(lat, lng);
                });
            } catch (Throwable t) {
                main.post(() -> Toast.makeText(this,
                        getString(R.string.loc_picker_search_error, String.valueOf(t.getMessage())),
                        Toast.LENGTH_SHORT).show());
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (map != null) map.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (map != null) map.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { io.shutdownNow(); } catch (Throwable ignored) {}
    }
}
