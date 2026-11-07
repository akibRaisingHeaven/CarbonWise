package com.example.carbonwise;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.util.MapTileIndex;
import org.osmdroid.views.MapView;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MapPickerActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    private MapView mapView;
    private TextView tvSelectedLocation;
    private EditText etMapSearch;
    private Button btnMapSearch, btnConfirmLocation;
    private ImageButton btnBack;
    private FloatingActionButton fabMyLocation;

    private String selectedPlaceName = "";
    private double selectedLat = 0.0;
    private double selectedLon = 0.0;

    private final ExecutorService reverseGeocodeExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService searchExecutor = Executors.newSingleThreadExecutor();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Handler debounceHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingReverseGeocode;

    public static final OnlineTileSourceBase ESRI_STREET_MAP = new OnlineTileSourceBase(
            "EsriWorldStreetMap",
            0, 19, 256, "",
            new String[]{"https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/"},
            "Tiles © Esri"
    ) {
        @Override
        public String getTileURLString(final long pMapTileIndex) {
            return getBaseUrl()
                    + MapTileIndex.getZoom(pMapTileIndex) + "/"
                    + MapTileIndex.getY(pMapTileIndex) + "/"
                    + MapTileIndex.getX(pMapTileIndex);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Configuration.getInstance().load(getApplicationContext(), PreferenceManager.getDefaultSharedPreferences(getApplicationContext()));
        Configuration.getInstance().setUserAgentValue("CarbonWiseApp/1.0 (" + getPackageName() + ")");

        clearOsmTileCache();

        setContentView(R.layout.activity_map_picker);

        mapView = findViewById(R.id.mapView);
        tvSelectedLocation = findViewById(R.id.tvSelectedLocation);
        etMapSearch = findViewById(R.id.etMapSearch);
        btnMapSearch = findViewById(R.id.btnMapSearch);
        btnConfirmLocation = findViewById(R.id.btnConfirmLocation);
        btnBack = findViewById(R.id.btnBack);
        fabMyLocation = findViewById(R.id.fabMyLocation);

        mapView.setTileSource(ESRI_STREET_MAP);
        mapView.setMultiTouchControls(true);

        mapView.post(() -> {
            GeoPoint startPoint = new GeoPoint(23.8103, 90.4125); // Default: Dhaka
            mapView.getController().setZoom(13.0);
            mapView.getController().setCenter(startPoint);
            updateCenterCoordinatesTextOnly();
            scheduleDebouncedReverseGeocode();
        });

        mapView.addMapListener(new MapListener() {
            @Override
            public boolean onScroll(ScrollEvent event) {
                updateCenterCoordinatesTextOnly();
                scheduleDebouncedReverseGeocode();
                return true;
            }

            @Override
            public boolean onZoom(ZoomEvent event) {
                updateCenterCoordinatesTextOnly();
                scheduleDebouncedReverseGeocode();
                return true;
            }
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

        fabMyLocation.setOnClickListener(v -> fetchAndMoveToCurrentLocation());

        btnConfirmLocation.setOnClickListener(v -> {
            if (selectedPlaceName.isEmpty()) {
                selectedPlaceName = String.format(Locale.US, "%.5f, %.5f", selectedLat, selectedLon);
            }
            Intent resultIntent = new Intent();
            resultIntent.putExtra("location_name", selectedPlaceName);
            resultIntent.putExtra("lat", selectedLat);
            resultIntent.putExtra("lon", selectedLon);
            setResult(Activity.RESULT_OK, resultIntent);
            finish();
        });
    }

    private void updateCenterCoordinatesTextOnly() {
        GeoPoint center = (GeoPoint) mapView.getMapCenter();
        if (center == null) return;
        selectedLat = center.getLatitude();
        selectedLon = center.getLongitude();
        tvSelectedLocation.setText(String.format(Locale.US, "Lat: %.4f, Lon: %.4f", selectedLat, selectedLon));
    }

    private void scheduleDebouncedReverseGeocode() {
        if (pendingReverseGeocode != null) {
            debounceHandler.removeCallbacks(pendingReverseGeocode);
        }
        pendingReverseGeocode = () -> fetchAddressName(selectedLat, selectedLon);
        debounceHandler.postDelayed(pendingReverseGeocode, 500);
    }

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

        searchExecutor.execute(() -> {
            boolean found = false;

            if (Geocoder.isPresent()) {
                try {
                    Geocoder geocoder = new Geocoder(MapPickerActivity.this, Locale.getDefault());
                    List<Address> addresses = geocoder.getFromLocationName(query, 1);
                    if (addresses != null && !addresses.isEmpty()) {
                        Address addr = addresses.get(0);
                        double lat = addr.getLatitude();
                        double lon = addr.getLongitude();
                        String name = addr.getLocality() != null ? addr.getLocality() : addr.getAddressLine(0);

                        mainHandler.post(() -> {
                            btnMapSearch.setEnabled(true);
                            GeoPoint target = new GeoPoint(lat, lon);
                            mapView.getController().animateTo(target);
                            mapView.getController().setZoom(14.0);
                            selectedPlaceName = name;
                            tvSelectedLocation.setText("📍 " + selectedPlaceName);
                        });
                        found = true;
                    }
                } catch (Exception e) {
                    Log.e("MapPicker", "Native Geocoder failed", e);
                }
            }

            if (!found) {
                try {
                    String encoded = URLEncoder.encode(query, "UTF-8");
                    String urlStr = "https://nominatim.openstreetmap.org/search?q=" + encoded + "&format=json&limit=1";
                    URL url = new URL(urlStr);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("User-Agent", "CarbonWiseApp/1.0 (" + getPackageName() + ")");
                    conn.setConnectTimeout(4000);
                    conn.setReadTimeout(4000);

                    if (conn.getResponseCode() == 200) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) sb.append(line);
                        reader.close();

                        JSONArray array = new JSONArray(sb.toString());
                        if (array.length() > 0) {
                            JSONObject item = array.getJSONObject(0);
                            double lat = item.getDouble("lat");
                            double lon = item.getDouble("lon");
                            String displayName = item.optString("display_name");

                            mainHandler.post(() -> {
                                btnMapSearch.setEnabled(true);
                                GeoPoint target = new GeoPoint(lat, lon);
                                mapView.getController().animateTo(target);
                                mapView.getController().setZoom(14.0);
                                selectedPlaceName = displayName.split(",")[0].trim();
                                tvSelectedLocation.setText("📍 " + selectedPlaceName);
                            });
                            found = true;
                        }
                    }
                } catch (Exception e) {
                    Log.e("MapPicker", "Nominatim Search HTTP error", e);
                }
            }

            if (!found) {
                mainHandler.post(() -> {
                    btnMapSearch.setEnabled(true);
                    Toast.makeText(MapPickerActivity.this, "Location not found", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void fetchAddressName(double lat, double lon) {
        reverseGeocodeExecutor.execute(() -> {
            try {
                String urlStr = String.format(Locale.US, "https://nominatim.openstreetmap.org/reverse?format=json&lat=%f&lon=%f", lat, lon);
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "CarbonWiseApp/1.0 (" + getPackageName() + ")");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();

                    JSONObject json = new JSONObject(sb.toString());
                    String name = json.optString("display_name");
                    if (!name.isEmpty()) {
                        String[] parts = name.split(",");
                        selectedPlaceName = (parts.length > 0 ? parts[0] : name).trim();
                        mainHandler.post(() -> tvSelectedLocation.setText("📍 " + selectedPlaceName));
                    }
                }
            } catch (Exception ignored) {}
        });
    }

    private void fetchAndMoveToCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            Toast.makeText(this, "Location service unavailable", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

        if (!isGpsEnabled && !isNetworkEnabled) {
            Toast.makeText(this, "Please turn on Location in system settings.", Toast.LENGTH_LONG).show();
            return;
        }

        Location bestLocation = null;
        try {
            Location gpsLoc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location netLoc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (gpsLoc != null && netLoc != null) {
                bestLocation = gpsLoc.getTime() > netLoc.getTime() ? gpsLoc : netLoc;
            } else {
                bestLocation = gpsLoc != null ? gpsLoc : netLoc;
            }
        } catch (SecurityException ignored) {}

        if (bestLocation != null) {
            moveMapToLocation(bestLocation);
        } else {
            Toast.makeText(this, "Acquiring GPS location...", Toast.LENGTH_SHORT).show();
            try {
                LocationListener locationListener = new LocationListener() {
                    @Override
                    public void onLocationChanged(@NonNull Location location) {
                        moveMapToLocation(location);
                        locationManager.removeUpdates(this);
                    }
                    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
                    @Override public void onProviderEnabled(@NonNull String provider) {}
                    @Override public void onProviderDisabled(@NonNull String provider) {}
                };

                if (isNetworkEnabled) {
                    locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener, Looper.getMainLooper());
                } else {
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener, Looper.getMainLooper());
                }
            } catch (SecurityException ignored) {}
        }
    }

    private void moveMapToLocation(Location location) {
        GeoPoint currentPoint = new GeoPoint(location.getLatitude(), location.getLongitude());
        mapView.getController().animateTo(currentPoint);
        mapView.getController().setZoom(16.0);
        Toast.makeText(this, "Moved to current location", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                fetchAndMoveToCurrentLocation();
            } else {
                Toast.makeText(this, "Location permission is required to find your current location", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void clearOsmTileCache() {
        try {
            File cacheDir = new File(getCacheDir(), "osmdroid");
            if (cacheDir.exists() && cacheDir.isDirectory()) {
                deleteDirRecursive(cacheDir);
            }
        } catch (Exception ignored) {}
    }

    private boolean deleteDirRecursive(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            if (children != null) {
                for (String child : children) {
                    deleteDirRecursive(new File(dir, child));
                }
            }
        }
        return dir != null && dir.delete();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }
}