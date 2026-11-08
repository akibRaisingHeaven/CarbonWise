package com.example.carbonwise;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Writes today's "always-on devices" baseload log once a day, shortly after midnight.
 * doWork() runs on a background thread, so it waits for each Firestore call to finish; returning
 * early would let Android end the worker before anything was written.
 */
public class BaseloadWorker extends Worker {

    public BaseloadWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return Result.success();
        }

        try {
            String userId = user.getUid();
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            Calendar cal = Calendar.getInstance();

            String docId = "baseline_" + new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(cal.getTime());
            String selectedDateStr = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(cal.getTime());

            DocumentSnapshot settingsDoc = Tasks.await(
                    db.collection("users").document(userId).collection("config").document("settings").get(),
                    30, TimeUnit.SECONDS);
            if (!settingsDoc.exists()) return Result.success();

            UserSettings settings = settingsDoc.toObject(UserSettings.class);
            if (settings == null) return Result.success();

            DocumentSnapshot existing = Tasks.await(
                    db.collection("users").document(userId).collection("activity_logs").document(docId).get(),
                    30, TimeUnit.SECONDS);
            if (existing.exists()) return Result.success();

            Map<String, ApplianceConfig> userAppliances = settings.getAppliances();
            int totalAlwaysOnWatts = 0;
            if (userAppliances != null) {
                for (ApplianceConfig app : userAppliances.values()) {
                    if (app.isOwned() && app.isAlwaysOn()) {
                        totalAlwaysOnWatts += app.getWattage();
                    }
                }
            }
            if (totalAlwaysOnWatts == 0) totalAlwaysOnWatts = 160;

            double co2 = CarbonCalculator.calculateElectricityCo2(
                    totalAlwaysOnWatts, 24.0, settings.getGreenElectricityPercent());
            ActivityLog baseloadLog = new ActivityLog(docId, "Electricity", "Baseload (Always-On Devices)",
                    co2, selectedDateStr, cal.getTimeInMillis());

            Tasks.await(
                    db.collection("users").document(userId).collection("activity_logs").document(docId).set(baseloadLog),
                    30, TimeUnit.SECONDS);

            return Result.success();
        } catch (Exception e) {
            // Network trouble or a timeout: let WorkManager try again later
            return Result.retry();
        }
    }
}
