package com.example.carbonwise;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Loads and saves the single UserSettings document for all of the settings screens.
 *
 * Every settings screen loads the current document, changes only its own fields and saves the
 * whole document back, so the screens never overwrite each other's values.
 */
public class SettingsStore {

    public interface LoadCallback {
        void onLoaded(UserSettings settings);
        void onError(String reason);
    }

    public interface SaveCallback {
        void onSaved();
        void onError(String reason);
    }

    /** Loads the saved settings, or a fresh default UserSettings if the user has none yet. */
    public static void load(LoadCallback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            callback.onError("Not signed in");
            return;
        }

        FirebaseFirestore.getInstance()
                .collection("users").document(user.getUid())
                .collection("config").document("settings")
                .get()
                .addOnSuccessListener(doc -> {
                    UserSettings settings = doc.exists() ? doc.toObject(UserSettings.class) : null;
                    if (settings == null) settings = new UserSettings();
                    if (settings.getAppliances() == null) settings.setAppliances(new HashMap<>());
                    callback.onLoaded(settings);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /**
     * Saves the settings and applies everything that depends on them: today's locked budget,
     * reminder alarms, today's baseload entry and the live tracker service.
     *
     * @param recomputeBaseload true when the green percentage or appliances changed, so today's
     *                          baseload entry must be recalculated; otherwise it is only created
     *                          if it is missing (this keeps a reported power outage intact).
     */
    public static void save(Context context, UserSettings settings, boolean recomputeBaseload, SaveCallback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            callback.onError("Not signed in");
            return;
        }

        final Context app = context.getApplicationContext();
        final FirebaseFirestore db = FirebaseFirestore.getInstance();
        final String userId = user.getUid();

        db.collection("users").document(userId).collection("config").document("settings")
                .set(settings)
                .addOnSuccessListener(aVoid -> {
                    Calendar today = Calendar.getInstance();
                    String todayStr = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(today.getTime());

                    // Lock in the effective budget for today (non-retroactive for past days)
                    Map<String, Object> budgetData = new HashMap<>();
                    budgetData.put("date", todayStr);
                    budgetData.put("budgetKg", settings.getDailyBudgetKg());
                    db.collection("users").document(userId).collection("daily_budgets").document(todayStr).set(budgetData);

                    ReminderScheduler.scheduleAllReminders(app, settings);

                    FirestoreRepository repo = new FirestoreRepository();
                    if (recomputeBaseload) {
                        repo.updateBaseload(today, todayStr, 0.0, settings.getAppliances(),
                                settings.getGreenElectricityPercent(), new FirestoreRepository.OperationCallback() {
                                    @Override public void onSuccess(String message) {}
                                    @Override public void onError(String error) {}
                                });
                    } else {
                        repo.ensureDailyBaseloadLogged(today, todayStr, settings.getAppliances(),
                                settings.getGreenElectricityPercent());
                    }

                    Intent serviceIntent = new Intent(app, CarbonGoalService.class);
                    if (settings.isLiveTrackerEnabled()) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            app.startForegroundService(serviceIntent);
                        } else {
                            app.startService(serviceIntent);
                        }
                    } else {
                        app.stopService(serviceIntent);
                    }

                    callback.onSaved();
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }
}
