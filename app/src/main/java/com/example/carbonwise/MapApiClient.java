package com.example.carbonwise;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MapApiClient {

    public interface DistanceCallback {
        void onSuccess(double distanceKm, int durationMinutes);
        void onError(String errorMessage);
    }

    private static final String PREF_NAME = "map_api_prefs";
    private static final String KEY_LAT = "last_lat";
    private static final String KEY_LON = "last_lon";

    // Default regional coordinates fallback (Khulna, BD)
    public static final double DEFAULT_LAT = 22.8456;
    public static final double DEFAULT_LON = 89.5403;

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static void saveLastKnownCoordinates(Context context, double lat, double lon) {
        if (context == null) return;
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putFloat(KEY_LAT, (float) lat)
                .putFloat(KEY_LON, (float) lon)
                .apply();
    }

    public static double[] getLastKnownCoordinates(Context context) {
        if (context == null) return new double[]{DEFAULT_LAT, DEFAULT_LON};
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        float lat = prefs.getFloat(KEY_LAT, (float) DEFAULT_LAT);
        float lon = prefs.getFloat(KEY_LON, (float) DEFAULT_LON);
        return new double[]{(double) lat, (double) lon};
    }

    public static void fetchDistance(String origin, String destination, DistanceCallback callback) {
        executor.execute(() -> {
            try {
                // 1. Dynamically resolve Origin (Coordinates or Web API Lookup)
                double[] originCoords = resolveLocation(origin);
                if (originCoords == null) {
                    mainHandler.post(() -> callback.onError("Could not locate starting point: " + origin));
                    return;
                }

                // 2. Dynamically resolve Destination (Coordinates or Web API Lookup)
                double[] destCoords = resolveLocation(destination);
                if (destCoords == null) {
                    mainHandler.post(() -> callback.onError("Could not locate destination point: " + destination));
                    return;
                }

                // 3. Query OSRM Road Distance
                boolean routed = fetchOsrmRoute(originCoords, destCoords, callback);

                // 4. Fallback calculation if OSRM server is temporarily down
                if (!routed) {
                    double airDist = calculateHaversineDistance(originCoords[0], originCoords[1], destCoords[0], destCoords[1]);
                    double roadEstKm = Math.round(airDist * 1.3 * 10.0) / 10.0;
                    int estMins = (int) Math.round((roadEstKm / 50.0) * 60.0);
                    mainHandler.post(() -> callback.onSuccess(roadEstKm, estMins));
                }

            } catch (Exception e) {
                Log.e("MapApiClient", "Error fetching distance", e);
                mainHandler.post(() -> callback.onError("Error calculating distance: " + e.getMessage()));
            }
        });
    }

    private static double[] resolveLocation(String locationStr) {
        if (locationStr == null || locationStr.trim().isEmpty()) return null;

        String clean = locationStr.trim();

        // Direct Coordinate Parsing (e.g. from MapPicker "22.89764, 89.50523")
        double[] coords = parseCoordinates(clean);
        if (coords != null) return coords;

        // Dynamic Web API 1: Open-Meteo Geocoding (Fast, Free, No key, Unblocked)
        coords = geocodeOpenMeteo(clean);
        if (coords != null) return coords;

        // Dynamic Web API 2: Nominatim Search API
        coords = geocodeNominatim(clean);
        if (coords != null) return coords;

        return null;
    }

    private static double[] parseCoordinates(String input) {
        String[] parts = input.split(",");
        if (parts.length == 2) {
            try {
                double lat = Double.parseDouble(parts[0].trim());
                double lon = Double.parseDouble(parts[1].trim());
                if (lat >= -90.0 && lat <= 90.0 && lon >= -180.0 && lon <= 180.0) {
                    return new double[]{lat, lon};
                }
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private static double[] geocodeOpenMeteo(String query) {
        try {
            String urlStr = "https://geocoding-api.open-meteo.com/v1/search?name="
                    + URLEncoder.encode(query, "UTF-8") + "&count=1";
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject json = new JSONObject(sb.toString());
                JSONArray results = json.optJSONArray("results");
                if (results != null && results.length() > 0) {
                    JSONObject first = results.getJSONObject(0);
                    return new double[]{first.getDouble("latitude"), first.getDouble("longitude")};
                }
            }
        } catch (Exception e) {
            Log.e("MapApiClient", "Open-Meteo geocoding error: " + query, e);
        }
        return null;
    }

    private static double[] geocodeNominatim(String query) {
        try {
            String urlStr = "https://nominatim.openstreetmap.org/search?q="
                    + URLEncoder.encode(query, "UTF-8") + "&format=json&limit=1";
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "CarbonWiseApp/1.0 (com.example.carbonwise)");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONArray array = new JSONArray(sb.toString());
                if (array.length() > 0) {
                    JSONObject item = array.getJSONObject(0);
                    return new double[]{item.getDouble("lat"), item.getDouble("lon")};
                }
            }
        } catch (Exception e) {
            Log.e("MapApiClient", "Nominatim geocoding error: " + query, e);
        }
        return null;
    }

    private static boolean fetchOsrmRoute(double[] origin, double[] dest, DistanceCallback callback) {
        try {
            String urlStr = String.format(Locale.US,
                    "https://router.project-osrm.org/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=false",
                    origin[1], origin[0], dest[1], dest[0]);

            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "CarbonWiseApp/1.0 (com.example.carbonwise)");
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject json = new JSONObject(sb.toString());
                JSONArray routes = json.optJSONArray("routes");
                if (routes != null && routes.length() > 0) {
                    JSONObject route = routes.getJSONObject(0);
                    double distanceKm = route.getDouble("distance") / 1000.0;
                    int durationMins = (int) Math.round(route.getDouble("duration") / 60.0);

                    mainHandler.post(() -> callback.onSuccess(distanceKm, durationMins));
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e("MapApiClient", "OSRM Route fetch error", e);
        }
        return false;
    }

    private static double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371;
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}