package com.example.carbonwise;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CarbonGoalService extends Service {

    private static final String CHANNEL_ID = "CarbonGoalServiceChannel";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("CarbonWise Active")
                .setContentText("Monitoring baseline emissions & eco-goals...")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build();

        startForeground(1, notification);
        autoLogAlwaysOnBaseline();

        return START_STICKY;
    }

    private void autoLogAlwaysOnBaseline() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        String userId = user.getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        String todayDate = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(new Date());
        String baselineDocId = "baseline_" + new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());

        db.collection("users").document(userId).collection("config").document("settings")
                .get()
                .addOnSuccessListener(configDoc -> {
                    double greenPercent = 0.0;
                    int totalAlwaysOnWatts = 0;

                    if (configDoc.exists()) {
                        UserSettings settings = configDoc.toObject(UserSettings.class);
                        if (settings != null) {
                            greenPercent = settings.getGreenEnergyPercent();
                            if (settings.getAppliances() != null) {
                                for (ApplianceConfig app : settings.getAppliances().values()) {
                                    if (app.isOwned() && app.isAlwaysOn()) {
                                        totalAlwaysOnWatts += app.getWattage();
                                    }
                                }
                            }
                        }
                    }

                    if (totalAlwaysOnWatts == 0) {
                        totalAlwaysOnWatts = 160;
                    }

                    double baselineCo2 = CarbonCalculator.calculateElectricityCo2(totalAlwaysOnWatts, 24.0, greenPercent);
                    long timestamp = System.currentTimeMillis();

                    final double finalCo2 = baselineCo2;
                    db.collection("users").document(userId)
                            .collection("activity_logs")
                            .document(baselineDocId)
                            .get()
                            .addOnSuccessListener(doc -> {
                                if (!doc.exists()) {
                                    ActivityLog baselineLog = new ActivityLog(
                                            baselineDocId,
                                            "Energy",
                                            "Baseload (Always-On Devices)",
                                            finalCo2,
                                            todayDate,
                                            timestamp
                                    );

                                    db.collection("users").document(userId)
                                            .collection("activity_logs")
                                            .document(baselineDocId)
                                            .set(baselineLog);
                                }
                            });
                });
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Carbon Goal Monitoring Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}