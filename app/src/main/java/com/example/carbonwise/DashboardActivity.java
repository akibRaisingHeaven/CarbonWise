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
    private TextView tvTotalCo2, tvEcoSuggestion, tvBudgetStatus, tvBudgetRemaining, tvBudgetState, tvSummaryTitle;
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
    // Historical daily budgets are keyed by the same date text used by activity logs.
    // This keeps a settings change from being applied retroactively to previously selected days.
    private final Map<String, Double> savedDailyBudgets = new HashMap<>();
    private boolean dailyBudgetsLoaded;
    private Map<String, ApplianceConfig> userAppliances = new HashMap<>();

    private EditText pendingMapEditText = null;
    private boolean pendingQuickLog;
    private boolean settingsLoaded;

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

        pendingQuickLog = getIntent() != null
                && getIntent().getBooleanExtra(CarbonWidgetProvider.EXTRA_OPEN_QUICK_LOG, false);

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
            // Stop the live tracker and clear what the widget shows for the signed-out user
            stopService(new Intent(DashboardActivity.this, CarbonGoalService.class));
            CarbonSummaryCache.clear(DashboardActivity.this);
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
        tvBudgetRemaining = findViewById(R.id.tvBudgetRemaining);
        tvBudgetState = findViewById(R.id.tvBudgetState);
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
            loadHistoricalDailyBudgets();
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
                    settingsLoaded = true;
                    updateBudgetProgress();
                    publishWidgetSummary();
                    maybeOpenPendingQuickLog();
                }

                @Override
                public void onNoSettingsFound() { }
            });
        }
    }

    /** Loads the per-day budget history used by Analytics and report generation. */
    private void loadHistoricalDailyBudgets() {
        repository.loadDailyBudgets(new FirestoreRepository.BudgetsCallback() {
            @Override
            public void onBudgetsLoaded(Map<String, Double> budgetsByDate) {
                if (isFinishing() || isDestroyed()) return;
                savedDailyBudgets.clear();
                if (budgetsByDate != null) savedDailyBudgets.putAll(budgetsByDate);
                dailyBudgetsLoaded = true;
                updateBudgetProgress();
            }

            @Override
            public void onError(String error) {
                if (isFinishing() || isDestroyed()) return;
                // Keep the resolver's legacy fallback for dates without a stored budget.
                dailyBudgetsLoaded = true;
                updateBudgetProgress();
            }
        });
    }

    private void loadSettingsAndLogs() {
        repository.loadUserSettings(new FirestoreRepository.SettingsCallback() {
            @Override
            public void onSettingsLoaded(UserSettings settings) {
                userGreenPercent = settings.getGreenElectricityPercent();
                if (settings.getDailyBudgetKg() > 0) {
                    userDailyBudgetKg = settings.getDailyBudgetKg();
                    updateBudgetProgress();
                    publishWidgetSummary();
                }
                if (settings.getAppliances() != null) {
                    userAppliances = settings.getAppliances();
                }
                settingsLoaded = true;
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
                maybeOpenPendingQuickLog();
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
                logsLoadedOnce = true;
                filterLogsBySelectedDate();
                publishWidgetSummary();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(DashboardActivity.this, "Error loading logs: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.getBooleanExtra(CarbonWidgetProvider.EXTRA_OPEN_QUICK_LOG, false)) {
            pendingQuickLog = true;
            calendar = Calendar.getInstance();
            selectedDateStr = CarbonSummaryCache.todayString();
            if (btnSelectDate != null) btnSelectDate.setText("📅 Today");
            if (logsLoadedOnce) filterLogsBySelectedDate();
            maybeOpenPendingQuickLog();
        }
    }

    private void maybeOpenPendingQuickLog() {
        if (!pendingQuickLog || !settingsLoaded || isFinishing()) return;
        pendingQuickLog = false;
        // The same validated, category-aware entry dialog used by the dashboard is reused.
        new android.os.Handler(getMainLooper()).postDelayed(this::showAddActivityDialog, 180);
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

    private boolean logsLoadedOnce = false;

    /** Publishes today's, week-to-date and month-to-date totals for the widget/provider. */
    private void publishWidgetSummary() {
        if (!logsLoadedOnce) return;
        Calendar now = Calendar.getInstance();
        Calendar todayStart = (Calendar) now.clone();
        todayStart.set(Calendar.HOUR_OF_DAY, 0);
        todayStart.set(Calendar.MINUTE, 0);
        todayStart.set(Calendar.SECOND, 0);
        todayStart.set(Calendar.MILLISECOND, 0);
        long todayMillis = todayStart.getTimeInMillis();
        String today = CarbonSummaryCache.todayString();

        Calendar weekStart = (Calendar) todayStart.clone();
        weekStart.setFirstDayOfWeek(Calendar.MONDAY);
        int dayOfWeek = weekStart.get(Calendar.DAY_OF_WEEK);
        int daysFromMonday = (dayOfWeek + 5) % 7;
        weekStart.add(Calendar.DAY_OF_YEAR, -daysFromMonday);

        Calendar monthStart = (Calendar) todayStart.clone();
        monthStart.set(Calendar.DAY_OF_MONTH, 1);

        double todayTotal = 0.0;
        double weekTotal = 0.0;
        double monthTotal = 0.0;
        Map<String, Double> categories = new HashMap<>();

        for (ActivityLog log : allFetchedLogs) {
            Calendar logDay = parseLogDay(log);
            if (logDay == null) continue;
            long logMillis = logDay.getTimeInMillis();
            double value = Math.max(0.0, log.getCo2Emitted());

            if (logMillis == todayMillis) {
                todayTotal += value;
                String category = log.getCategory() == null ? "Other" : log.getCategory();
                categories.merge(category, value, Double::sum);
            }
            if (logMillis >= weekStart.getTimeInMillis() && logMillis <= todayMillis) weekTotal += value;
            if (logDay.get(Calendar.YEAR) == todayStart.get(Calendar.YEAR)
                    && logDay.get(Calendar.MONTH) == todayStart.get(Calendar.MONTH)
                    && logMillis <= todayMillis) monthTotal += value;
        }

        int daysThisWeek = Math.max(1, daysBetweenDays(weekStart, todayStart) + 1);
        int daysThisMonth = todayStart.get(Calendar.DAY_OF_MONTH);
        double weekBudget = userDailyBudgetKg * daysThisWeek;
        double monthBudget = userDailyBudgetKg * daysThisMonth;
        String weekLabel = "Week to date · " + new SimpleDateFormat("MMM d", Locale.getDefault()).format(weekStart.getTime());
        String monthLabel = "Month to date · " + new SimpleDateFormat("MMMM", Locale.getDefault()).format(todayStart.getTime());

        CarbonSummaryCache.publish(this, today, todayTotal, userDailyBudgetKg, categories,
                weekTotal, weekBudget, weekLabel, monthTotal, monthBudget, monthLabel);
    }

    private Calendar parseLogDay(ActivityLog log) {
        if (log == null) return null;
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

    private int daysBetweenDays(Calendar from, Calendar to) {
        Calendar cursor = (Calendar) from.clone();
        int days = 0;
        while (cursor.before(to)) {
            cursor.add(Calendar.DAY_OF_YEAR, 1);
            days++;
        }
        return days;
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

        String todayKey = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(new Date());
        // Always use today's current setting for today; historical dates use their saved budget.
        // Until the history request completes, avoid briefly presenting today's budget for an old date.
        if (!dailyBudgetsLoaded && selectedDateStr != null && !selectedDateStr.equals(todayKey)) {
            tvBudgetStatus.setText("Loading saved daily budget…");
            tvBudgetRemaining.setText(" ");
            tvBudgetState.setText(" ");
            pbDailyBudget.setProgress(0);
            return;
        }
        double budget = Math.max(0.0, BudgetResolver.budgetForDate(
                savedDailyBudgets, selectedDateStr, todayKey, userDailyBudgetKg));
        int percentage = budget > 0
                ? (int) Math.round(Math.max(0.0, currentTotalCo2) / budget * 100.0) : 0;
        pbDailyBudget.setProgress(Math.max(0, Math.min(100, percentage)));

        tvBudgetStatus.setText(budget > 0
                ? String.format(Locale.getDefault(), "Daily Budget: %.1f / %.1f kg CO₂ (%d%% used)",
                        currentTotalCo2, budget, percentage)
                : "Daily budget is not configured");

        CarbonBudgetStatus status = CarbonBudgetStatus.from(currentTotalCo2, budget);
        tvBudgetState.setText(status.getLabel());
        tvBudgetState.setTextColor(status.getDashboardColor());

        String remainingText;
        if (budget <= 0) {
            remainingText = "Set a daily budget";
        } else if (currentTotalCo2 > budget) {
            remainingText = String.format(Locale.getDefault(), "%.1f kg CO₂ over budget",
                    currentTotalCo2 - budget);
        } else {
            remainingText = String.format(Locale.getDefault(), "%.1f kg CO₂ remaining",
                    budget - currentTotalCo2);
        }
        tvBudgetRemaining.setText(remainingText);
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
            if ("Electricity".equalsIgnoreCase(category) || "Energy".equalsIgnoreCase(category)) {
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