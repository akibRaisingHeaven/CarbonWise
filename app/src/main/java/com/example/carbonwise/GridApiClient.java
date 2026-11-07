package com.example.carbonwise;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Fetches the live share of renewable electricity on the grid from Electricity Maps.
 *
 * Replaces WattTimeApiClient: WattTime's free plan only covers CAISO_NORTH (California)
 * and does not cover Bangladesh, so it can never work for users in Khulna.
 *
 * The location (lat/lon from the device GPS) is sent to Electricity Maps, which resolves
 * it to the correct grid zone automatically. Results are cached per location for 15 minutes
 * to stay well inside the API rate limits.
 */
public class GridApiClient {

    private static final String BASE_URL = "https://api.electricitymap.org/v3/power-breakdown/latest";
    private static final String PREFS_NAME = "grid_prefs";
    private static final String KEY_PERCENT = "cached_percent";
    private static final String KEY_TIME = "cached_time";
    private static final String KEY_LOC = "cached_loc";
    private static final long CACHE_MS = 15 * 60 * 1000L;

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();

    public interface GridCallback {
        void onSuccess(double cleanPercent);
        void onError(String reason);
    }

    public static void fetchCleanPercentage(Context context, double lat, double lon, GridCallback callback) {
        Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Cache is only valid for the same (rounded) location
        String locKey = String.format(java.util.Locale.US, "%.2f,%.2f", lat, lon);
        long age = System.currentTimeMillis() - prefs.getLong(KEY_TIME, 0);
        if (age < CACHE_MS && prefs.contains(KEY_PERCENT)
                && locKey.equals(prefs.getString(KEY_LOC, ""))) {
            callback.onSuccess(prefs.getFloat(KEY_PERCENT, 0f));
            return;
        }

        String token = BuildConfig.ELECTRICITY_MAPS_TOKEN;
        if (token == null || token.isEmpty()) {
            callback.onError("Electricity Maps API key is missing (add it to local.properties).");
            return;
        }

        HttpUrl url = HttpUrl.parse(BASE_URL).newBuilder()
                .addQueryParameter("lat", String.format(java.util.Locale.US, "%.4f", lat))
                .addQueryParameter("lon", String.format(java.util.Locale.US, "%.4f", lon))
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("auth-token", token)
                .build();

        CLIENT.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError("No connection: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (Response r = response) {
                    String body = r.body() != null ? r.body().string() : "";
                    if (!r.isSuccessful()) {
                        callback.onError("HTTP " + r.code() + ": " + body);
                        return;
                    }
                    JSONObject json = new JSONObject(body);
                    double percent;
                    if (!json.isNull("renewablePercentage")) {
                        percent = json.getDouble("renewablePercentage");
                    } else if (!json.isNull("fossilFreePercentage")) {
                        percent = json.getDouble("fossilFreePercentage");
                    } else {
                        callback.onError("Response had no renewable percentage.");
                        return;
                    }
                    prefs.edit()
                            .putFloat(KEY_PERCENT, (float) percent)
                            .putLong(KEY_TIME, System.currentTimeMillis())
                            .putString(KEY_LOC, locKey)
                            .apply();
                    callback.onSuccess(percent);
                } catch (Exception e) {
                    callback.onError("Could not parse response: " + e.getMessage());
                }
            }
        });
    }
}
