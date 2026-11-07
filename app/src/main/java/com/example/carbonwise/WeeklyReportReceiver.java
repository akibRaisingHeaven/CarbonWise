package com.example.carbonwise;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class WeeklyReportReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "weekly_report_channel";

    @Override
    public void onReceive(Context context, Intent intent) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
        Set<String> past7Days = new HashSet<>();

        for (int i = 0; i < 7; i++) {
            past7Days.add(sdf.format(cal.getTime()));
            cal.add(Calendar.DAY_OF_YEAR, -1);
        }

        db.collection("users").document(user.getUid()).collection("activity_logs")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    double weeklyCo2 = 0.0;
                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        ActivityLog log = doc.toObject(ActivityLog.class);
                        if (log != null && past7Days.contains(log.getDate())) {
                            weeklyCo2 += log.getCo2Emitted();
                        }
                    }
                    sendNotification(context, weeklyCo2);
                });
    }

    private void sendNotification(Context context, double weeklyCo2) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Weekly Carbon Reports",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

        Intent openReportsIntent = new Intent(context, ReportsActivity.class);
        openReportsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        openReportsIntent.putExtra("AUTO_GENERATE", true);
        openReportsIntent.putExtra("REPORT_TYPE", "7_DAYS");

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                openReportsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_agenda)
                .setContentTitle("📊 Weekly Carbon Report")
                .setContentText(String.format(Locale.getDefault(), "Past 7 days total: %.1f kg CO₂. Tap to generate & view report!", weeklyCo2))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        if (manager != null) {
            manager.notify(301, builder.build());
        }
    }
}