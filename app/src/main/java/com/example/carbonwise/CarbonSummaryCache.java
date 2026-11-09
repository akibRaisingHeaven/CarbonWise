package com.example.carbonwise;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

/**
 * Local summary cache shared by the dashboard, ContentProvider and home-screen widget.
 * Daily, week-to-date and month-to-date aggregates are published together so widgets never
 * need to query Firestore directly.
 */
public final class CarbonSummaryCache {

    static final String PREFS_NAME = "carbon_cache_prefs";
    static final String KEY_TOTAL = "today_co2";
    static final String KEY_GOAL = "daily_goal";
    static final String KEY_DATE = "cache_date";
    static final String KEY_CATEGORIES = "categories_json";
    static final String KEY_UPDATED = "updated_at";
    static final String KEY_AGGREGATE_DATE = "aggregate_date";
    static final String KEY_WEEK_TOTAL = "week_total_co2";
    static final String KEY_WEEK_BUDGET = "week_budget_co2";
    static final String KEY_WEEK_LABEL = "week_label";
    static final String KEY_MONTH_TOTAL = "month_total_co2";
    static final String KEY_MONTH_BUDGET = "month_budget_co2";
    static final String KEY_MONTH_LABEL = "month_label";

    private CarbonSummaryCache() { }

    public static String todayString() {
        return new SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(new Date());
    }

    /** Updates today's summary only; used by the optional live tracker service. */
    public static void publish(Context context, String dateStr, double totalKg, double goalKg,
                               Map<String, Double> categoryTotals) {
        Context app = context.getApplicationContext();
        JSONObject categories = toJson(categoryTotals);
        app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putFloat(KEY_TOTAL, (float) Math.max(0, totalKg))
                .putFloat(KEY_GOAL, (float) Math.max(0, goalKg))
                .putString(KEY_DATE, dateStr)
                .putString(KEY_CATEGORIES, categories.toString())
                .putLong(KEY_UPDATED, System.currentTimeMillis())
                .apply();
        notifyObservers(app);
    }

    /** Publishes daily and period summaries calculated from the current Firestore log snapshot. */
    public static void publish(Context context, String dateStr, double totalKg, double goalKg,
                               Map<String, Double> categoryTotals,
                               double weekTotalKg, double weekBudgetKg, String weekLabel,
                               double monthTotalKg, double monthBudgetKg, String monthLabel) {
        Context app = context.getApplicationContext();
        JSONObject categories = toJson(categoryTotals);
        app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putFloat(KEY_TOTAL, (float) Math.max(0, totalKg))
                .putFloat(KEY_GOAL, (float) Math.max(0, goalKg))
                .putString(KEY_DATE, dateStr)
                .putString(KEY_CATEGORIES, categories.toString())
                .putFloat(KEY_WEEK_TOTAL, (float) Math.max(0, weekTotalKg))
                .putFloat(KEY_WEEK_BUDGET, (float) Math.max(0, weekBudgetKg))
                .putString(KEY_WEEK_LABEL, weekLabel == null ? "This week" : weekLabel)
                .putFloat(KEY_MONTH_TOTAL, (float) Math.max(0, monthTotalKg))
                .putFloat(KEY_MONTH_BUDGET, (float) Math.max(0, monthBudgetKg))
                .putString(KEY_MONTH_LABEL, monthLabel == null ? "This month" : monthLabel)
                .putString(KEY_AGGREGATE_DATE, dateStr)
                .putLong(KEY_UPDATED, System.currentTimeMillis())
                .apply();
        notifyObservers(app);
    }

    private static JSONObject toJson(Map<String, Double> categoryTotals) {
        JSONObject categories = new JSONObject();
        if (categoryTotals != null) {
            for (Map.Entry<String, Double> entry : categoryTotals.entrySet()) {
                try { categories.put(entry.getKey(), entry.getValue()); }
                catch (Exception ignored) { }
            }
        }
        return categories;
    }

    private static void notifyObservers(Context app) {
        app.getContentResolver().notifyChange(CarbonContentProvider.CONTENT_URI, null);
        app.getContentResolver().notifyChange(CarbonContentProvider.CATEGORIES_URI, null);
    }

    /** Clears user-specific widget data on sign-out. */
    public static void clear(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
        notifyObservers(app);
    }
}
