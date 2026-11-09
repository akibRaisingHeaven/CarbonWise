package com.example.carbonwise;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.util.Iterator;

/**
 * Read-only window onto today's carbon summary.
 *
 *   content://com.example.carbonwise.provider/summary     one row: today's total, budget, percent used
 *   content://com.example.carbonwise.provider/categories  one row per category with today's CO2
 *
 * The home-screen widget reads this provider and refreshes through a ContentObserver whenever
 * CarbonSummaryCache.publish() calls notifyChange(). Values from an earlier day are never shown
 * as today's: after midnight the total reads 0 until fresh numbers are published.
 */
public class CarbonContentProvider extends ContentProvider {

    public static final String AUTHORITY = "com.example.carbonwise.provider";
    public static final Uri BASE_URI = Uri.parse("content://" + AUTHORITY);
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/summary");
    public static final Uri CATEGORIES_URI = Uri.parse("content://" + AUTHORITY + "/categories");

    // Column names of the /summary row
    public static final String COL_TOTAL = "total_co2_kg";
    public static final String COL_GOAL = "goal_kg";
    public static final String COL_PERCENT = "percent_used";
    public static final String COL_OVER = "over_budget";
    public static final String COL_DATE = "date";
    public static final String COL_UPDATED = "updated_at";
    public static final String COL_WEEK_TOTAL = "week_total_co2_kg";
    public static final String COL_WEEK_BUDGET = "week_budget_kg";
    public static final String COL_WEEK_LABEL = "week_label";
    public static final String COL_MONTH_TOTAL = "month_total_co2_kg";
    public static final String COL_MONTH_BUDGET = "month_budget_kg";
    public static final String COL_MONTH_LABEL = "month_label";

    // Column names of the /categories rows
    public static final String COL_CATEGORY = "category";
    public static final String COL_CATEGORY_CO2 = "co2_kg";

    private static final int CODE_SUMMARY = 1;
    private static final int CODE_CATEGORIES = 2;

    private static final UriMatcher MATCHER = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        MATCHER.addURI(AUTHORITY, "summary", CODE_SUMMARY);
        MATCHER.addURI(AUTHORITY, "categories", CODE_CATEGORIES);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection,
                        @Nullable String selection, @Nullable String[] selectionArgs,
                        @Nullable String sortOrder) {
        Context context = getContext();
        if (context == null) return null;

        SharedPreferences prefs = context.getSharedPreferences(CarbonSummaryCache.PREFS_NAME, Context.MODE_PRIVATE);
        boolean isToday = CarbonSummaryCache.todayString().equals(prefs.getString(CarbonSummaryCache.KEY_DATE, ""));

        Cursor cursor;
        switch (MATCHER.match(uri)) {
            case CODE_SUMMARY: {
                double total = isToday ? prefs.getFloat(CarbonSummaryCache.KEY_TOTAL, 0f) : 0.0;
                double goal = prefs.getFloat(CarbonSummaryCache.KEY_GOAL, (float) CarbonCalculator.DAILY_CARBON_BUDGET_KG);
                if (goal <= 0) goal = CarbonCalculator.DAILY_CARBON_BUDGET_KG;
                int percent = (int) Math.round(total / goal * 100.0);

                boolean aggregatesAreToday = CarbonSummaryCache.todayString().equals(
                        prefs.getString(CarbonSummaryCache.KEY_AGGREGATE_DATE, ""));
                double weekTotal = aggregatesAreToday ? prefs.getFloat(CarbonSummaryCache.KEY_WEEK_TOTAL, 0f) : 0.0;
                double weekBudget = aggregatesAreToday ? prefs.getFloat(CarbonSummaryCache.KEY_WEEK_BUDGET, 0f) : 0.0;
                double monthTotal = aggregatesAreToday ? prefs.getFloat(CarbonSummaryCache.KEY_MONTH_TOTAL, 0f) : 0.0;
                double monthBudget = aggregatesAreToday ? prefs.getFloat(CarbonSummaryCache.KEY_MONTH_BUDGET, 0f) : 0.0;
                MatrixCursor c = new MatrixCursor(new String[]{
                        COL_TOTAL, COL_GOAL, COL_PERCENT, COL_OVER, COL_DATE, COL_UPDATED,
                        COL_WEEK_TOTAL, COL_WEEK_BUDGET, COL_WEEK_LABEL,
                        COL_MONTH_TOTAL, COL_MONTH_BUDGET, COL_MONTH_LABEL});
                c.addRow(new Object[]{
                        total, goal, percent, total > goal ? 1 : 0,
                        CarbonSummaryCache.todayString(),
                        (isToday ? prefs.getLong(CarbonSummaryCache.KEY_UPDATED, 0L) : 0L),
                        weekTotal, weekBudget,
                        prefs.getString(CarbonSummaryCache.KEY_WEEK_LABEL, "This week"),
                        monthTotal, monthBudget,
                        prefs.getString(CarbonSummaryCache.KEY_MONTH_LABEL, "This month")});
                cursor = c;
                break;
            }
            case CODE_CATEGORIES: {
                MatrixCursor c = new MatrixCursor(new String[]{COL_CATEGORY, COL_CATEGORY_CO2});
                if (isToday) {
                    try {
                        JSONObject json = new JSONObject(prefs.getString(CarbonSummaryCache.KEY_CATEGORIES, "{}"));
                        Iterator<String> keys = json.keys();
                        while (keys.hasNext()) {
                            String key = keys.next();
                            c.addRow(new Object[]{key, json.getDouble(key)});
                        }
                    } catch (Exception ignored) {
                    }
                }
                cursor = c;
                break;
            }
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }

        // Lets observers registered on this URI hear about changes
        cursor.setNotificationUri(context.getContentResolver(), uri);
        return cursor;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        switch (MATCHER.match(uri)) {
            case CODE_SUMMARY:
                return "vnd.android.cursor.item/vnd." + AUTHORITY + ".summary";
            case CODE_CATEGORIES:
                return "vnd.android.cursor.dir/vnd." + AUTHORITY + ".category";
            default:
                return null;
        }
    }

    // Read-only provider: writes go through CarbonSummaryCache inside the app
    @Nullable @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) { return null; }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) { return 0; }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) { return 0; }
}
