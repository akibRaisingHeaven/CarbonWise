package com.example.carbonwise;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

/**
 * The single place where "today's carbon summary" is stored. Whoever knows the latest numbers
 * (the dashboard or the live tracker service) calls publish(); that writes the values and
 * notifies the content provider's observers, which refreshes the home-screen widget.
 * CarbonContentProvider reads the same values back out.
 */
public final class CarbonSummaryCache {

    static final String PREFS_NAME = "carbon_cache_prefs";
    static final String KEY_TOTAL = "today_co2";
    static final String KEY_GOAL = "daily_goal";
    static final String KEY_DATE = "cache_date";
    static final String KEY_CATEGORIES = "categories_json";
    static final String KEY_UPDATED = "updated_at";

    private CarbonSummaryCache() { }

    public static String todayString() {
        return new SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(new Date());
    }

    /** Stores today's totals and tells observers (the widget) that the data changed. */
    public static void publish(Context context, String dateStr, double totalKg, double goalKg,
                               Map<String, Double> categoryTotals) {
        Context app = context.getApplicationContext();

        JSONObject categories = new JSONObject();
        if (categoryTotals != null) {
            for (Map.Entry<String, Double> e : categoryTotals.entrySet()) {
                try {
                    categories.put(e.getKey(), e.getValue());
                } catch (Exception ignored) {
                }
            }
        }

        app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putFloat(KEY_TOTAL, (float) totalKg)
                .putFloat(KEY_GOAL, (float) goalKg)
                .putString(KEY_DATE, dateStr)
                .putString(KEY_CATEGORIES, categories.toString())
                .putLong(KEY_UPDATED, System.currentTimeMillis())
                .apply();

        app.getContentResolver().notifyChange(CarbonContentProvider.CONTENT_URI, null);
        app.getContentResolver().notifyChange(CarbonContentProvider.CATEGORIES_URI, null);
    }

    /** Forgets the cached numbers (used on logout) and refreshes observers. */
    public static void clear(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
        app.getContentResolver().notifyChange(CarbonContentProvider.CONTENT_URI, null);
        app.getContentResolver().notifyChange(CarbonContentProvider.CATEGORIES_URI, null);
    }
}
