package com.example.carbonwise;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DashboardActivity extends AppCompatActivity {

    private RecyclerView rvActivityLogs;
    private Button btnLogActivity, btnLogout, btnSelectDate, btnSettings, btnReportOutage, btnAnalytics;
    private TextView tvTotalCo2, tvEcoSuggestion, tvBudgetStatus, tvSummaryTitle;
    private ProgressBar pbDailyBudget;
    private ActivityLogAdapter adapter;
    private List<ActivityLog> logList;
    private List<ActivityLog> allFetchedLogs;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String userId;

    private double currentTotalCo2 = 0.0;
    private String selectedDateStr;
    private Calendar calendar;

    private double userGreenPercent = 0.0;
    private Map<String, ApplianceConfig> userAppliances = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        // Notification permission request for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            startActivity(new Intent(DashboardActivity.this, LoginActivity.class));
            finish();
            return;
        }

        userId = currentUser.getUid();
        db = FirebaseFirestore.getInstance();

        calendar = Calendar.getInstance();
        selectedDateStr = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(calendar.getTime());

        rvActivityLogs = findViewById(R.id.rvActivityLogs);
        rvActivityLogs.setLayoutManager(new LinearLayoutManager(this));
        rvActivityLogs.setNestedScrollingEnabled(false); // Prevents list clipping inside NestedScrollView
        btnLogActivity = findViewById(R.id.btnLogActivity);
        btnLogout = findViewById(R.id.btnLogout);
        btnSelectDate = findViewById(R.id.btnSelectDate);
        btnSettings = findViewById(R.id.btnSettings);
        btnReportOutage = findViewById(R.id.btnReportOutage);
        btnAnalytics = findViewById(R.id.btnAnalytics);

        tvTotalCo2 = findViewById(R.id.tvTotalCo2);
        tvEcoSuggestion = findViewById(R.id.tvEcoSuggestion);
        tvBudgetStatus = findViewById(R.id.tvBudgetStatus);
        tvSummaryTitle = findViewById(R.id.tvSummaryTitle);
        pbDailyBudget = findViewById(R.id.pbDailyBudget);

        rvActivityLogs.setLayoutManager(new LinearLayoutManager(this));
        logList = new ArrayList<>();
        allFetchedLogs = new ArrayList<>();

        adapter = new ActivityLogAdapter(logList, new ActivityLogAdapter.OnLogActionListener() {
            @Override
            public void onEdit(ActivityLog log) {
                if (log.getTitle().contains("Baseload")) {
                    showReportOutageDialog();
                } else {
                    showEditActivityDialog(log);
                }
            }

            @Override
            public void onDelete(ActivityLog log) {
                confirmDeleteLog(log);
            }
        });
        rvActivityLogs.setAdapter(adapter);

        btnLogActivity.setOnClickListener(v -> showAddActivityDialog());
        btnSettings.setOnClickListener(v -> startActivity(new Intent(DashboardActivity.this, SettingsActivity.class)));
        btnReportOutage.setOnClickListener(v -> showReportOutageDialog());
        btnAnalytics.setOnClickListener(v -> startActivity(new Intent(DashboardActivity.this, AnalyticsActivity.class)));

        btnLogout.setOnClickListener(v -> {
            mAuth.signOut();
            startActivity(new Intent(DashboardActivity.this, LoginActivity.class));
            finish();
        });

        btnSelectDate.setOnClickListener(v -> showDatePickerDialog());

        Intent serviceIntent = new Intent(this, CarbonGoalService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        checkAndLoadUserSettings();
        loadFirestoreLogs();
    }

    private void checkAndLoadUserSettings() {
        db.collection("users").document(userId).collection("config").document("settings")
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        startActivity(new Intent(DashboardActivity.this, SettingsActivity.class));
                    } else {
                        UserSettings settings = doc.toObject(UserSettings.class);
                        if (settings != null) {
                            userGreenPercent = settings.getGreenEnergyPercent();
                            if (settings.getAppliances() != null) {
                                userAppliances = settings.getAppliances();
                            }
                            ReminderScheduler.scheduleAllReminders(DashboardActivity.this, settings);
                        }
                    }
                });
    }

    private void showDatePickerDialog() {
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    calendar.set(Calendar.YEAR, year);
                    calendar.set(Calendar.MONTH, month);
                    calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);

                    String todayStr = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(new Date());
                    selectedDateStr = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(calendar.getTime());

                    btnSelectDate.setText(selectedDateStr.equals(todayStr) ? "📅 Today" : "📅 " + selectedDateStr);
                    filterLogsBySelectedDate();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );

        datePickerDialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        datePickerDialog.show();
    }

    private void loadFirestoreLogs() {
        db.collection("users").document(userId).collection("activity_logs")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;

                    allFetchedLogs.clear();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        ActivityLog log = doc.toObject(ActivityLog.class);
                        if (log != null) {
                            allFetchedLogs.add(log);
                        }
                    }
                    filterLogsBySelectedDate();
                });
    }

    private void filterLogsBySelectedDate() {
        logList.clear();
        double sumCo2ForDay = 0.0;

        for (ActivityLog log : allFetchedLogs) {
            if (selectedDateStr.equals(log.getDate())) {
                logList.add(log);
                sumCo2ForDay += log.getCo2Emitted();
            }
        }
        adapter.notifyDataSetChanged();

        currentTotalCo2 = sumCo2ForDay;
        tvSummaryTitle.setText("Emissions for " + selectedDateStr);
        updateBudgetProgress();

        if (!logList.isEmpty()) {
            ActivityLog highestLog = logList.get(0);
            for (ActivityLog log : logList) {
                if (log.getCo2Emitted() > highestLog.getCo2Emitted()) {
                    highestLog = log;
                }
            }

            double co2 = highestLog.getCo2Emitted();
            String category = highestLog.getCategory();
            String title = highestLog.getTitle();

            double estimatedMetric = 1.0;
            if ("Energy".equalsIgnoreCase(category)) {
                int watts = userAppliances.containsKey(title) ? userAppliances.get(title).getWattage() : CarbonCalculator.DEFAULT_APPLIANCE_WATTS.getOrDefault(title, 100);
                double effectiveFactor = CarbonCalculator.DEFAULT_GRID_CO2_PER_KWH * (1.0 - (userGreenPercent / 100.0));
                estimatedMetric = (effectiveFactor > 0) ? (co2 / effectiveFactor) * (1000.0 / watts) : 1.0;
            } else if ("Transport".equalsIgnoreCase(category)) {
                double factor = CarbonCalculator.CO2_PER_KM_CAR;
                if (title.contains("Bus")) factor = CarbonCalculator.CO2_PER_KM_BUS;
                else if (title.contains("Motorbike")) factor = CarbonCalculator.CO2_PER_KM_BIKE;
                estimatedMetric = (factor > 0) ? (co2 / factor) : 0.0;
            } else if ("Meals".equalsIgnoreCase(category)) {
                double factor = CarbonCalculator.CO2_PER_MEAL_BEEF;
                if (title.contains("Poultry") || title.contains("Low Meat")) factor = CarbonCalculator.CO2_PER_MEAL_POULTRY;
                else if (title.contains("Vegetarian")) factor = CarbonCalculator.CO2_PER_MEAL_VEGETARIAN;
                else if (title.contains("Vegan")) factor = CarbonCalculator.CO2_PER_MEAL_VEGAN;
                estimatedMetric = Math.max(1, Math.round(co2 / factor));
            }

            String suggestion = EcoSuggestionEngine.getSuggestion(category, title, estimatedMetric, co2);
            tvEcoSuggestion.setText("Largest Footprint (" + title + "):\n" + suggestion);
        } else {
            tvEcoSuggestion.setText("No activities logged for " + selectedDateStr + ". Tap + to add logs!");
        }
    }

    private void updateBudgetProgress() {
        tvTotalCo2.setText(String.format(Locale.getDefault(), "%.1f kg CO₂", currentTotalCo2));

        double budget = CarbonCalculator.DAILY_CARBON_BUDGET_KG;
        int percentage = (int) Math.min(100, (currentTotalCo2 / budget) * 100);

        pbDailyBudget.setProgress(percentage);
        tvBudgetStatus.setText(String.format(Locale.getDefault(),
                "Daily Budget: %.1f / %.1f kg CO₂ (%d%% used)", currentTotalCo2, budget, percentage));
    }

    private List<String> getAvailableManualApplianceNames() {
        List<String> list = new ArrayList<>();
        if (userAppliances != null && !userAppliances.isEmpty()) {
            for (ApplianceConfig config : userAppliances.values()) {
                if (config.isOwned() && !config.isAlwaysOn()) {
                    list.add(config.getName());
                }
            }
        }
        if (list.isEmpty()) {
            for (String name : CarbonCalculator.getSortedApplianceList()) {
                ApplianceConfig cfg = userAppliances.get(name);
                if (cfg == null || !cfg.isAlwaysOn()) {
                    list.add(name);
                }
            }
        }
        Collections.sort(list);
        return list;
    }

    private void showReportOutageDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Report Power Outage");
        builder.setMessage("Enter blackout hours experienced on " + selectedDateStr + " to adjust Baseload:");

        final EditText etOutage = new EditText(this);
        etOutage.setHint("Outage hours (e.g., 2.5)");
        etOutage.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etOutage.setPadding(32, 24, 32, 24);
        builder.setView(etOutage);

        builder.setPositiveButton("Update Baseload", (dialog, which) -> {
            String input = etOutage.getText().toString().trim();
            if (TextUtils.isEmpty(input)) return;

            double outageHours = Double.parseDouble(input);
            if (outageHours < 0.0 || outageHours > 24.0) {
                Toast.makeText(this, "Outage hours must be between 0.0 and 24.0", Toast.LENGTH_SHORT).show();
                return;
            }

            updateBaseloadForSelectedDate(outageHours);
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private void updateBaseloadForSelectedDate(double outageHours) {
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
        double updatedCo2 = CarbonCalculator.calculateElectricityCo2(totalAlwaysOnWatts, activeHours, userGreenPercent);

        String docId = "baseline_" + new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(calendar.getTime());
        long timestamp = calendar.getTimeInMillis();

        ActivityLog baseloadLog = new ActivityLog(
                docId,
                "Energy",
                "Baseload (Always-On Devices)",
                updatedCo2,
                selectedDateStr,
                timestamp
        );

        db.collection("users").document(userId)
                .collection("activity_logs")
                .document(docId)
                .set(baseloadLog)
                .addOnSuccessListener(aVoid -> Toast.makeText(this,
                        String.format(Locale.getDefault(), "Baseload updated (%.1fh active run time)", activeHours),
                        Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to update baseload: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void showAddActivityDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.activity_dialog_add_log, null);

        Spinner spCategory = dialogView.findViewById(R.id.spCategory);
        Spinner spAppliance = dialogView.findViewById(R.id.spAppliance);
        Spinner spTransportMode = dialogView.findViewById(R.id.spTransportMode);
        Spinner spMealType = dialogView.findViewById(R.id.spMealType);

        EditText etHoursUsed = dialogView.findViewById(R.id.etHoursUsed);
        EditText etDistance = dialogView.findViewById(R.id.etDistance);
        EditText etMealCount = dialogView.findViewById(R.id.etMealCount);

        LinearLayout layoutElectricity = dialogView.findViewById(R.id.layoutElectricity);
        LinearLayout layoutTransport = dialogView.findViewById(R.id.layoutTransport);
        LinearLayout layoutMeals = dialogView.findViewById(R.id.layoutMeals);

        spCategory.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"Energy", "Transport", "Meals"}));
        spAppliance.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, getAvailableManualApplianceNames()));
        spTransportMode.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"Car", "Bus", "Motorbike", "Bicycle / Walking"}));
        spMealType.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"High Meat (Beef / Lamb)", "Low Meat (Poultry / Fish)", "Vegetarian", "Vegan"}));

        spCategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                layoutElectricity.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
                layoutTransport.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
                layoutMeals.setVisibility(position == 2 ? View.VISIBLE : View.GONE);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        builder.setView(dialogView)
                .setPositiveButton("Calculate & Log", (dialog, which) -> {
                    String category = spCategory.getSelectedItem().toString();
                    double calculatedCo2 = 0.0;
                    String title = "";

                    if ("Energy".equalsIgnoreCase(category)) {
                        String applianceName = spAppliance.getSelectedItem().toString();
                        int watts = userAppliances.containsKey(applianceName) ? userAppliances.get(applianceName).getWattage() : CarbonCalculator.DEFAULT_APPLIANCE_WATTS.getOrDefault(applianceName, 100);

                        String hoursStr = etHoursUsed.getText().toString().trim();
                        if (TextUtils.isEmpty(hoursStr)) {
                            Toast.makeText(this, "Please enter hours used", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        double activeHours = Double.parseDouble(hoursStr);
                        if (activeHours <= 0 || activeHours > 24.0) {
                            Toast.makeText(this, "Hours used must be between 0.1 and 24.0 hours", Toast.LENGTH_LONG).show();
                            return;
                        }

                        calculatedCo2 = CarbonCalculator.calculateElectricityCo2(watts, activeHours, userGreenPercent);
                        title = applianceName;

                    } else if ("Transport".equalsIgnoreCase(category)) {
                        String mode = spTransportMode.getSelectedItem().toString();
                        String distStr = etDistance.getText().toString().trim();

                        if (TextUtils.isEmpty(distStr)) {
                            Toast.makeText(this, "Please enter distance traveled", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        double dist = Double.parseDouble(distStr);
                        if (dist <= 0) {
                            Toast.makeText(this, "Distance must be greater than 0 km", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        calculatedCo2 = CarbonCalculator.calculateTransportCo2(mode, dist);
                        title = mode + " Travel";

                    } else { // Meals
                        String mealType = spMealType.getSelectedItem().toString();
                        String countStr = etMealCount.getText().toString().trim();

                        if (TextUtils.isEmpty(countStr)) {
                            Toast.makeText(this, "Please enter meal count", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        int count = Integer.parseInt(countStr);
                        if (count <= 0) {
                            Toast.makeText(this, "Meal count must be at least 1", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        calculatedCo2 = CarbonCalculator.calculateMealCo2(mealType, count);
                        title = mealType + " Meal";
                    }

                    saveActivityLog(title, category, calculatedCo2);
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .create()
                .show();
    }

    private void showEditActivityDialog(ActivityLog log) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.activity_dialog_add_log, null);

        Spinner spCategory = dialogView.findViewById(R.id.spCategory);
        Spinner spAppliance = dialogView.findViewById(R.id.spAppliance);
        Spinner spTransportMode = dialogView.findViewById(R.id.spTransportMode);
        Spinner spMealType = dialogView.findViewById(R.id.spMealType);

        EditText etHoursUsed = dialogView.findViewById(R.id.etHoursUsed);
        EditText etDistance = dialogView.findViewById(R.id.etDistance);
        EditText etMealCount = dialogView.findViewById(R.id.etMealCount);

        LinearLayout layoutElectricity = dialogView.findViewById(R.id.layoutElectricity);
        LinearLayout layoutTransport = dialogView.findViewById(R.id.layoutTransport);
        LinearLayout layoutMeals = dialogView.findViewById(R.id.layoutMeals);

        spCategory.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"Energy", "Transport", "Meals"}));
        spAppliance.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, getAvailableManualApplianceNames()));
        spTransportMode.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"Car", "Bus", "Motorbike", "Bicycle / Walking"}));
        spMealType.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"High Meat (Beef / Lamb)", "Low Meat (Poultry / Fish)", "Vegetarian", "Vegan"}));

        int selectedIndex = 0;
        if ("Transport".equalsIgnoreCase(log.getCategory())) selectedIndex = 1;
        else if ("Meals".equalsIgnoreCase(log.getCategory())) selectedIndex = 2;

        spCategory.setSelection(selectedIndex);
        layoutElectricity.setVisibility(selectedIndex == 0 ? View.VISIBLE : View.GONE);
        layoutTransport.setVisibility(selectedIndex == 1 ? View.VISIBLE : View.GONE);
        layoutMeals.setVisibility(selectedIndex == 2 ? View.VISIBLE : View.GONE);

        spCategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                layoutElectricity.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
                layoutTransport.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
                layoutMeals.setVisibility(position == 2 ? View.VISIBLE : View.GONE);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        builder.setView(dialogView)
                .setTitle("Edit Activity Log")
                .setPositiveButton("Update Log", (dialog, which) -> {
                    String category = spCategory.getSelectedItem().toString();
                    double calculatedCo2 = 0.0;
                    String title = "";

                    if ("Energy".equalsIgnoreCase(category)) {
                        String applianceName = spAppliance.getSelectedItem().toString();
                        int watts = userAppliances.containsKey(applianceName) ? userAppliances.get(applianceName).getWattage() : CarbonCalculator.DEFAULT_APPLIANCE_WATTS.getOrDefault(applianceName, 100);

                        String hoursStr = etHoursUsed.getText().toString().trim();
                        if (TextUtils.isEmpty(hoursStr)) {
                            Toast.makeText(this, "Please enter hours used", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        double activeHours = Double.parseDouble(hoursStr);
                        if (activeHours <= 0 || activeHours > 24.0) {
                            Toast.makeText(this, "Hours used must be between 0.1 and 24.0 hours", Toast.LENGTH_LONG).show();
                            return;
                        }

                        calculatedCo2 = CarbonCalculator.calculateElectricityCo2(watts, activeHours, userGreenPercent);
                        title = applianceName;

                    } else if ("Transport".equalsIgnoreCase(category)) {
                        String mode = spTransportMode.getSelectedItem().toString();
                        String distStr = etDistance.getText().toString().trim();

                        if (TextUtils.isEmpty(distStr)) {
                            Toast.makeText(this, "Please enter distance traveled", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        double dist = Double.parseDouble(distStr);
                        if (dist <= 0) {
                            Toast.makeText(this, "Distance must be greater than 0 km", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        calculatedCo2 = CarbonCalculator.calculateTransportCo2(mode, dist);
                        title = mode + " Travel";

                    } else { // Meals
                        String mealType = spMealType.getSelectedItem().toString();
                        String countStr = etMealCount.getText().toString().trim();

                        if (TextUtils.isEmpty(countStr)) {
                            Toast.makeText(this, "Please enter meal count", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        int count = Integer.parseInt(countStr);
                        if (count <= 0) {
                            Toast.makeText(this, "Meal count must be at least 1", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        calculatedCo2 = CarbonCalculator.calculateMealCo2(mealType, count);
                        title = mealType + " Meal";
                    }

                    updateExistingActivityLog(log, title, category, calculatedCo2);
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .create()
                .show();
    }

    private void confirmDeleteLog(ActivityLog log) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Activity")
                .setMessage("Are you sure you want to delete \"" + log.getTitle() + "\"?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    db.collection("users").document(userId)
                            .collection("activity_logs")
                            .document(log.getId())
                            .delete()
                            .addOnSuccessListener(aVoid -> Toast.makeText(DashboardActivity.this, "Log deleted", Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e -> Toast.makeText(DashboardActivity.this, "Delete failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void saveActivityLog(String title, String category, double co2Amount) {
        String logId = db.collection("users").document(userId).collection("activity_logs").document().getId();
        long timestamp = System.currentTimeMillis();

        ActivityLog newLog = new ActivityLog(logId, category, title, co2Amount, selectedDateStr, timestamp);

        db.collection("users").document(userId)
                .collection("activity_logs")
                .document(logId)
                .set(newLog)
                .addOnSuccessListener(aVoid -> Toast.makeText(DashboardActivity.this,
                        String.format(Locale.getDefault(), "Logged: %.2f kg CO₂ calculated!", co2Amount),
                        Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(DashboardActivity.this, "Failed to log: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void updateExistingActivityLog(ActivityLog oldLog, String title, String category, double newCo2Amount) {
        ActivityLog updatedLog = new ActivityLog(oldLog.getId(), category, title, newCo2Amount, oldLog.getDate(), oldLog.getTimestamp());

        db.collection("users").document(userId)
                .collection("activity_logs")
                .document(oldLog.getId())
                .set(updatedLog)
                .addOnSuccessListener(aVoid -> Toast.makeText(DashboardActivity.this, "Log updated successfully!", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(DashboardActivity.this, "Failed to update: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
}