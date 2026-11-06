package com.example.carbonwise;

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
        tvPeriodTitle = findViewById(R.id.tvPeriodTitle);
        tvTotalPeriodCo2 = findViewById(R.id.tvTotalPeriodCo2);
        tvDailyAverageCo2 = findViewById(R.id.tvDailyAverageCo2);
        tvCategoryBreakdown = findViewById(R.id.tvCategoryBreakdown);
        containerChartRows = findViewById(R.id.containerChartRows);

        findViewById(R.id.btnReturnDashboard).setOnClickListener(v -> finish());

        btn7Days.setOnClickListener(v -> {
            showing7Days = true;
            updateTabStyles();
            renderAnalytics();
        });

        btn30Days.setOnClickListener(v -> {
            showing7Days = false;
            updateTabStyles();
            renderAnalytics();
        });

        fetchLogsAndRender();
    }

    private void updateTabStyles() {
        int primaryColor = Color.parseColor("#1D4ED8");
        if (showing7Days) {
            btn7Days.setBackgroundTintList(ColorStateList.valueOf(primaryColor));
            btn7Days.setTextColor(Color.WHITE);
            btn30Days.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
            btn30Days.setTextColor(primaryColor);
        } else {
            btn30Days.setBackgroundTintList(ColorStateList.valueOf(primaryColor));
            btn30Days.setTextColor(Color.WHITE);
            btn7Days.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
            btn7Days.setTextColor(primaryColor);
        }
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
        containerChartRows.removeAllViews();
        int daysCount = showing7Days ? 7 : 30;
        tvPeriodTitle.setText(daysCount + "-Day Emission Summary");

        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());

        Map<String, Double> dailyTotals = new HashMap<>();
        Map<String, Double> categoryTotals = new HashMap<>();
        categoryTotals.put("Energy", 0.0);
        categoryTotals.put("Transport", 0.0);
        categoryTotals.put("Meals", 0.0);

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
            if (dailyTotals.containsKey(log.getDate())) {
                double current = dailyTotals.get(log.getDate());
                dailyTotals.put(log.getDate(), current + log.getCo2Emitted());
                totalCo2 += log.getCo2Emitted();

                String cat = log.getCategory();
                if (cat != null && categoryTotals.containsKey(cat)) {
                    categoryTotals.put(cat, categoryTotals.get(cat) + log.getCo2Emitted());
                }
            }
        }

        for (Double daySum : dailyTotals.values()) {
            if (daySum > CarbonCalculator.DAILY_CARBON_BUDGET_KG) {
                daysOverBudget++;
            }
        }

        double dailyAverage = totalCo2 / daysCount;
        tvTotalPeriodCo2.setText(String.format(Locale.getDefault(), "%.1f kg CO₂ Total", totalCo2));
        tvDailyAverageCo2.setText(String.format(Locale.getDefault(), "Daily Avg: %.1f kg CO₂ | Days Over Budget: %d", dailyAverage, daysOverBudget));

        double catSum = categoryTotals.get("Energy") + categoryTotals.get("Transport") + categoryTotals.get("Meals");
        if (catSum > 0) {
            int ePct = (int) Math.round((categoryTotals.get("Energy") / catSum) * 100);
            int tPct = (int) Math.round((categoryTotals.get("Transport") / catSum) * 100);
            int mPct = (int) Math.round((categoryTotals.get("Meals") / catSum) * 100);
            tvCategoryBreakdown.setText(String.format(Locale.getDefault(), "⚡ Energy: %d%% | 🚗 Transport: %d%% | 🍲 Meals: %d%%", ePct, tPct, mPct));
        } else {
            tvCategoryBreakdown.setText("⚡ Energy: 0% | 🚗 Transport: 0% | 🍲 Meals: 0%");
        }

        for (int i = dateKeys.size() - 1; i >= 0; i--) {
            String fullDateStr = dateKeys.get(i);
            double dayCo2 = dailyTotals.get(fullDateStr);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(20, 16, 20, 16);
            row.setBackgroundColor(Color.WHITE);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, 12);
            row.setLayoutParams(lp);

            TextView tvDate = new TextView(this);
            tvDate.setText(String.format(Locale.getDefault(), "%s: %.1f kg CO₂", fullDateStr, dayCo2));
            tvDate.setTextColor(Color.parseColor("#0F172A"));
            tvDate.setTextSize(13);
            tvDate.setTypeface(null, android.graphics.Typeface.BOLD);

            ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            pb.setMax(100);
            int progress = (int) Math.min(100, (dayCo2 / CarbonCalculator.DAILY_CARBON_BUDGET_KG) * 100);
            pb.setProgress(progress);

            if (dayCo2 > CarbonCalculator.DAILY_CARBON_BUDGET_KG) {
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