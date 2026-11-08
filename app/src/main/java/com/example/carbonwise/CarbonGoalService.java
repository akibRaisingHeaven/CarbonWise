package com.example.carbonwise;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CarbonGoalService extends Service {

    private static final String CHANNEL_ID = "carbon_baseline_channel";
    private static final int NOTIFICATION_ID = 1;

    private FirebaseFirestore db;
    private ListenerRegistration logListener;
    private String userId;
    private String currentNotificationText = "Monitoring baseline emissions & eco-goals...";
    private ListenerRegistration settingsListener;
    private double lastTodayTotal = 0.0;
    private double dailyBudgetKg = CarbonCalculator.DAILY_CARBON_BUDGET_KG;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            userId = user.getUid();
            db = FirebaseFirestore.getInstance();
            if (logListener == null) {
                startLiveEmissionsListener();
            }
        }

        startForeground(NOTIFICATION_ID, buildNotification(currentNotificationText));
        return START_STICKY;
    }

    // Writes today's progress against the budget chosen in Setup to the cache and the notification
    private void publishProgress() {
        // Cache live metrics in SharedPreferences for CarbonContentProvider
        SharedPreferences prefs = getSharedPreferences("carbon_cache_prefs", MODE_PRIVATE);
        prefs.edit()
                .putFloat("today_co2", (float) lastTodayTotal)
                .putFloat("daily_goal", (float) dailyBudgetKg)
                .apply();

        // Notify ContentProvider observers of URI data updates
        getContentResolver().notifyChange(CarbonContentProvider.CONTENT_URI, null);

        int percentage = (int) Math.min(100, (lastTodayTotal / dailyBudgetKg) * 100);
        currentNotificationText = String.format(Locale.getDefault(),
                "Today: %.1f / %.1f kg CO₂ (%d%% used)", lastTodayTotal, dailyBudgetKg, percentage);

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification(currentNotificationText));
        }
    }

    private void startLiveEmissionsListener() {
        if (userId == null || db == null) return;

        // Follow the daily budget saved in Setup
        settingsListener = db.collection("users").document(userId).collection("config").document("settings")
                .addSnapshotListener((doc, e) -> {
                    if (e != null || doc == null || !doc.exists()) return;
                    Double budget = doc.getDouble("dailyBudgetKg");
                    if (budget != null && budget > 0) {
                        dailyBudgetKg = CarbonCalculator.clampBudget(budget);
                        publishProgress();
                    }
                });

        String todayStr = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(new Date());

        logListener = db.collection("users").document(userId).collection("activity_logs")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;

                    double todayTotal = 0.0;
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        ActivityLog log = doc.toObject(ActivityLog.class);
                        if (log != null && todayStr.equals(log.getDate())) {
                            todayTotal += log.getCo2Emitted();
                        }
                    }

                    lastTodayTotal = todayTotal;
                    publishProgress();
                });
    }

    private Notification buildNotification(String text) {
        Intent intent = new Intent(this, DashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

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

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Baseload & Live Tracker Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Displays real-time carbon emissions progress and maintains baseline calculations.");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
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