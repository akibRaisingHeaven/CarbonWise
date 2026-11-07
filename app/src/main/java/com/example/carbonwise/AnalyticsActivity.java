package com.example.carbonwise;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AnalyticsActivity extends AppCompatActivity {

    private Button btn7Days, btn30Days;
    private TextView tvPeriodTitle, tvTotalPeriodCo2, tvDailyAverageCo2, tvCategoryBreakdown;
    private LinearLayout containerChartRows;

    private FirebaseFirestore db;
    private String userId;

    private boolean showing7Days = true;
    private final List<ActivityLog> allFetchedLogs = new ArrayList<>();
    private final Map<String, Double> historicalBudgets = new HashMap<>();
    private double activeUserBudget = 15.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analytics);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            finish();
            return;
        }

        userId = user.getUid();
        db = FirebaseFirestore.getInstance();

        btn7Days = findViewById(R.id.btn7Days);
        btn30Days = findViewById(R.id.btn30Days);
        Button btnGenerateReport = findViewById(R.id.btnGenerateReport);
        Button btnViewSavedReports = findViewById(R.id.btnViewSavedReports);
        Button btnReturnDashboard = findViewById(R.id.btnReturnDashboard);

        tvPeriodTitle = findViewById(R.id.tvPeriodTitle);
        tvTotalPeriodCo2 = findViewById(R.id.tvTotalPeriodCo2);
        tvDailyAverageCo2 = findViewById(R.id.tvDailyAverageCo2);
        tvCategoryBreakdown = findViewById(R.id.tvCategoryBreakdown);
        containerChartRows = findViewById(R.id.containerChartRows);

        if (btnReturnDashboard != null) btnReturnDashboard.setOnClickListener(v -> finish());

        if (btnGenerateReport != null) {
            btnGenerateReport.setOnClickListener(v -> {
                Intent intent = new Intent(AnalyticsActivity.this, ReportsActivity.class);
                intent.putExtra("AUTO_GENERATE", true);
                intent.putExtra("REPORT_TYPE", showing7Days ? "7_DAYS" : "30_DAYS");
                startActivity(intent);
            });
        }

        if (btnViewSavedReports != null) {
            btnViewSavedReports.setOnClickListener(v -> {
                Intent intent = new Intent(AnalyticsActivity.this, ReportsActivity.class);
                intent.putExtra("AUTO_GENERATE", false);
                startActivity(intent);
            });
        }

        if (btn7Days != null) {
            btn7Days.setOnClickListener(v -> {
                showing7Days = true;
                updateTabStyles();
                renderAnalytics();
            });
        }

        if (btn30Days != null) {
            btn30Days.setOnClickListener(v -> {
                showing7Days = false;
                updateTabStyles();
                renderAnalytics();
            });
        }

        updateTabStyles();
        fetchBudgetsAndLogs();
    }

    private void updateTabStyles() {
        if (btn7Days == null || btn30Days == null) return;

        int primaryColor = Color.parseColor("#1D4ED8");
        if (showing7Days) {
            btn7Days.setBackgroundTintList(ColorStateList.valueOf(primaryColor));
            btn7Days.setTextColor(Color.WHITE);
            btn30Days.setBackgroundTintList(ColorStateList.valueOf(Color.WHITE));
            btn30Days.setTextColor(primaryColor);
        } else {
            btn30Days.setBackgroundTintList(ColorStateList.valueOf(primaryColor));
            btn30Days.setTextColor(Color.WHITE);
            btn7Days.setBackgroundTintList(ColorStateList.valueOf(Color.WHITE));
            btn7Days.setTextColor(primaryColor);
        }
    }

    private void fetchBudgetsAndLogs() {
        if (userId == null) return;

        // Fetch User Settings for default budget fallback
        db.collection("users").document(userId).collection("config").document("settings")
                .get().addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        UserSettings s = doc.toObject(UserSettings.class);
                        if (s != null) activeUserBudget = s.getDailyBudgetKg();
                    }
                    fetchHistoricalBudgets();
                }).addOnFailureListener(e -> fetchHistoricalBudgets());
    }

    private void fetchHistoricalBudgets() {
        db.collection("users").document(userId).collection("daily_budgets")
                .get().addOnSuccessListener(snapshots -> {
                    historicalBudgets.clear();
                    if (snapshots != null) {
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            Double bKg = doc.getDouble("budgetKg");
                            if (bKg != null) {
                                historicalBudgets.put(doc.getId(), bKg);
                            }
                        }
                    }
                    fetchLogsAndRender();
                }).addOnFailureListener(e -> fetchLogsAndRender());
    }

    private void fetchLogsAndRender() {
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
                    renderAnalytics();
                });
    }

    private void renderAnalytics() {
        if (containerChartRows == null) return;
        containerChartRows.removeAllViews();

        int daysCount = showing7Days ? 7 : 30;
        if (tvPeriodTitle != null) {
            tvPeriodTitle.setText(String.format(Locale.getDefault(), "%d-Day Emission Summary", daysCount));
        }

        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
        String todayStr = sdf.format(new java.util.Date());

        Map<String, Double> dailyTotals = new HashMap<>();
        Map<String, Double> categoryTotals = new HashMap<>();
        categoryTotals.put("Electricity", 0.0);
        categoryTotals.put("Transport", 0.0);
        categoryTotals.put("Meals", 0.0);
        categoryTotals.put("Cooking", 0.0);

        List<String> dateKeys = new ArrayList<>();
        Calendar cal = Calendar.getInstance();

        for (int i = 0; i < daysCount; i++) {
            String fullDateStr = sdf.format(cal.getTime());
            dateKeys.add(fullDateStr);
            dailyTotals.put(fullDateStr, 0.0);
            cal.add(Calendar.DAY_OF_YEAR, -1);
        }

        double totalCo2 = 0.0;
        int daysOverBudget = 0;

        for (ActivityLog log : allFetchedLogs) {
            if (log != null && log.getDate() != null && dailyTotals.containsKey(log.getDate())) {
                Double currentValObj = dailyTotals.get(log.getDate());
                double currentVal = (currentValObj != null) ? currentValObj : 0.0;
                dailyTotals.put(log.getDate(), currentVal + log.getCo2Emitted());
                totalCo2 += log.getCo2Emitted();

                String cat = log.getCategory();
                if ("Energy".equalsIgnoreCase(cat)) cat = "Electricity";

                if (cat != null && categoryTotals.containsKey(cat)) {
                    Double catCurrentObj = categoryTotals.get(cat);
                    double catCurrentVal = (catCurrentObj != null) ? catCurrentObj : 0.0;
                    categoryTotals.put(cat, catCurrentVal + log.getCo2Emitted());
                }
            }
        }

        // Evaluate each date against its specific historical budget
        for (String dateKey : dateKeys) {
            double daySum = dailyTotals.getOrDefault(dateKey, 0.0);

            // Fallback logic:
            // 1. Explicit saved historical budget
            // 2. If today, activeUserBudget
            // 3. If past date with no recorded budget, legacy default of 15.0 kg
            double dayBudget;
            if (historicalBudgets.containsKey(dateKey)) {
                dayBudget = historicalBudgets.get(dateKey);
            } else if (dateKey.equals(todayStr)) {
                dayBudget = activeUserBudget;
            } else {
                dayBudget = 15.0; // Legacy default budget for unrecorded past dates
            }

            if (daySum > dayBudget) {
                daysOverBudget++;
            }
        }

        double dailyAverage = totalCo2 / daysCount;

        if (tvTotalPeriodCo2 != null) {
            tvTotalPeriodCo2.setText(String.format(Locale.getDefault(), "%.1f kg CO₂ Total", totalCo2));
        }

        if (tvDailyAverageCo2 != null) {
            tvDailyAverageCo2.setText(String.format(Locale.getDefault(), "Daily Avg: %.1f kg CO₂ | Days Over Budget: %d", dailyAverage, daysOverBudget));
        }

        double electricityVal = categoryTotals.getOrDefault("Electricity", 0.0);
        double transportVal = categoryTotals.getOrDefault("Transport", 0.0);
        double mealsVal = categoryTotals.getOrDefault("Meals", 0.0);
        double cookingVal = categoryTotals.getOrDefault("Cooking", 0.0);

        double catSum = electricityVal + transportVal + mealsVal + cookingVal;

        if (tvCategoryBreakdown != null) {
            if (catSum > 0) {
                int ePct = (int) Math.round((electricityVal / catSum) * 100);
                int tPct = (int) Math.round((transportVal / catSum) * 100);
                int mPct = (int) Math.round((mealsVal / catSum) * 100);
                int cPct = (int) Math.round((cookingVal / catSum) * 100);
                tvCategoryBreakdown.setText(String.format(Locale.getDefault(), "⚡ Electricity: %d%% | 🚗 Transport: %d%% | 🍲 Meals: %d%% | 🔥 Cooking: %d%%", ePct, tPct, mPct, cPct));
            } else {
                tvCategoryBreakdown.setText("⚡ Electricity: 0% | 🚗 Transport: 0% | 🍲 Meals: 0% | 🔥 Cooking: 0%");
            }
        }

        for (int i = 0; i < dateKeys.size(); i++) {
            String fullDateStr = dateKeys.get(i);
            Double dayCo2Obj = dailyTotals.get(fullDateStr);
            double dayCo2 = (dayCo2Obj != null) ? dayCo2Obj : 0.0;

            double dayBudget;
            if (historicalBudgets.containsKey(fullDateStr)) {
                dayBudget = historicalBudgets.get(fullDateStr);
            } else if (fullDateStr.equals(todayStr)) {
                dayBudget = activeUserBudget;
            } else {
                dayBudget = 15.0; // Legacy default budget for unrecorded past dates
            }

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(20, 16, 20, 16);
            row.setBackgroundColor(Color.WHITE);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, 12);
            row.setLayoutParams(lp);

            TextView tvDate = new TextView(this);
            tvDate.setText(String.format(Locale.getDefault(), "%s: %.1f / %.1f kg CO₂", fullDateStr, dayCo2, dayBudget));
            tvDate.setTextColor(Color.parseColor("#0F172A"));
            tvDate.setTextSize(13);
            tvDate.setTypeface(null, android.graphics.Typeface.BOLD);

            ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            pb.setMax(100);
            int progress = (int) Math.min(100, (dayCo2 / dayBudget) * 100);
            pb.setProgress(progress);

            if (dayCo2 > dayBudget) {
                pb.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#EF4444")));
            } else {
                pb.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#1D4ED8")));
            }

            row.addView(tvDate);
            row.addView(pb);
            containerChartRows.addView(row);
        }
    }
}