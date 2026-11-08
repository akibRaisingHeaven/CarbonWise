package com.example.carbonwise;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.widget.RemoteViews;

import java.util.Locale;

/**
 * Home-screen widget: today's CO2, budget and progress.
 * It reads everything through CarbonContentProvider (never from Firestore), so it shows the
 * same numbers as the dashboard. CarbonWiseApp watches the provider with a ContentObserver and
 * calls refreshAll() whenever the data changes.
 */
public class CarbonWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        RemoteViews views = buildViews(context);
        for (int id : appWidgetIds) {
            appWidgetManager.updateAppWidget(id, views);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();
        // A new day (or a changed clock) means yesterday's numbers must not stay on screen
        if (Intent.ACTION_DATE_CHANGED.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {
            refreshAll(context);
        }
    }

    /** Redraws every widget on the home screen from the provider's current data. */
    public static void refreshAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, CarbonWidgetProvider.class));
        if (ids == null || ids.length == 0) return;
        manager.updateAppWidget(ids, buildViews(context));
    }

    private static RemoteViews buildViews(Context context) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_carbon_summary);

        double total = 0.0;
        double goal = CarbonCalculator.DAILY_CARBON_BUDGET_KG;
        int percent = 0;
        boolean over = false;
        boolean hasData = false;

        try (Cursor c = context.getContentResolver().query(
                CarbonContentProvider.CONTENT_URI, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                total = c.getDouble(c.getColumnIndexOrThrow(CarbonContentProvider.COL_TOTAL));
                goal = c.getDouble(c.getColumnIndexOrThrow(CarbonContentProvider.COL_GOAL));
                percent = c.getInt(c.getColumnIndexOrThrow(CarbonContentProvider.COL_PERCENT));
                over = c.getInt(c.getColumnIndexOrThrow(CarbonContentProvider.COL_OVER)) == 1;
                hasData = c.getLong(c.getColumnIndexOrThrow(CarbonContentProvider.COL_UPDATED)) > 0;
            }
        } catch (Exception ignored) {
        }

        views.setTextViewText(R.id.tvWidgetTotal, String.format(Locale.getDefault(), "%.1f kg CO₂", total));

        if (!hasData) {
            views.setTextViewText(R.id.tvWidgetBudget, "Open CarbonWise to start tracking");
            views.setProgressBar(R.id.pbWidgetBudget, 100, 0, false);
            views.setTextViewText(R.id.tvWidgetStatus, "");
        } else {
            views.setTextViewText(R.id.tvWidgetBudget, String.format(Locale.getDefault(),
                    "of %.1f kg daily budget (%d%% used)", goal, percent));
            views.setProgressBar(R.id.pbWidgetBudget, 100, Math.max(0, Math.min(100, percent)), false);
            views.setTextViewText(R.id.tvWidgetStatus, over ? "⚠️ Over budget" : "✅ On track");
            views.setTextColor(R.id.tvWidgetStatus, over ? Color.parseColor("#FCA5A5") : Color.parseColor("#BBF7D0"));
        }

        Intent open = new Intent(context, DashboardActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widgetRoot, pending);

        return views;
    }
}
