package com.example.carbonwise;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Live tracker: keeps an ongoing notification with today's CO2 against the daily budget,
 * publishes the same numbers for the content provider / widget, and alerts once per day when
 * the budget is exceeded.
 */
public class CarbonGoalService extends Service {

    private static final String CHANNEL_ID = "carbon_baseline_channel";
    private static final String ALERT_CHANNEL_ID = "carbon_goal_alert_channel";
    private static final int NOTIFICATION_ID = 1;
    private static final int ALERT_NOTIFICATION_ID = 2;
    private static final String PREFS_ALERT = "carbon_alert_prefs";
    private static final String KEY_ALERT_DATE = "alert_date";

    private FirebaseFirestore db;
    private ListenerRegistration logListener;
    private ListenerRegistration settingsListener;
    private String userId;

    private String currentNotificationText = "Monitoring baseline emissions & eco-goals...";
    private double dailyBudgetKg = CarbonCalculator.DAILY_CARBON_BUDGET_KG;

    // Totals for every date seen in the logs, so "today" can be recomputed when midnight passes
    private final Map<String, Map<String, Double>> categoryTotalsByDate = new HashMap<>();
    private boolean logsLoaded = false;

    // Recomputes "today" when the date or clock changes while the service is running
    private final BroadcastReceiver dateChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            publishProgress();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_DATE_CHANGED);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        ContextCompat.registerReceiver(this, dateChangeReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // A foreground service must call startForeground quickly, so do it first
        Notification notification = buildNotification(currentNotificationText);
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            // Nobody is signed in (for example after logout), so there is nothing to track
            stopSelf();
            return START_NOT_STICKY;
        }

        userId = user.getUid();
        db = FirebaseFirestore.getInstance();
        if (logListener == null) {
            startLiveEmissionsListener();
        }
        return START_STICKY;
    }

    private void startLiveEmissionsListener() {
        // Follow the daily budget saved in Settings
        settingsListener = db.collection("users").document(userId).collection("config").document("settings")
                .addSnapshotListener((doc, e) -> {
                    if (e != null || doc == null || !doc.exists()) return;
                    Double budget = doc.getDouble("dailyBudgetKg");
                    if (budget != null && budget > 0) {
                        dailyBudgetKg = CarbonCalculator.clampBudget(budget);
                        if (logsLoaded) publishProgress();
                    }
                });

        logListener = db.collection("users").document(userId).collection("activity_logs")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;

                    categoryTotalsByDate.clear();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        ActivityLog log = doc.toObject(ActivityLog.class);
                        if (log == null || log.getDate() == null) continue;
                        String category = log.getCategory() == null ? "Other" : log.getCategory();
                        categoryTotalsByDate
                                .computeIfAbsent(log.getDate(), k -> new HashMap<>())
                                .merge(category, log.getCo2Emitted(), Double::sum);
                    }
                    logsLoaded = true;
                    publishProgress();
                });
    }

    /** Works out today's numbers and pushes them to the cache/widget, the notification and the alert. */
    private void publishProgress() {
        String today = CarbonSummaryCache.todayString();
        Map<String, Double> todayCategories = categoryTotalsByDate.get(today);
        if (todayCategories == null) todayCategories = new HashMap<>();

        double todayTotal = 0.0;
        for (double v : todayCategories.values()) todayTotal += v;

        // Content provider / widget
        CarbonSummaryCache.publish(this, today, todayTotal, dailyBudgetKg, todayCategories);

        // Ongoing notification
        int percentage = (int) Math.min(100, (todayTotal / dailyBudgetKg) * 100);
        currentNotificationText = String.format(Locale.getDefault(),
                "Today: %.1f / %.1f kg CO₂ (%d%% used)", todayTotal, dailyBudgetKg, percentage);
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification(currentNotificationText));
        }

        // One alert per day when the budget is passed
        if (todayTotal > dailyBudgetKg) {
            SharedPreferences prefs = getSharedPreferences(PREFS_ALERT, MODE_PRIVATE);
            if (!today.equals(prefs.getString(KEY_ALERT_DATE, ""))) {
                prefs.edit().putString(KEY_ALERT_DATE, today).apply();
                sendBudgetExceededAlert(todayTotal);
            }
        }
    }

    private void sendBudgetExceededAlert(double todayTotal) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) return;

        Intent intent = new Intent(this, DashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification alert = new NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("⚠️ Daily carbon budget exceeded")
                .setContentText(String.format(Locale.getDefault(),
                        "You are at %.1f kg CO₂ today, over your %.1f kg budget.", todayTotal, dailyBudgetKg))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build();
        manager.notify(ALERT_NOTIFICATION_ID, alert);
    }

    private Notification buildNotification(String text) {
        Intent intent = new Intent(this, DashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("🌿 CarbonWise Live Tracker")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager == null) return;

            NotificationChannel live = new NotificationChannel(
                    CHANNEL_ID, "Baseload & Live Tracker Service", NotificationManager.IMPORTANCE_LOW);
            live.setDescription("Displays real-time carbon emissions progress and maintains baseline calculations.");
            manager.createNotificationChannel(live);

            NotificationChannel alert = new NotificationChannel(
                    ALERT_CHANNEL_ID, "Budget Alerts", NotificationManager.IMPORTANCE_DEFAULT);
            alert.setDescription("Tells you once a day when you pass your daily carbon budget.");
            manager.createNotificationChannel(alert);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(dateChangeReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        if (logListener != null) {
            logListener.remove();
            logListener = null;
        }
        if (settingsListener != null) {
            settingsListener.remove();
            settingsListener = null;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
