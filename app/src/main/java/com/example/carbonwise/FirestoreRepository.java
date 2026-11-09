package com.example.carbonwise;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FirestoreRepository {

    public interface LogsCallback {
        void onLogsLoaded(List<ActivityLog> logs);
        void onError(String error);
    }

    public interface OperationCallback {
        void onSuccess(String message);
        void onError(String error);
    }

    public interface SettingsCallback {
        void onSettingsLoaded(UserSettings settings);
        void onNoSettingsFound();
    }

    private final FirebaseFirestore db;
    private final String userId;

    public FirestoreRepository() {
        this.db = FirebaseFirestore.getInstance();
        this.userId = FirebaseAuth.getInstance().getCurrentUser() != null ?
                FirebaseAuth.getInstance().getCurrentUser().getUid() : "";
    }

    public void loadUserSettings(SettingsCallback callback) {
        if (userId.isEmpty()) return;
        db.collection("users").document(userId).collection("config").document("settings")
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        callback.onNoSettingsFound();
                    } else {
                        UserSettings settings = doc.toObject(UserSettings.class);
                        if (settings != null) callback.onSettingsLoaded(settings);
                    }
                });
    }

    public void listenToLogs(LogsCallback callback) {
        if (userId.isEmpty()) return;
        db.collection("users").document(userId).collection("activity_logs")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        callback.onError(e.getMessage());
                        return;
                    }
                    if (snapshots != null) {
                        List<ActivityLog> logs = new ArrayList<>();
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            ActivityLog log = doc.toObject(ActivityLog.class);
                            if (log != null) logs.add(log);
                        }
                        callback.onLogsLoaded(logs);
                    }
                });
    }

    public void saveLog(String title, String category, double co2, String dateStr, OperationCallback callback) {
        String logId = db.collection("users").document(userId).collection("activity_logs").document().getId();
        ActivityLog newLog = new ActivityLog(logId, category, title, co2, dateStr, System.currentTimeMillis());

        db.collection("users").document(userId).collection("activity_logs").document(logId)
                .set(newLog)
                .addOnSuccessListener(aVoid -> callback.onSuccess(String.format(Locale.getDefault(), "Logged: %.2f kg CO₂!", co2)))
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void updateLog(ActivityLog oldLog, String title, String category, double newCo2, OperationCallback callback) {
        ActivityLog updatedLog = new ActivityLog(oldLog.getId(), category, title, newCo2, oldLog.getDate(), oldLog.getTimestamp());

        db.collection("users").document(userId).collection("activity_logs").document(oldLog.getId())
                .set(updatedLog)
                .addOnSuccessListener(aVoid -> callback.onSuccess("Log updated successfully!"))
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void deleteLog(String logId, OperationCallback callback) {
        if (logId != null && logId.startsWith("baseline_")) {
            callback.onError("Baseload logs cannot be deleted. Use power outage reporting to adjust.");
            return;
        }
        db.collection("users").document(userId).collection("activity_logs").document(logId)
                .delete()
                .addOnSuccessListener(aVoid -> callback.onSuccess("Log deleted"))
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void updateBaseload(Calendar calendar, String selectedDateStr, double outageHours, Map<String, ApplianceConfig> userAppliances, double greenPercent, OperationCallback callback) {
        int totalAlwaysOnWatts = 0;
        if (userAppliances != null) {
            for (ApplianceConfig app : userAppliances.values()) {
                if (app.isOwned() && app.isAlwaysOn()) {
                    totalAlwaysOnWatts += app.getWattage();
                }
            }
        }
        if (totalAlwaysOnWatts == 0) totalAlwaysOnWatts = 160;

        double activeHours = Math.max(0.0, 24.0 - outageHours);
        double updatedCo2 = CarbonCalculator.calculateElectricityCo2(totalAlwaysOnWatts, activeHours, greenPercent);

        String docId = "baseline_" + new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(calendar.getTime());
        ActivityLog baseloadLog = new ActivityLog(docId, "Electricity", "Baseload (Always-On Devices)", updatedCo2, selectedDateStr, calendar.getTimeInMillis());

        db.collection("users").document(userId).collection("activity_logs").document(docId)
                .set(baseloadLog)
                .addOnSuccessListener(aVoid -> callback.onSuccess(String.format(Locale.getDefault(), "Baseload updated (%.1fh active run time)", activeHours)))
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void ensureDailyBaseloadLogged(Calendar calendar, String selectedDateStr, Map<String, ApplianceConfig> userAppliances, double greenPercent) {
        if (userId == null || userId.isEmpty()) return;

        String docId = "baseline_" + new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(calendar.getTime());

        db.collection("users").document(userId).collection("activity_logs").document(docId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        updateBaseload(calendar, selectedDateStr, 0.0, userAppliances, greenPercent, new OperationCallback() {
                            @Override
                            public void onSuccess(String message) {}

                            @Override
                            public void onError(String error) {}
                        });
                    }
                });
    }

    /**
     * Reads all logs exactly once (no live listener). Use this for work that must run a single
     * time, such as generating a report; listenToLogs() would run it again on every later change.
     */
    public void loadLogsOnce(LogsCallback callback) {
        if (userId.isEmpty()) {
            callback.onError("Not signed in");
            return;
        }
        db.collection("users").document(userId).collection("activity_logs")
                .get()
                .addOnSuccessListener(snapshots -> {
                    List<ActivityLog> logs = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        ActivityLog log = doc.toObject(ActivityLog.class);
                        if (log != null) logs.add(log);
                    }
                    callback.onLogsLoaded(logs);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public interface BudgetsCallback {
        void onBudgetsLoaded(Map<String, Double> budgetsByDate);
        void onError(String error);
    }

    /** Reads the budget saved for each day (document id = date text, e.g. "Nov 8, 2026"). */
    public void loadDailyBudgets(BudgetsCallback callback) {
        if (userId.isEmpty()) {
            callback.onError("Not signed in");
            return;
        }
        db.collection("users").document(userId).collection("daily_budgets")
                .get()
                .addOnSuccessListener(snapshots -> {
                    Map<String, Double> budgets = new java.util.HashMap<>();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        Double budgetKg = doc.getDouble("budgetKg");
                        if (budgetKg != null) budgets.put(doc.getId(), budgetKg);
                    }
                    callback.onBudgetsLoaded(budgets);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // Saved reports no longer live in Firestore: see ReportsRepository (Realtime Database).
}