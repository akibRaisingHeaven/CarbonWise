package com.example.carbonwise;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class CarbonContentProvider extends ContentProvider {

    public static final String AUTHORITY = "com.example.carbonwise.provider";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/summary");
    private static final String PREFS_NAME = "carbon_cache_prefs";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection,
                        @Nullable String selection, @Nullable String[] selectionArgs,
                        @Nullable String sortOrder) {
        if (getContext() == null) return null;

        SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        float totalCo2 = prefs.getFloat("today_co2", 0.0f);
        float goalKg = prefs.getFloat("daily_goal", 15.0f);

        MatrixCursor cursor = new MatrixCursor(new String[]{"total_co2_kg", "goal_kg"});
        cursor.addRow(new Object[]{totalCo2, goalKg});
        return cursor;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return "vnd.android.cursor.dir/vnd.com.example.carbonwise.summary";
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) { return null; }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) { return 0; }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) { return 0; }
}