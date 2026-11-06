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

        autoLogAlwaysOnBaseline(0.0);

        return START_STICKY;
    }

    private void autoLogAlwaysOnBaseline(double dailyOutageHours) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        String userId = user.getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        String todayDate = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(new Date());
        String baselineDocId = "baseline_" + new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());

        double combinedWatts = 160.0;
        double effectiveHours = Math.max(0, 24.0 - dailyOutageHours);
        double baselineCo2 = CarbonCalculator.calculateElectricityCo2(combinedWatts, effectiveHours);
        long timestamp = System.currentTimeMillis();

        db.collection("users").document(userId)
                .collection("activity_logs")
                .document(baselineDocId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        ActivityLog baselineLog = new ActivityLog(
                                baselineDocId,
                                "Energy",
                                "Baseload (Refrigerator & Router)",
                                baselineCo2,
                                todayDate,
                                timestamp
                        );

                        db.collection("users").document(userId)
                                .collection("activity_logs")
                                .document(baselineDocId)
                                .set(baselineLog);
                    }
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