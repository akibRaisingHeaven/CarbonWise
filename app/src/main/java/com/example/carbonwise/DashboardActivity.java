package com.example.carbonwise;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
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

    private FirestoreRepository repository;
    private String userId;

    private double currentTotalCo2 = 0.0;
    private String selectedDateStr;
    private Calendar calendar;

    private double userGreenPercent = 0.0;
    // Daily budget chosen in Setup (falls back to the 15 kg default until settings load)
    private double userDailyBudgetKg = CarbonCalculator.DAILY_CARBON_BUDGET_KG;
    private Map<String, ApplianceConfig> userAppliances = new HashMap<>();

    private EditText pendingMapEditText = null;

    private final androidx.activity.result.ActivityResultLauncher<Intent> mapPickerLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null && pendingMapEditText != null) {
                    String locationName = result.getData().getStringExtra("location_name");
                    if (locationName != null && !locationName.isEmpty()) {
                        pendingMapEditText.setText(locationName);
                        // Remember the exact pin position so the route uses it instead of re-searching the name
                        if (result.getData().hasExtra("lat") && result.getData().hasExtra("lon")) {
                            double pickedLat = result.getData().getDoubleExtra("lat", 0.0);
                            double pickedLon = result.getData().getDoubleExtra("lon", 0.0);
                            pendingMapEditText.setTag(new String[]{
                                    locationName,
                                    String.format(java.util.Locale.US, "%.6f, %.6f", pickedLat, pickedLon)});
                        }
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            startActivity(new Intent(DashboardActivity.this, LoginActivity.class));
            finish();
            return;
        }

        userId = currentUser.getUid();
        repository = new FirestoreRepository();

        calendar = Calendar.getInstance();
        selectedDateStr = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(calendar.getTime());

        initViews();
        setupRecyclerView();

        btnLogActivity.setOnClickListener(v -> showAddActivityDialog());
        btnSettings.setOnClickListener(v -> startActivity(new Intent(DashboardActivity.this, SettingsActivity.class)));
        btnReportOutage.setOnClickListener(v -> showReportOutageDialog());
        btnAnalytics.setOnClickListener(v -> startActivity(new Intent(DashboardActivity.this, AnalyticsActivity.class)));

        btnLogout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(DashboardActivity.this, LoginActivity.class));
            finish();
        });

        btnSelectDate.setOnClickListener(v -> showDatePickerDialog());

        loadSettingsAndLogs();
    }

    private void initViews() {
        rvActivityLogs = findViewById(R.id.rvActivityLogs);
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
    }

    private void setupRecyclerView() {
        logList = new ArrayList<>();
        allFetchedLogs = new ArrayList<>();

        rvActivityLogs.setLayoutManager(new LinearLayoutManager(this));
        rvActivityLogs.setNestedScrollingEnabled(true);

        adapter = new ActivityLogAdapter(logList, new ActivityLogAdapter.OnLogActionListener() {
            @Override
            public void onEdit(ActivityLog log) {
                if (log.getTitle().contains("Baseload") || (log.getId() != null && log.getId().startsWith("baseline_"))) {
                    showReportOutageDialog();
                } else {
                    showEditActivityDialog(log);
                }
            }

            @Override
            public void onDelete(ActivityLog log) {
                if (log.getTitle().contains("Baseload") || (log.getId() != null && log.getId().startsWith("baseline_"))) {
                    Toast.makeText(DashboardActivity.this, "Baseload log is automated and cannot be deleted.", Toast.LENGTH_SHORT).show();
                } else {
                    confirmDeleteLog(log);
                }
            }
        });
        rvActivityLogs.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Setup now lives in separate screens that save on their own and simply finish(),
        // so pick up everything they may have changed (budget, green %, appliances) here
        if (repository != null) {
            repository.loadUserSettings(new FirestoreRepository.SettingsCallback() {
                @Override
                public void onSettingsLoaded(UserSettings settings) {
                    userGreenPercent = settings.getGreenElectricityPercent();
                    if (settings.getAppliances() != null) {
                        userAppliances = settings.getAppliances();
                    }
                    if (settings.getDailyBudgetKg() > 0) {
                        userDailyBudgetKg = settings.getDailyBudgetKg();
                    }
                    updateBudgetProgress();
                }

                @Override
                public void onNoSettingsFound() { }
            });
        }
    }

    private void loadSettingsAndLogs() {
        repository.loadUserSettings(new FirestoreRepository.SettingsCallback() {
            @Override
            public void onSettingsLoaded(UserSettings settings) {
                userGreenPercent = settings.getGreenElectricityPercent();
                if (settings.getDailyBudgetKg() > 0) {
                    userDailyBudgetKg = settings.getDailyBudgetKg();
                    updateBudgetProgress();
                }
                if (settings.getAppliances() != null) {
                    userAppliances = settings.getAppliances();
                }
                ReminderScheduler.scheduleAllReminders(DashboardActivity.this, settings);

                // Auto-check and write baseload entry if missing
                repository.ensureDailyBaseloadLogged(calendar, selectedDateStr, userAppliances, userGreenPercent);

                Intent serviceIntent = new Intent(DashboardActivity.this, CarbonGoalService.class);
                if (settings.isLiveTrackerEnabled()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent);
                    } else {
                        startService(serviceIntent);
                    }
                } else {
                    stopService(serviceIntent);
                }
            }

            @Override
            public void onNoSettingsFound() {
                startActivity(new Intent(DashboardActivity.this, SettingsActivity.class));
            }
        });

        repository.listenToLogs(new FirestoreRepository.LogsCallback() {
            @Override
            public void onLogsLoaded(List<ActivityLog> logs) {
                allFetchedLogs.clear();
                allFetchedLogs.addAll(logs);
                filterLogsBySelectedDate();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(DashboardActivity.this, "Error loading logs: " + error, Toast.LENGTH_SHORT).show();
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

                    // Auto-check and write baseload entry when switching dates
                    repository.ensureDailyBaseloadLogged(calendar, selectedDateStr, userAppliances, userGreenPercent);
                    filterLogsBySelectedDate();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );

        datePickerDialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        datePickerDialog.show();
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
        updateEcoSuggestion();
    }

    private void updateBudgetProgress() {
        tvTotalCo2.setText(String.format(Locale.getDefault(), "%.1f kg CO₂", currentTotalCo2));

        double budget = userDailyBudgetKg;
        int percentage = (int) Math.min(100, (currentTotalCo2 / budget) * 100);

        pbDailyBudget.setProgress(percentage);
        tvBudgetStatus.setText(String.format(Locale.getDefault(),
                "Daily Budget: %.1f / %.1f kg CO₂ (%d%% used)", currentTotalCo2, budget, percentage));
    }

    private void updateEcoSuggestion() {
        if (!logList.isEmpty()) {
            ActivityLog highestLog = logList.get(0);
            for (ActivityLog log : logList) {
                if (log.getCo2Emitted() > highestLog.getCo2Emitted()) highestLog = log;
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
                double factor = CarbonCalculator.calculateTransportCo2(title, 1.0);
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

    private void showAddActivityDialog() {
        ActivityDialogHelper.showAddOrEditDialog(
                this, null, userAppliances, userGreenPercent,
                targetField -> {
                    pendingMapEditText = targetField;
                    mapPickerLauncher.launch(new Intent(DashboardActivity.this, MapPickerActivity.class));
                },
                (title, category, co2) -> repository.saveLog(title, category, co2, selectedDateStr, new FirestoreRepository.OperationCallback() {
                    @Override public void onSuccess(String message) { Toast.makeText(DashboardActivity.this, message, Toast.LENGTH_SHORT).show(); }
                    @Override public void onError(String error) { Toast.makeText(DashboardActivity.this, "Failed to save: " + error, Toast.LENGTH_SHORT).show(); }
                })
        );
    }

    private void showEditActivityDialog(ActivityLog log) {
        ActivityDialogHelper.showAddOrEditDialog(
                this, log, userAppliances, userGreenPercent,
                targetField -> {
                    pendingMapEditText = targetField;
                    mapPickerLauncher.launch(new Intent(DashboardActivity.this, MapPickerActivity.class));
                },
                (title, category, co2) -> repository.updateLog(log, title, category, co2, new FirestoreRepository.OperationCallback() {
                    @Override public void onSuccess(String message) { Toast.makeText(DashboardActivity.this, message, Toast.LENGTH_SHORT).show(); }
                    @Override public void onError(String error) { Toast.makeText(DashboardActivity.this, "Failed to update: " + error, Toast.LENGTH_SHORT).show(); }
                })
        );
    }

    private void showReportOutageDialog() {
        ActivityDialogHelper.showReportOutageDialog(this, selectedDateStr, outageHours -> repository.updateBaseload(
                calendar, selectedDateStr, outageHours, userAppliances, userGreenPercent, new FirestoreRepository.OperationCallback() {
                    @Override public void onSuccess(String message) { Toast.makeText(DashboardActivity.this, message, Toast.LENGTH_SHORT).show(); }
                    @Override public void onError(String error) { Toast.makeText(DashboardActivity.this, "Failed: " + error, Toast.LENGTH_SHORT).show(); }
                }
        ));
    }

    private void confirmDeleteLog(ActivityLog log) {
        if (log.getTitle().contains("Baseload") || (log.getId() != null && log.getId().startsWith("baseline_"))) {
            Toast.makeText(this, "Baseload log is automated and cannot be deleted. Report power outage to adjust.", Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Delete Activity")
                .setMessage("Are you sure you want to delete \"" + log.getTitle() + "\"?")
                .setPositiveButton("Delete", (dialog, which) -> repository.deleteLog(log.getId(), new FirestoreRepository.OperationCallback() {
                    @Override public void onSuccess(String message) { Toast.makeText(DashboardActivity.this, message, Toast.LENGTH_SHORT).show(); }
                    @Override public void onError(String error) { Toast.makeText(DashboardActivity.this, "Delete failed: " + error, Toast.LENGTH_SHORT).show(); }
                }))
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }
}