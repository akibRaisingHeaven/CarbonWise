package com.example.carbonwise;

import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ContextThemeWrapper;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SettingsActivity extends AppCompatActivity {

    private EditText etGreenPercent, etCustomBudget;
    private Spinner spBudgetTier;

    private CheckBox cbEnableLiveTracker, cbEnableDailyReminder, cbEnableWeeklyReport, cbEnableMonthlyReport;
    private Spinner spWeeklyDay, spMonthlyDay;
    private Button btnDailyReminderTime, btnWeeklyReportTime, btnMonthlyReportTime;
    private Button btnSaveSettings, btnReturnDashboard;

    private LinearLayout containerApplianceList;
    private FirebaseFirestore db;
    private String userId;

    private int dailyHour = 20, dailyMinute = 0;
    private int weeklyHour = 20, weeklyMinute = 0;
    private int monthlyHour = 20, monthlyMinute = 0;

    private final Map<String, View> applianceRowViews = new HashMap<>();
    private UserSettings initialUserSettings = null;

    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (GpsLocationHelper.hasPermission(SettingsActivity.this)) {
                    detectGridFromGps();
                } else {
                    Toast.makeText(SettingsActivity.this, "Location permission is needed to detect your local grid.", Toast.LENGTH_LONG).show();
                }
            });

    private void detectGridFromGps() {
        Toast.makeText(this, "Getting your location...", Toast.LENGTH_SHORT).show();

        GpsLocationHelper.getCurrentLocation(this, new GpsLocationHelper.LocationCallback() {
            @Override
            public void onLocation(double latitude, double longitude) {
                GridApiClient.fetchCleanPercentage(
                        SettingsActivity.this,
                        latitude,
                        longitude,
                        new GridApiClient.GridCallback() {
                            @Override
                            public void onSuccess(double cleanPercent) {
                                runOnUiThread(() -> {
                                    etGreenPercent.setText(String.format(Locale.getDefault(), "%.1f", cleanPercent));
                                    Toast.makeText(SettingsActivity.this, String.format(Locale.getDefault(), "Live Grid Updated: %.1f%% Clean Electricity", cleanPercent), Toast.LENGTH_LONG).show();
                                });
                            }

                            @Override
                            public void onError(String reason) {
                                runOnUiThread(() -> applyGridFallback(reason));
                            }
                        }
                );
            }

            @Override
            public void onError(String reason) {
                applyGridFallback(reason);
            }
        });
    }

    private void applyGridFallback(String reason) {
        final double regionalFallback = 1.0;
        if (etGreenPercent.getText().toString().trim().isEmpty()) {
            etGreenPercent.setText(String.format(Locale.getDefault(), "%.1f", regionalFallback));
            Toast.makeText(this, "Live data unavailable (" + reason + "). Using regional estimate 1.0%.", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Live data unavailable (" + reason + "). Keeping your current value.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            finish();
            return;
        }

        userId = user.getUid();
        db = FirebaseFirestore.getInstance();

        etGreenPercent = findViewById(R.id.etGreenPercent);
        spBudgetTier = findViewById(R.id.spBudgetTier);
        etCustomBudget = findViewById(R.id.etCustomBudget);

        Button btnAutoDetectGrid = findViewById(R.id.btnAutoDetectGrid);
        if (btnAutoDetectGrid != null) {
            btnAutoDetectGrid.setOnClickListener(v -> {
                if (GpsLocationHelper.hasPermission(SettingsActivity.this)) {
                    detectGridFromGps();
                } else {
                    locationPermissionLauncher.launch(new String[]{
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION});
                }
            });
        }

        cbEnableLiveTracker = findViewById(R.id.cbEnableLiveTracker);
        cbEnableDailyReminder = findViewById(R.id.cbEnableDailyReminder);
        cbEnableWeeklyReport = findViewById(R.id.cbEnableWeeklyReport);
        cbEnableMonthlyReport = findViewById(R.id.cbEnableMonthlyReport);

        spWeeklyDay = findViewById(R.id.spWeeklyDay);
        spMonthlyDay = findViewById(R.id.spMonthlyDay);

        btnDailyReminderTime = findViewById(R.id.btnDailyReminderTime);
        btnWeeklyReportTime = findViewById(R.id.btnWeeklyReportTime);
        btnMonthlyReportTime = findViewById(R.id.btnMonthlyReportTime);

        btnSaveSettings = findViewById(R.id.btnSaveSettings);
        btnReturnDashboard = findViewById(R.id.btnReturnDashboard);

        containerApplianceList = findViewById(R.id.containerApplianceList);

        setupSpinners();

        btnDailyReminderTime.setOnClickListener(v -> showTimePicker(0));
        btnWeeklyReportTime.setOnClickListener(v -> showTimePicker(1));
        btnMonthlyReportTime.setOnClickListener(v -> showTimePicker(2));

        btnSaveSettings.setOnClickListener(v -> saveUserSettings());
        btnReturnDashboard.setOnClickListener(v -> handleReturnRequest());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleReturnRequest();
            }
        });

        populateApplianceRows();
        loadExistingSettings();
    }

    private void setupSpinners() {
        // Budget Tiers
        String[] budgetTiers = new String[]{
                "🌱 Eco / Strict (10.0 kg CO₂/day)",
                "⚖️ Standard (15.0 kg CO₂/day)",
                "🛋️ Lenient (20.0 kg CO₂/day)",
                "✏️ Custom Budget Goal"
        };
        ArrayAdapter<String> budgetAdapter = new ArrayAdapter<>(this, R.layout.spinner_item, budgetTiers);
        budgetAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spBudgetTier.setAdapter(budgetAdapter);

        spBudgetTier.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                etCustomBudget.setVisibility(position == 3 ? View.VISIBLE : View.GONE);
            }

            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        String[] daysOfWeek = new String[]{"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
        ArrayAdapter<String> weekAdapter = new ArrayAdapter<>(this, R.layout.spinner_item, daysOfWeek);
        weekAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spWeeklyDay.setAdapter(weekAdapter);

        List<String> daysOfMonth = new ArrayList<>();
        for (int i = 1; i <= 28; i++) {
            daysOfMonth.add("Day " + i);
        }
        ArrayAdapter<String> monthAdapter = new ArrayAdapter<>(this, R.layout.spinner_item, daysOfMonth);
        monthAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spMonthlyDay.setAdapter(monthAdapter);
    }

    private void showTimePicker(int mode) {
        int initHour = (mode == 0) ? dailyHour : (mode == 1 ? weeklyHour : monthlyHour);
        int initMinute = (mode == 0) ? dailyMinute : (mode == 1 ? weeklyMinute : monthlyMinute);

        TimePickerDialog dialog = new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> {
                    if (mode == 0) {
                        dailyHour = hourOfDay;
                        dailyMinute = minute;
                    } else if (mode == 1) {
                        weeklyHour = hourOfDay;
                        weeklyMinute = minute;
                    } else {
                        monthlyHour = hourOfDay;
                        monthlyMinute = minute;
                    }
                    updateButtonLabels();
                },
                initHour,
                initMinute,
                false
        );
        dialog.show();
    }

    private String formatTimeStr(int hour, int minute) {
        int displayHour = hour % 12;
        if (displayHour == 0) displayHour = 12;
        String amPm = hour >= 12 ? "PM" : "AM";
        return String.format(Locale.getDefault(), "%d:%02d %s", displayHour, minute, amPm);
    }

    private void updateButtonLabels() {
        btnDailyReminderTime.setText("⏰ Daily Time: " + formatTimeStr(dailyHour, dailyMinute));
        btnWeeklyReportTime.setText("⏰ Weekly Time: " + formatTimeStr(weeklyHour, weeklyMinute));
        btnMonthlyReportTime.setText("⏰ Monthly Time: " + formatTimeStr(monthlyHour, monthlyMinute));
    }

    private void populateApplianceRows() {
        containerApplianceList.removeAllViews();
        List<String> appliances = CarbonCalculator.getSortedApplianceList();

        for (String name : appliances) {
            LinearLayout itemLayout = new LinearLayout(this);
            itemLayout.setOrientation(LinearLayout.VERTICAL);
            itemLayout.setPadding(24, 20, 24, 20);
            itemLayout.setBackgroundColor(Color.WHITE);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, 16);
            itemLayout.setLayoutParams(lp);

            CheckBox cbOwned = new CheckBox(this);
            cbOwned.setText(name);
            cbOwned.setTextSize(16);
            cbOwned.setTextColor(Color.parseColor("#0F172A"));
            cbOwned.setButtonTintList(ColorStateList.valueOf(Color.parseColor("#1D4ED8")));
            cbOwned.setMinHeight(48);

            LinearLayout subLayout = new LinearLayout(this);
            subLayout.setOrientation(LinearLayout.HORIZONTAL);
            subLayout.setPadding(0, 8, 0, 0);

            EditText etWatt = new EditText(this);
            etWatt.setHint("Watts");
            etWatt.setText(String.valueOf(CarbonCalculator.DEFAULT_APPLIANCE_WATTS.getOrDefault(name, 100)));
            etWatt.setInputType(InputType.TYPE_CLASS_NUMBER);
            etWatt.setTextColor(Color.parseColor("#0F172A"));
            etWatt.setHintTextColor(Color.parseColor("#94A3B8"));
            etWatt.setTextSize(14);
            etWatt.setMinHeight(48);
            etWatt.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            CheckBox cbAlwaysOn = new CheckBox(this);
            cbAlwaysOn.setText("Always On (24h)");
            cbAlwaysOn.setTextSize(14);
            cbAlwaysOn.setTextColor(Color.parseColor("#334155"));
            cbAlwaysOn.setButtonTintList(ColorStateList.valueOf(Color.parseColor("#1D4ED8")));
            cbAlwaysOn.setMinHeight(48);

            if (name.contains("Refrigerator") || name.contains("Router")) {
                cbAlwaysOn.setChecked(true);
                cbOwned.setChecked(true);
            }

            boolean isOwned = cbOwned.isChecked();
            etWatt.setEnabled(isOwned);
            cbAlwaysOn.setEnabled(isOwned);

            cbOwned.setOnCheckedChangeListener((buttonView, isChecked) -> {
                etWatt.setEnabled(isChecked);
                cbAlwaysOn.setEnabled(isChecked);
                if (!isChecked) {
                    cbAlwaysOn.setChecked(false);
                }
            });

            subLayout.addView(etWatt);
            subLayout.addView(cbAlwaysOn);

            itemLayout.addView(cbOwned);
            itemLayout.addView(subLayout);

            containerApplianceList.addView(itemLayout);
            itemLayout.setTag(new ApplianceRowHolder(cbOwned, etWatt, cbAlwaysOn));
            applianceRowViews.put(name, itemLayout);
        }
    }

    private void loadExistingSettings() {
        db.collection("users").document(userId).collection("config").document("settings")
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        UserSettings settings = doc.toObject(UserSettings.class);
                        if (settings != null) {
                            initialUserSettings = settings;
                            etGreenPercent.setText(String.valueOf(settings.getGreenElectricityPercent()));

                            String tier = settings.getBudgetTier();
                            if ("ECO".equalsIgnoreCase(tier)) spBudgetTier.setSelection(0);
                            else if ("LENIENT".equalsIgnoreCase(tier)) spBudgetTier.setSelection(2);
                            else if ("CUSTOM".equalsIgnoreCase(tier)) spBudgetTier.setSelection(3);
                            else spBudgetTier.setSelection(1);

                            etCustomBudget.setText(String.valueOf(settings.getCustomBudgetKg()));

                            cbEnableLiveTracker.setChecked(settings.isLiveTrackerEnabled());

                            cbEnableDailyReminder.setChecked(settings.isReminderEnabled());
                            dailyHour = settings.getReminderHour();
                            dailyMinute = settings.getReminderMinute();

                            cbEnableWeeklyReport.setChecked(settings.isWeeklyReminderEnabled());
                            int weekDayIdx = Math.max(1, Math.min(7, settings.getWeeklyReminderDay())) - 1;
                            spWeeklyDay.setSelection(weekDayIdx);
                            weeklyHour = settings.getWeeklyReminderHour();
                            weeklyMinute = settings.getWeeklyReminderMinute();

                            cbEnableMonthlyReport.setChecked(settings.isMonthlyReminderEnabled());
                            int monthDayIdx = Math.max(1, Math.min(28, settings.getMonthlyReminderDay())) - 1;
                            spMonthlyDay.setSelection(monthDayIdx);
                            monthlyHour = settings.getMonthlyReminderHour();
                            monthlyMinute = settings.getMonthlyReminderMinute();

                            updateButtonLabels();

                            if (settings.getAppliances() != null) {
                                for (Map.Entry<String, ApplianceConfig> entry : settings.getAppliances().entrySet()) {
                                    View row = applianceRowViews.get(entry.getKey());
                                    if (row != null) {
                                        ApplianceRowHolder holder = (ApplianceRowHolder) row.getTag();
                                        boolean isOwned = entry.getValue().isOwned();
                                        holder.cbOwned.setChecked(isOwned);
                                        holder.etWatt.setText(String.valueOf(entry.getValue().getWattage()));
                                        holder.etWatt.setEnabled(isOwned);
                                        holder.cbAlwaysOn.setChecked(isOwned && entry.getValue().isAlwaysOn());
                                        holder.cbAlwaysOn.setEnabled(isOwned);
                                    }
                                }
                            }
                        }
                    } else {
                        updateButtonLabels();
                        initialUserSettings = captureCurrentUserSettings();
                    }
                });
    }

    private UserSettings captureCurrentUserSettings() {
        String greenStr = etGreenPercent.getText().toString().trim();
        double greenPercent = TextUtils.isEmpty(greenStr) ? 0.0 : Double.parseDouble(greenStr);

        int tierPos = spBudgetTier.getSelectedItemPosition();
        String budgetTier = (tierPos == 0) ? "ECO" : (tierPos == 2 ? "LENIENT" : (tierPos == 3 ? "CUSTOM" : "STANDARD"));

        String customStr = etCustomBudget.getText().toString().trim();
        double customBudget = TextUtils.isEmpty(customStr) ? 15.0 : Double.parseDouble(customStr);

        double activeBudget = 15.0;
        if ("ECO".equalsIgnoreCase(budgetTier)) activeBudget = 10.0;
        else if ("LENIENT".equalsIgnoreCase(budgetTier)) activeBudget = 20.0;
        else if ("CUSTOM".equalsIgnoreCase(budgetTier)) activeBudget = customBudget;

        boolean liveTrackerOn = cbEnableLiveTracker.isChecked();
        int selectedWeeklyDay = spWeeklyDay.getSelectedItemPosition() + 1;
        int selectedMonthlyDay = spMonthlyDay.getSelectedItemPosition() + 1;

        Map<String, ApplianceConfig> applianceMap = new HashMap<>();
        for (Map.Entry<String, View> entry : applianceRowViews.entrySet()) {
            ApplianceRowHolder holder = (ApplianceRowHolder) entry.getValue().getTag();
            boolean isOwned = holder.cbOwned.isChecked();
            String wattText = holder.etWatt.getText().toString().trim();
            int watt = TextUtils.isEmpty(wattText) ? 100 : Integer.parseInt(wattText);
            boolean alwaysOn = isOwned && holder.cbAlwaysOn.isChecked();

            applianceMap.put(entry.getKey(), new ApplianceConfig(entry.getKey(), watt, alwaysOn, isOwned));
        }

        return new UserSettings(
                greenPercent, budgetTier, customBudget, activeBudget,
                liveTrackerOn,
                cbEnableDailyReminder.isChecked(), dailyHour, dailyMinute,
                cbEnableWeeklyReport.isChecked(), selectedWeeklyDay, weeklyHour, weeklyMinute,
                cbEnableMonthlyReport.isChecked(), selectedMonthlyDay, monthlyHour, monthlyMinute,
                applianceMap
        );
    }

    private boolean isSettingsModified() {
        if (initialUserSettings == null) return false;

        UserSettings current = captureCurrentUserSettings();
        if (Double.compare(current.getGreenElectricityPercent(), initialUserSettings.getGreenElectricityPercent()) != 0) return true;
        if (!current.getBudgetTier().equals(initialUserSettings.getBudgetTier())) return true;
        if (Double.compare(current.getCustomBudgetKg(), initialUserSettings.getCustomBudgetKg()) != 0) return true;
        if (cbEnableLiveTracker.isChecked() != initialUserSettings.isLiveTrackerEnabled()) return true;

        if (cbEnableDailyReminder.isChecked() != initialUserSettings.isReminderEnabled()) return true;
        if (dailyHour != initialUserSettings.getReminderHour() || dailyMinute != initialUserSettings.getReminderMinute()) return true;

        if (cbEnableWeeklyReport.isChecked() != initialUserSettings.isWeeklyReminderEnabled()) return true;
        if ((spWeeklyDay.getSelectedItemPosition() + 1) != initialUserSettings.getWeeklyReminderDay()) return true;
        if (weeklyHour != initialUserSettings.getWeeklyReminderHour() || weeklyMinute != initialUserSettings.getWeeklyReminderMinute()) return true;

        if (cbEnableMonthlyReport.isChecked() != initialUserSettings.isMonthlyReminderEnabled()) return true;
        if ((spMonthlyDay.getSelectedItemPosition() + 1) != initialUserSettings.getMonthlyReminderDay()) return true;
        if (monthlyHour != initialUserSettings.getMonthlyReminderHour() || monthlyMinute != initialUserSettings.getMonthlyReminderMinute()) return true;

        return false;
    }

    private void handleReturnRequest() {
        if (isSettingsModified()) {
            AlertDialog dialog = new AlertDialog.Builder(
                    new ContextThemeWrapper(this, androidx.appcompat.R.style.Theme_AppCompat_Light_Dialog_Alert)
            )
                    .setTitle("Unsaved Changes")
                    .setMessage("You have modified your settings. Discard changes and return to Dashboard?")
                    .setPositiveButton("Discard & Return", (d, which) -> navigateToDashboard())
                    .setNegativeButton("Keep Editing", (d, which) -> d.dismiss())
                    .create();

            dialog.show();

            Button positiveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button negativeBtn = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);

            if (positiveBtn != null) {
                positiveBtn.setTextColor(Color.parseColor("#DC2626"));
                positiveBtn.setTextSize(14);
            }
            if (negativeBtn != null) {
                negativeBtn.setTextColor(Color.parseColor("#2563EB"));
                negativeBtn.setTextSize(14);
            }
        } else {
            navigateToDashboard();
        }
    }

    private void navigateToDashboard() {
        startActivity(new Intent(SettingsActivity.this, DashboardActivity.class));
        finish();
    }

    private void saveUserSettings() {
        UserSettings settings = captureCurrentUserSettings();

        if (settings.getGreenElectricityPercent() < 0 || settings.getGreenElectricityPercent() > 100) {
            Toast.makeText(this, "Green electricity ratio must be between 0% and 100%", Toast.LENGTH_SHORT).show();
            return;
        }

        if ("CUSTOM".equalsIgnoreCase(settings.getBudgetTier()) && settings.getCustomBudgetKg() <= 0) {
            Toast.makeText(this, "Custom budget goal must be greater than 0 kg CO₂", Toast.LENGTH_SHORT).show();
            return;
        }

        // Save active UserSettings configuration
        db.collection("users").document(userId).collection("config").document("settings")
                .set(settings)
                .addOnSuccessListener(aVoid -> {
                    // Lock in effective budget for today in daily_budgets collection (non-retroactive)
                    String todayStr = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Calendar.getInstance().getTime());
                    Map<String, Object> budgetData = new HashMap<>();
                    budgetData.put("date", todayStr);
                    budgetData.put("budgetKg", settings.getDailyBudgetKg());

                    db.collection("users").document(userId).collection("daily_budgets").document(todayStr).set(budgetData);

                    ReminderScheduler.scheduleAllReminders(SettingsActivity.this, settings);

                    FirestoreRepository repo = new FirestoreRepository();
                    Calendar today = Calendar.getInstance();
                    repo.updateBaseload(today, todayStr, 0.0, settings.getAppliances(), settings.getGreenElectricityPercent(), new FirestoreRepository.OperationCallback() {
                        @Override public void onSuccess(String message) {}
                        @Override public void onError(String error) {}
                    });

                    Intent serviceIntent = new Intent(SettingsActivity.this, CarbonGoalService.class);
                    if (settings.isLiveTrackerEnabled()) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(serviceIntent);
                        } else {
                            startService(serviceIntent);
                        }
                    } else {
                        stopService(serviceIntent);
                    }

                    Toast.makeText(SettingsActivity.this, "Settings & Budget Goal Saved!", Toast.LENGTH_SHORT).show();
                    navigateToDashboard();
                })
                .addOnFailureListener(e -> Toast.makeText(SettingsActivity.this, "Save Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private static class ApplianceRowHolder {
        CheckBox cbOwned;
        EditText etWatt;
        CheckBox cbAlwaysOn;

        ApplianceRowHolder(CheckBox cbOwned, EditText etWatt, CheckBox cbAlwaysOn) {
            this.cbOwned = cbOwned;
            this.etWatt = etWatt;
            this.cbAlwaysOn = cbAlwaysOn;
        }
    }
}