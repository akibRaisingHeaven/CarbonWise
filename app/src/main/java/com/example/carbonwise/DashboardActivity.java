package com.example.carbonwise;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
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
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DashboardActivity extends AppCompatActivity {

    private RecyclerView rvActivityLogs;
    private Button btnLogActivity, btnLogout, btnSelectDate;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

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
        btnLogActivity = findViewById(R.id.btnLogActivity);
        btnLogout = findViewById(R.id.btnLogout);
        btnSelectDate = findViewById(R.id.btnSelectDate);
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
                showEditActivityDialog(log);
            }

            @Override
            public void onDelete(ActivityLog log) {
                confirmDeleteLog(log);
            }
        });
        rvActivityLogs.setAdapter(adapter);

        btnLogActivity.setOnClickListener(v -> showAddActivityDialog());

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

        loadFirestoreLogs();
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

                    if (selectedDateStr.equals(todayStr)) {
                        btnSelectDate.setText("📅 Today");
                    } else {
                        btnSelectDate.setText("📅 " + selectedDateStr);
                    }

                    filterLogsBySelectedDate();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );

        // Block future dates
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
            ActivityLog latest = logList.get(0);
            double co2 = latest.getCo2Emitted();
            String category = latest.getCategory();
            String title = latest.getTitle();

            double estimatedMetric = 1.0;
            if ("Energy".equalsIgnoreCase(category)) {
                int watts = CarbonCalculator.APPLIANCE_WATTS.getOrDefault(title, 100);
                estimatedMetric = (co2 / CarbonCalculator.CO2_PER_KWH) * (1000.0 / watts);
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
            tvEcoSuggestion.setText(suggestion);
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

    private void showAddActivityDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.activity_dialog_add_log, null);

        Spinner spCategory = dialogView.findViewById(R.id.spCategory);
        Spinner spAppliance = dialogView.findViewById(R.id.spAppliance);
        Spinner spTransportMode = dialogView.findViewById(R.id.spTransportMode);
        Spinner spMealType = dialogView.findViewById(R.id.spMealType);

        CheckBox cbAlwaysOn = dialogView.findViewById(R.id.cbAlwaysOn);
        EditText etHoursUsed = dialogView.findViewById(R.id.etHoursUsed);
        EditText etDistance = dialogView.findViewById(R.id.etDistance);
        EditText etMealCount = dialogView.findViewById(R.id.etMealCount);

        LinearLayout layoutElectricity = dialogView.findViewById(R.id.layoutElectricity);
        LinearLayout layoutTransport = dialogView.findViewById(R.id.layoutTransport);
        LinearLayout layoutMeals = dialogView.findViewById(R.id.layoutMeals);

        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, new String[]{"Energy", "Transport", "Meals"});
        spCategory.setAdapter(catAdapter);

        List<String> appliances = CarbonCalculator.getSortedApplianceList();
        ArrayAdapter<String> appAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, appliances);
        spAppliance.setAdapter(appAdapter);

        ArrayAdapter<String> transAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, new String[]{"Car", "Bus", "Motorbike", "Bicycle / Walking"});
        spTransportMode.setAdapter(transAdapter);

        ArrayAdapter<String> mealAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, new String[]{"High Meat (Beef / Lamb)", "Low Meat (Poultry / Fish)", "Vegetarian", "Vegan"});
        spMealType.setAdapter(mealAdapter);

        spCategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                layoutElectricity.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
                layoutTransport.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
                layoutMeals.setVisibility(position == 2 ? View.VISIBLE : View.GONE);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        cbAlwaysOn.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                etHoursUsed.setText("24");
                etHoursUsed.setEnabled(false);
            } else {
                etHoursUsed.setText("");
                etHoursUsed.setEnabled(true);
            }
        });

        builder.setView(dialogView)
                .setPositiveButton("Calculate & Log", (dialog, which) -> {
                    String category = spCategory.getSelectedItem().toString();
                    double calculatedCo2 = 0.0;
                    String title = "";

                    if ("Energy".equalsIgnoreCase(category)) {
                        String applianceName = spAppliance.getSelectedItem().toString();
                        int watts = CarbonCalculator.APPLIANCE_WATTS.getOrDefault(applianceName, 100);
                        String hoursStr = etHoursUsed.getText().toString().trim();

                        if (TextUtils.isEmpty(hoursStr)) {
                            Toast.makeText(this, "Please enter hours used", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        double hours = Double.parseDouble(hoursStr);
                        if (hours <= 0 || hours > 24.0) {
                            Toast.makeText(this, "Hours used must be between 0.1 and 24.0 hours", Toast.LENGTH_LONG).show();
                            return;
                        }

                        calculatedCo2 = CarbonCalculator.calculateElectricityCo2(watts, hours);
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

        CheckBox cbAlwaysOn = dialogView.findViewById(R.id.cbAlwaysOn);
        EditText etHoursUsed = dialogView.findViewById(R.id.etHoursUsed);
        EditText etDistance = dialogView.findViewById(R.id.etDistance);
        EditText etMealCount = dialogView.findViewById(R.id.etMealCount);

        LinearLayout layoutElectricity = dialogView.findViewById(R.id.layoutElectricity);
        LinearLayout layoutTransport = dialogView.findViewById(R.id.layoutTransport);
        LinearLayout layoutMeals = dialogView.findViewById(R.id.layoutMeals);

        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, new String[]{"Energy", "Transport", "Meals"});
        spCategory.setAdapter(catAdapter);

        List<String> appliances = CarbonCalculator.getSortedApplianceList();
        ArrayAdapter<String> appAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, appliances);
        spAppliance.setAdapter(appAdapter);

        ArrayAdapter<String> transAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, new String[]{"Car", "Bus", "Motorbike", "Bicycle / Walking"});
        spTransportMode.setAdapter(transAdapter);

        ArrayAdapter<String> mealAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, new String[]{"High Meat (Beef / Lamb)", "Low Meat (Poultry / Fish)", "Vegetarian", "Vegan"});
        spMealType.setAdapter(mealAdapter);

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

        cbAlwaysOn.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                etHoursUsed.setText("24");
                etHoursUsed.setEnabled(false);
            } else {
                etHoursUsed.setText("");
                etHoursUsed.setEnabled(true);
            }
        });

        builder.setView(dialogView)
                .setTitle("Edit Activity Log")
                .setPositiveButton("Update Log", (dialog, which) -> {
                    String category = spCategory.getSelectedItem().toString();
                    double calculatedCo2 = 0.0;
                    String title = "";

                    if ("Energy".equalsIgnoreCase(category)) {
                        String applianceName = spAppliance.getSelectedItem().toString();
                        int watts = CarbonCalculator.APPLIANCE_WATTS.getOrDefault(applianceName, 100);
                        String hoursStr = etHoursUsed.getText().toString().trim();

                        if (TextUtils.isEmpty(hoursStr)) {
                            Toast.makeText(this, "Please enter hours used", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        double hours = Double.parseDouble(hoursStr);
                        if (hours <= 0 || hours > 24.0) {
                            Toast.makeText(this, "Hours used must be between 0.1 and 24.0 hours", Toast.LENGTH_LONG).show();
                            return;
                        }

                        calculatedCo2 = CarbonCalculator.calculateElectricityCo2(watts, hours);
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