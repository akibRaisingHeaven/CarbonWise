package com.example.carbonwise;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.SizeF;
import android.widget.RemoteViews;

import java.util.Locale;

/** Resizable CarbonWise daily-summary widget with quick logging. */
public class CarbonWidgetProvider extends AppWidgetProvider {

    public static final String EXTRA_OPEN_QUICK_LOG = "com.example.carbonwise.OPEN_QUICK_LOG";

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) updateWidget(context, manager, id);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager,
                                          int appWidgetId, Bundle newOptions) {
        super.onAppWidgetOptionsChanged(context, manager, appWidgetId, newOptions);
        updateWidget(context, manager, appWidgetId);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_DATE_CHANGED.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {
            refreshAll(context);
        }
    }

    /** Redraw every widget instance after the ContentProvider cache changes. */
    public static void refreshAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, CarbonWidgetProvider.class));
        if (ids == null) return;
        for (int id : ids) updateWidget(context, manager, id);
    }

    private static void updateWidget(Context context, AppWidgetManager manager, int id) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            java.util.Map<SizeF, RemoteViews> responsive = new java.util.HashMap<>();
            responsive.put(new SizeF(160f, 130f), buildViews(context, id, R.layout.widget_carbon_small));
            responsive.put(new SizeF(250f, 150f), buildViews(context, id, R.layout.widget_carbon_medium));
            responsive.put(new SizeF(320f, 245f), buildViews(context, id, R.layout.widget_carbon_large));
            manager.updateAppWidget(id, new RemoteViews(responsive));
        } else {
            Bundle options = manager.getAppWidgetOptions(id);
            int width = options == null ? 250 : options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250);
            int height = options == null ? 140 : options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 140);
            int layout = (width < 200 || height < 135) ? R.layout.widget_carbon_small
                    : (width < 300 || height < 245) ? R.layout.widget_carbon_medium
                    : R.layout.widget_carbon_large;
            manager.updateAppWidget(id, buildViews(context, id, layout));
        }
    }

    private static RemoteViews buildViews(Context context, int widgetId, int layoutId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), layoutId);
        WidgetSummary summary = readSummary(context);

        views.setTextViewText(R.id.tvWidgetHeader, "🌿 CarbonWise · Today");
        views.setTextViewText(R.id.tvWidgetTotal, summary.periodReady
                ? String.format(Locale.getDefault(), "%.1f kg CO₂", summary.total) : "— kg CO₂");
        views.setTextViewText(R.id.tvWidgetBudget, summary.periodReady
                ? String.format(Locale.getDefault(), "of %.1f kg budget · %d%% used", summary.budget, summary.percent)
                : "Open CarbonWise to sync your data");
        views.setProgressBar(R.id.pbWidgetBudget, 100, summary.periodReady
                ? Math.max(0, Math.min(100, summary.percent)) : 0, false);
        views.setTextViewText(R.id.tvWidgetStatus, summary.status);
        views.setTextColor(R.id.tvWidgetStatus, summary.statusColor);

        String remainingText = !summary.periodReady ? "Budget not synced"
                : summary.remaining >= 0
                ? String.format(Locale.getDefault(), "%.1f kg CO₂ remaining", summary.remaining)
                : String.format(Locale.getDefault(), "%.1f kg CO₂ over budget", -summary.remaining);
        views.setTextViewText(R.id.tvWidgetRemaining, remainingText);

        Intent quickLog = new Intent(context, QuickLogActivity.class);
        quickLog.putExtra(EXTRA_OPEN_QUICK_LOG, true);
        quickLog.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent quickLogPending = PendingIntent.getActivity(context, 7000 + widgetId, quickLog,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.btnWidgetQuickLog, quickLogPending);

        Intent openDashboard = new Intent(context, DashboardActivity.class);
        openDashboard.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPending = PendingIntent.getActivity(context, 8000 + widgetId, openDashboard,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widgetRoot, openPending);
        return views;
    }

    private static WidgetSummary readSummary(Context context) {
        WidgetSummary result = new WidgetSummary();
        double dailyTotal = 0;
        double dailyBudget = CarbonCalculator.DAILY_CARBON_BUDGET_KG;
        long updated = 0;
        try (Cursor cursor = context.getContentResolver().query(
                CarbonContentProvider.CONTENT_URI, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                dailyTotal = cursor.getDouble(cursor.getColumnIndexOrThrow(CarbonContentProvider.COL_TOTAL));
                dailyBudget = cursor.getDouble(cursor.getColumnIndexOrThrow(CarbonContentProvider.COL_GOAL));
                updated = cursor.getLong(cursor.getColumnIndexOrThrow(CarbonContentProvider.COL_UPDATED));
            }
        } catch (Exception ignored) { }

        result.total = Math.max(0, dailyTotal);
        result.budget = Math.max(0, dailyBudget);
        result.periodReady = updated > 0 && result.budget > 0;
        result.percent = result.budget > 0
                ? (int) Math.round(result.total / result.budget * 100.0) : 0;
        result.remaining = result.budget - result.total;
        CarbonBudgetStatus status = CarbonBudgetStatus.from(result.total, result.budget);
        result.status = result.periodReady ? status.getLabel() : "Open CarbonWise to sync";
        result.statusColor = status.getWidgetColor();
        return result;
    }

    private static class WidgetSummary {
        String status = "Open CarbonWise to sync";
        double total;
        double budget;
        double remaining;
        int percent;
        int statusColor = Color.WHITE;
        boolean periodReady;
    }
}
