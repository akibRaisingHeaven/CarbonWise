package com.example.carbonwise;

import android.app.Activity;
import android.content.Intent;
import android.graphics.RectF;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONArray;
import org.json.JSONObject;
import org.maplibre.android.MapLibre;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;
import org.maplibre.geojson.Feature;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Location picker built on MapLibre + OpenFreeMap (free, no API key, no billing account).
 *
 * The pin is fixed at the center of the map. When the map stops moving, the place name is
 * read from the label the map itself is drawing under the pin, so the name always matches
 * what the user sees. If there is no label under the pin, the name comes from a reverse lookup.
 * The exact coordinates are always returned together with the name.
 */
public class MapPickerActivity extends AppCompatActivity {

    private static final String STYLE_URL = "https://tiles.openfreemap.org/styles/liberty";
    private static final String USER_AGENT = "CarbonWiseApp/1.0 (com.example.carbonwise)";

    private MapView mapView;
    private MapLibreMap map;

    private TextView tvSelectedLocation, tvSelectedCoords;
    private EditText etMapSearch;
    private Button btnMapSearch, btnConfirmLocation;
    private ImageButton btnBack;
    private FloatingActionButton fabMyLocation;

    private String selectedPlaceName = "";
    private boolean nameReady = false;
    private String pendingSearchName = null;
    private int resolveGeneration = 0;

    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (GpsLocationHelper.hasPermission(this)) {
                    moveToMyLocation();
                } else {
                    Toast.makeText(this, "Location permission is required to find your current location", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        MapLibre.getInstance(this);
        setContentView(R.layout.activity_map_picker);

        mapView = findViewById(R.id.mapView);
        tvSelectedLocation = findViewById(R.id.tvSelectedLocation);
        tvSelectedCoords = findViewById(R.id.tvSelectedCoords);
        etMapSearch = findViewById(R.id.etMapSearch);
        btnMapSearch = findViewById(R.id.btnMapSearch);
        btnConfirmLocation = findViewById(R.id.btnConfirmLocation);
        btnBack = findViewById(R.id.btnBack);
        fabMyLocation = findViewById(R.id.fabMyLocation);

        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(m -> {
            map = m;
            map.getUiSettings().setRotateGesturesEnabled(false);
            map.getUiSettings().setTiltGesturesEnabled(false);
            map.setCameraPosition(new CameraPosition.Builder()
                    .target(new LatLng(MapApiClient.DEFAULT_LAT, MapApiClient.DEFAULT_LON))
                    .zoom(13.0)
                    .build());

            map.addOnCameraMoveStartedListener(reason -> onMapMoveStarted());
            map.addOnCameraIdleListener(this::onMapIdle);

            map.setStyle(STYLE_URL, style -> onMapIdle());
        });

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        btnMapSearch.setOnClickListener(v -> searchLocationName());
        etMapSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchLocationName();
                return true;
            }
            return false;
        });

        fabMyLocation.setOnClickListener(v -> {
            if (GpsLocationHelper.hasPermission(this)) {
                moveToMyLocation();
            } else {
                locationPermissionLauncher.launch(new String[]{
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION});
            }
        });

        btnConfirmLocation.setOnClickListener(v -> confirmLocation());
    }

    // ---------- Map events ----------

    private void onMapMoveStarted() {
        nameReady = false;
        tvSelectedLocation.setText("Move the map to place the pin...");
    }

    private void onMapIdle() {
        if (map == null || map.getStyle() == null) return;
        LatLng center = map.getCameraPosition().target;
        if (center == null) return;

        tvSelectedCoords.setText(String.format(Locale.US, "%.5f, %.5f", center.getLatitude(), center.getLongitude()));

        // A search just moved the map here: keep the name the user searched for
        if (pendingSearchName != null) {
            setPlaceName(pendingSearchName);
            pendingSearchName = null;
            return;
        }

        resolvePlaceNameAtPin(center);
    }

    private void resolvePlaceNameAtPin(LatLng center) {
        final int generation = ++resolveGeneration;

        // 1) Prefer the label the map is actually drawing under the pin
        String label = labelUnderPin();
        if (label != null) {
            setPlaceName(label);
            return;
        }

        // 2) Otherwise ask for the nearest named place
        tvSelectedLocation.setText("Locating...");
        networkExecutor.execute(() -> {
            String name = reverseGeocode(center.getLatitude(), center.getLongitude());
            mainHandler.post(() -> {
                // Ignore late answers for a spot the user has already moved away from
                if (generation != resolveGeneration || isFinishing()) return;
                setPlaceName(name != null ? name : "Selected location");
            });
        });
    }

    private String labelUnderPin() {
        try {
            float cx = mapView.getWidth() / 2f;
            float cy = mapView.getHeight() / 2f;
            float r = 24f * getResources().getDisplayMetrics().density;
            List<Feature> features = map.queryRenderedFeatures(new RectF(cx - r, cy - r, cx + r, cy + r));
            for (Feature f : features) {
                String name = bestName(f);
                if (name != null) return name;
            }
        } catch (Exception e) {
            Log.e("MapPicker", "queryRenderedFeatures failed", e);
        }
        return null;
    }

    private String bestName(Feature f) {
        String[] keys = {"name_en", "name:latin", "name"};
        for (String key : keys) {
            try {
                if (f.hasProperty(key)) {
                    String value = f.getStringProperty(key);
                    if (value != null && !value.trim().isEmpty()) return value.trim();
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private void setPlaceName(String name) {
        selectedPlaceName = name;
        nameReady = true;
        tvSelectedLocation.setText("📍 " + name);
    }

    // ---------- Search ----------

    private void searchLocationName() {
        String query = etMapSearch.getText().toString().trim();
        if (query.isEmpty()) {
            Toast.makeText(this, "Enter a place name to search", Toast.LENGTH_SHORT).show();
            return;
        }

        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }

        Toast.makeText(this, "Searching for \"" + query + "\"...", Toast.LENGTH_SHORT).show();
        btnMapSearch.setEnabled(false);

        networkExecutor.execute(() -> {
            // Same OpenStreetMap data as the map itself, so results line up with what is drawn
            double[] found = nominatimSearch(query);
            String name = query;

            if (found == null && Geocoder.isPresent()) {
                try {
                    List<Address> addresses = new Geocoder(this, Locale.getDefault()).getFromLocationName(query, 1);
                    if (addresses != null && !addresses.isEmpty()) {
                        Address a = addresses.get(0);
                        found = new double[]{a.getLatitude(), a.getLongitude()};
                        if (a.getLocality() != null) name = a.getLocality();
                    }
                } catch (Exception e) {
                    Log.e("MapPicker", "Native Geocoder failed", e);
                }
            }

            final double[] result = found;
            final String foundName = lastSearchName != null ? lastSearchName : name;
            lastSearchName = null;

            mainHandler.post(() -> {
                btnMapSearch.setEnabled(true);
                if (result == null) {
                    Toast.makeText(this, "Location not found", Toast.LENGTH_SHORT).show();
                    return;
                }
                pendingSearchName = foundName;
                setPlaceName(foundName);
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(result[0], result[1]), 15.0), 800);
            });
        });
    }

    private String lastSearchName = null;

    private double[] nominatimSearch(String query) {
        try {
            String urlStr = "https://nominatim.openstreetmap.org/search?format=json&limit=1&q="
                    + URLEncoder.encode(query, "UTF-8");
            JSONArray array = new JSONArray(httpGet(urlStr));
            if (array.length() > 0) {
                JSONObject item = array.getJSONObject(0);
                String display = item.optString("name", "");
                if (display.isEmpty()) display = item.optString("display_name", "").split(",")[0].trim();
                lastSearchName = display.isEmpty() ? null : display;
                return new double[]{item.getDouble("lat"), item.getDouble("lon")};
            }
        } catch (Exception e) {
            Log.e("MapPicker", "Nominatim search error", e);
        }
        return null;
    }

    private String reverseGeocode(double lat, double lon) {
        try {
            String urlStr = String.format(Locale.US,
                    "https://nominatim.openstreetmap.org/reverse?format=json&zoom=17&lat=%f&lon=%f", lat, lon);
            JSONObject json = new JSONObject(httpGet(urlStr));
            String name = json.optString("name", "");
            if (name.isEmpty()) {
                String display = json.optString("display_name", "");
                if (!display.isEmpty()) name = display.split(",")[0].trim();
            }
            return name.isEmpty() ? null : name;
        } catch (Exception e) {
            Log.e("MapPicker", "Reverse geocode error", e);
            return null;
        }
    }

    private String httpGet(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        if (conn.getResponseCode() != 200) {
            throw new Exception("HTTP " + conn.getResponseCode());
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    // ---------- My location ----------

    private void moveToMyLocation() {
        Toast.makeText(this, "Getting your location...", Toast.LENGTH_SHORT).show();
        GpsLocationHelper.getCurrentLocation(this, new GpsLocationHelper.LocationCallback() {
            @Override
            public void onLocation(double latitude, double longitude) {
                if (map == null) return;
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(latitude, longitude), 16.0), 800);
            }

            @Override
            public void onError(String reason) {
                Toast.makeText(MapPickerActivity.this, "Could not get location: " + reason, Toast.LENGTH_LONG).show();
            }
        });
    }

    // ---------- Confirm ----------

    private void confirmLocation() {
        if (map == null) return;
        LatLng center = map.getCameraPosition().target;
        if (center == null) return;

        double lat = center.getLatitude();
        double lon = center.getLongitude();

        // Only use the name if it belongs to the spot the pin is on right now
        String name = (nameReady && !selectedPlaceName.isEmpty())
                ? selectedPlaceName
                : String.format(Locale.US, "%.5f, %.5f", lat, lon);

        Intent resultIntent = new Intent();
        resultIntent.putExtra("location_name", name);
        resultIntent.putExtra("lat", lat);
        resultIntent.putExtra("lon", lon);
        setResult(Activity.RESULT_OK, resultIntent);
        finish();
    }

    // ---------- MapView lifecycle ----------

    @Override protected void onStart() { super.onStart(); mapView.onStart(); }
    @Override protected void onResume() { super.onResume(); mapView.onResume(); }
    @Override protected void onPause() { super.onPause(); mapView.onPause(); }
    @Override protected void onStop() { super.onStop(); mapView.onStop(); }
    @Override public void onLowMemory() { super.onLowMemory(); mapView.onLowMemory(); }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        networkExecutor.shutdownNow();
        mapView.onDestroy();
    }
}
