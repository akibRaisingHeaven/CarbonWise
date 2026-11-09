package com.example.carbonwise;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

import java.util.Calendar;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Dialog-only entry point used by the home-screen widget. It deliberately does not show
 * DashboardActivity; only the logging dialog is presented over the calling surface.
 */
public class QuickLogActivity extends AppCompatActivity {

    private final Map<String, ApplianceConfig> appliances = new HashMap<>();
    private double greenElectricityPercent = 0.0;
    private double dailyBudgetKg = CarbonCalculator.DAILY_CARBON_BUDGET_KG;
    private FirestoreRepository repository;
    private EditText pendingMapField;
    private AlertDialog entryDialog;
    private boolean dialogShown;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable fallbackShowDialog = () -> showEntryDialog();

    private final ActivityResultLauncher<Intent> mapPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null && pendingMapField != null) {
                    String locationName = result.getData().getStringExtra("location_name");
                    if (locationName != null && !locationName.isEmpty()) {
                        pendingMapField.setText(locationName);
                        if (result.getData().hasExtra("lat") && result.getData().hasExtra("lon")) {
                            double lat = result.getData().getDoubleExtra("lat", 0.0);
                            double lon = result.getData().getDoubleExtra("lon", 0.0);
                            pendingMapField.setTag(new String[]{locationName,
                                    String.format(java.util.Locale.US, "%.6f, %.6f", lat, lon)});
                        }
                    }
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Apply dim-behind programmatically. Keeping windowDimAmount out of the theme avoids
        // AAPT resource-linking failures on SDK/toolchain combinations that omit that attr.
        if (getWindow() != null) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams windowParams = getWindow().getAttributes();
            windowParams.dimAmount = 0.55f;
            getWindow().setAttributes(windowParams);
        }
        // Transparent host window: only the AlertDialog itself is visible above the home screen.
        View transparent = new View(this);
        transparent.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        setContentView(transparent);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Toast.makeText(this, "Please open CarbonWise and sign in first.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        repository = new FirestoreRepository();
        // Do not leave the user with a blank overlay if settings cannot be fetched while offline.
        mainHandler.postDelayed(fallbackShowDialog, 1200L);
        repository.loadUserSettings(new FirestoreRepository.SettingsCallback() {
            @Override
            public void onSettingsLoaded(UserSettings settings) {
                if (settings != null) {
                    greenElectricityPercent = settings.getGreenElectricityPercent();
                    if (settings.getDailyBudgetKg() > 0) dailyBudgetKg = settings.getDailyBudgetKg();
                    if (settings.getAppliances() != null) {
                        appliances.clear();
                        appliances.putAll(settings.getAppliances());
                    }
                }
                showEntryDialog();
            }

            @Override
            public void onNoSettingsFound() {
                showEntryDialog();
            }
        });
    }

    private void showEntryDialog() {
        if (dialogShown || isFinishing()) return;
        dialogShown = true;
        mainHandler.removeCallbacks(fallbackShowDialog);

        entryDialog = ActivityDialogHelper.showAddOrEditDialog(
                this,
                null,
                appliances,
                greenElectricityPercent,
                targetField -> {
                    pendingMapField = targetField;
                    mapPickerLauncher.launch(new Intent(this, MapPickerActivity.class));
                },
                (title, category, co2) -> repository.saveLog(
                        title, category, co2, CarbonSummaryCache.todayString(),
                        new FirestoreRepository.OperationCallback() {
                            @Override
                            public void onSuccess(String message) {
                                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
                                refreshWidgetSummary();
                                if (entryDialog != null && entryDialog.isShowing()) entryDialog.dismiss();
                            }

                            @Override
                            public void onError(String error) {
                                Toast.makeText(getApplicationContext(), "Could not save activity: " + error,
                                        Toast.LENGTH_LONG).show();
                            }
                        }),
                false
        );
        entryDialog.setOnDismissListener(dialog -> finish());
    }

    /** After a widget-originated log, republish all periods so the widget changes without opening the dashboard. */
    private void refreshWidgetSummary() {
        repository.loadLogsOnce(new FirestoreRepository.LogsCallback() {
            @Override
            public void onLogsLoaded(java.util.List<ActivityLog> logs) {
                Calendar today = Calendar.getInstance();
                Calendar start = (Calendar) today.clone();
                start.set(Calendar.HOUR_OF_DAY, 0);
                start.set(Calendar.MINUTE, 0);
                start.set(Calendar.SECOND, 0);
                start.set(Calendar.MILLISECOND, 0);

                Calendar weekStart = (Calendar) start.clone();
                weekStart.setFirstDayOfWeek(Calendar.MONDAY);
                int daysFromMonday = (weekStart.get(Calendar.DAY_OF_WEEK) + 5) % 7;
                weekStart.add(Calendar.DAY_OF_YEAR, -daysFromMonday);
                Calendar monthStart = (Calendar) start.clone();
                monthStart.set(Calendar.DAY_OF_MONTH, 1);

                double todayTotal = 0, weekTotal = 0, monthTotal = 0;
                Map<String, Double> categories = new HashMap<>();
                for (ActivityLog log : logs) {
                    Calendar logDay = parseLogDay(log);
                    if (logDay == null) continue;
                    long dayMillis = logDay.getTimeInMillis();
                    double co2 = Math.max(0, log.getCo2Emitted());
                    if (dayMillis == start.getTimeInMillis()) {
                        todayTotal += co2;
                        String category = log.getCategory() == null ? "Other" : log.getCategory();
                        categories.merge(category, co2, Double::sum);
                    }
                    if (dayMillis >= weekStart.getTimeInMillis() && dayMillis <= start.getTimeInMillis()) weekTotal += co2;
                    if (logDay.get(Calendar.YEAR) == start.get(Calendar.YEAR)
                            && logDay.get(Calendar.MONTH) == start.get(Calendar.MONTH)
                            && dayMillis <= start.getTimeInMillis()) monthTotal += co2;
                }
                int elapsedWeekDays = Math.max(1, daysBetween(weekStart, start) + 1);
                int elapsedMonthDays = start.get(Calendar.DAY_OF_MONTH);
                String weekLabel = "Week to date · " + new SimpleDateFormat("MMM d", Locale.getDefault()).format(weekStart.getTime());
                String monthLabel = "Month to date · " + new SimpleDateFormat("MMMM", Locale.getDefault()).format(start.getTime());
                CarbonSummaryCache.publish(QuickLogActivity.this, CarbonSummaryCache.todayString(),
                        todayTotal, dailyBudgetKg, categories,
                        weekTotal, dailyBudgetKg * elapsedWeekDays, weekLabel,
                        monthTotal, dailyBudgetKg * elapsedMonthDays, monthLabel);
            }

            @Override
            public void onError(String error) {
                // The log is already saved; the next dashboard/background sync will refresh the cache.
            }
        });
    }

    private Calendar parseLogDay(ActivityLog log) {
        Calendar day = Calendar.getInstance();
        try {
            Date parsed = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).parse(log.getDate());
            if (parsed != null) day.setTime(parsed);
            else if (log.getTimestamp() > 0) day.setTimeInMillis(log.getTimestamp());
            else return null;
        } catch (Exception e) {
            if (log.getTimestamp() > 0) day.setTimeInMillis(log.getTimestamp());
            else return null;
        }
        day.set(Calendar.HOUR_OF_DAY, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);
        return day;
    }

    private int daysBetween(Calendar from, Calendar to) {
        Calendar cursor = (Calendar) from.clone();
        int days = 0;
        while (cursor.before(to)) { cursor.add(Calendar.DAY_OF_YEAR, 1); days++; }
        return days;
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacks(fallbackShowDialog);
        super.onDestroy();
    }
}
