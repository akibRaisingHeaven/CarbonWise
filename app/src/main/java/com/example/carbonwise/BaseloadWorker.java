package com.example.carbonwise;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Map;

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

        String userId = user.getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Calendar cal = Calendar.getInstance();

        SimpleDateFormat sdfDoc = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        SimpleDateFormat sdfDate = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());

        String docId = "baseline_" + sdfDoc.format(cal.getTime());
        String selectedDateStr = sdfDate.format(cal.getTime());

        db.collection("users").document(userId).collection("config").document("settings")
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        UserSettings settings = doc.toObject(UserSettings.class);
                        if (settings != null) {
                            double greenPercent = settings.getGreenElectricityPercent();
                            Map<String, ApplianceConfig> userAppliances = settings.getAppliances();

                            db.collection("users").document(userId).collection("activity_logs").document(docId)
                                    .get()
                                    .addOnSuccessListener(logDoc -> {
                                        if (!logDoc.exists()) {
                                            int totalAlwaysOnWatts = 0;
                                            if (userAppliances != null) {
                                                for (ApplianceConfig app : userAppliances.values()) {
                                                    if (app.isOwned() && app.isAlwaysOn()) {
                                                        totalAlwaysOnWatts += app.getWattage();
                                                    }
                                                }
                                            }
                                            if (totalAlwaysOnWatts == 0) totalAlwaysOnWatts = 160;

                                            double co2 = CarbonCalculator.calculateElectricityCo2(totalAlwaysOnWatts, 24.0, greenPercent);
                                            ActivityLog baseloadLog = new ActivityLog(docId, "Electricity", "Baseload (Always-On Devices)", co2, selectedDateStr, cal.getTimeInMillis());

                                            db.collection("users").document(userId).collection("activity_logs").document(docId)
                                                    .set(baseloadLog);
                                        }
                                    });
                        }
                    }
                });

        return Result.success();
    }
}