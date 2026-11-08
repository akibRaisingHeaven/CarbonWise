package com.example.carbonwise;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class ReportsActivity extends AppCompatActivity {

    private RecyclerView rvSavedReports;
    private ReportAdapter adapter;
    private final List<SavedReport> reportList = new ArrayList<>();
    private FirestoreRepository repository;
    private double userDailyBudgetKg = CarbonCalculator.DAILY_CARBON_BUDGET_KG;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reports);

        repository = new FirestoreRepository();
        repository.loadUserSettings(new FirestoreRepository.SettingsCallback() {
            @Override
            public void onSettingsLoaded(UserSettings settings) {
                if (settings.getDailyBudgetKg() > 0) userDailyBudgetKg = settings.getDailyBudgetKg();
            }

            @Override
            public void onNoSettingsFound() { }
        });

        rvSavedReports = findViewById(R.id.rvSavedReports);
        if (rvSavedReports != null) {
            rvSavedReports.setLayoutManager(new LinearLayoutManager(this));
        }

        adapter = new ReportAdapter(reportList, new ReportAdapter.OnReportActionListener() {
            @Override
            public void onClick(SavedReport report) {
                showReportDetailDialog(report);
            }

            @Override
            public void onDelete(SavedReport report) {
                confirmDeleteReport(report);
            }
        });

        if (rvSavedReports != null) {
            rvSavedReports.setAdapter(adapter);
        }

        TextView tvTitle = findViewById(R.id.tvScreenTitle);
        if (tvTitle != null) tvTitle.setText("In-App Saved Reports");

        TextView btnBack = findViewById(R.id.btnSettingsBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });

        Button btn7 = findViewById(R.id.btnGenerate7Days);
        Button btn30 = findViewById(R.id.btnGenerate30Days);

        if (btn7 != null) btn7.setOnClickListener(v -> generateAndSaveReport("7_DAYS"));
        if (btn30 != null) btn30.setOnClickListener(v -> generateAndSaveReport("30_DAYS"));

        loadReports();

        if (getIntent().getBooleanExtra("AUTO_GENERATE", false)) {
            String period = getIntent().getStringExtra("REPORT_TYPE");
            if (period == null) period = "7_DAYS";
            generateAndSaveReport(period);
        }
    }

    private void loadReports() {
        repository.listenToReports(new FirestoreRepository.ReportsCallback() {
            @Override
            public void onReportsLoaded(List<SavedReport> reports) {
                reportList.clear();
                reportList.addAll(reports);
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onError(String error) {
                Toast.makeText(ReportsActivity.this, "Error loading reports: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void generateAndSaveReport(String periodType) {
        int daysCount = "30_DAYS".equals(periodType) ? 30 : 7;
        repository.listenToLogs(new FirestoreRepository.LogsCallback() {
            @Override
            public void onLogsLoaded(List<ActivityLog> logs) {
                SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
                Map<String, Double> dailyTotals = new HashMap<>();
                Map<String, Double> categoryTotals = new HashMap<>();
                categoryTotals.put("Electricity", 0.0);
                categoryTotals.put("Transport", 0.0);
                categoryTotals.put("Meals", 0.0);
                categoryTotals.put("Cooking", 0.0);

                Calendar cal = Calendar.getInstance();
                for (int i = 0; i < daysCount; i++) {
                    dailyTotals.put(sdf.format(cal.getTime()), 0.0);
                    cal.add(Calendar.DAY_OF_YEAR, -1);
                }

                double totalCo2 = 0.0;
                int daysOverBudget = 0;

                for (ActivityLog log : logs) {
                    if (log != null && log.getDate() != null && dailyTotals.containsKey(log.getDate())) {
                        Double current = dailyTotals.get(log.getDate());
                        double currentVal = current != null ? current : 0.0;
                        dailyTotals.put(log.getDate(), currentVal + log.getCo2Emitted());
                        totalCo2 += log.getCo2Emitted();

                        String cat = log.getCategory();
                        if ("Energy".equalsIgnoreCase(cat)) cat = "Electricity";

                        if (cat != null && categoryTotals.containsKey(cat)) {
                            Double catCurrent = categoryTotals.get(cat);
                            double catVal = catCurrent != null ? catCurrent : 0.0;
                            categoryTotals.put(cat, catVal + log.getCo2Emitted());
                        }
                    }
                }

                for (Double val : dailyTotals.values()) {
                    if (val != null && val > userDailyBudgetKg) {
                        daysOverBudget++;
                    }
                }

                double dailyAvg = totalCo2 / daysCount;
                String reportId = UUID.randomUUID().toString();
                String title = daysCount + "-Day Carbon Footprint Report";
                String dateStr = sdf.format(new Date());

                double electricityCo2 = categoryTotals.getOrDefault("Electricity", 0.0);
                double transportCo2 = categoryTotals.getOrDefault("Transport", 0.0);
                double mealsCo2 = categoryTotals.getOrDefault("Meals", 0.0);
                double cookingCo2 = categoryTotals.getOrDefault("Cooking", 0.0);

                SavedReport report = new SavedReport(
                        reportId, title, periodType, dateStr, System.currentTimeMillis(),
                        totalCo2, dailyAvg, daysOverBudget,
                        electricityCo2, transportCo2, mealsCo2, cookingCo2
                );

                repository.saveGeneratedReport(report, new FirestoreRepository.OperationCallback() {
                    @Override
                    public void onSuccess(String message) {
                        Toast.makeText(ReportsActivity.this, message, Toast.LENGTH_SHORT).show();
                        showReportDetailDialog(report);
                    }

                    @Override
                    public void onError(String error) {
                        Toast.makeText(ReportsActivity.this, "Save error: " + error, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String error) {
                Toast.makeText(ReportsActivity.this, "Failed to fetch logs: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showReportDetailDialog(SavedReport report) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_report_detail, null);

        TextView tvTitle = dialogView.findViewById(R.id.tvDialogTitle);
        TextView tvSub = dialogView.findViewById(R.id.tvDialogSub);
        TextView tvTotal = dialogView.findViewById(R.id.tvDialogTotalCo2);
        TextView tvAvg = dialogView.findViewById(R.id.tvDialogDailyAvg);
        TextView tvOver = dialogView.findViewById(R.id.tvDialogDaysOver);

        ProgressBar pbElectricity = dialogView.findViewById(R.id.pbElectricity);
        ProgressBar pbTransport = dialogView.findViewById(R.id.pbTransport);
        ProgressBar pbMeals = dialogView.findViewById(R.id.pbMeals);
        ProgressBar pbCooking = dialogView.findViewById(R.id.pbCooking);

        TextView tvElectricityVal = dialogView.findViewById(R.id.tvElectricityVal);
        TextView tvTransportVal = dialogView.findViewById(R.id.tvTransportVal);
        TextView tvMealsVal = dialogView.findViewById(R.id.tvMealsVal);
        TextView tvCookingVal = dialogView.findViewById(R.id.tvCookingVal);

        if (tvTitle != null) tvTitle.setText(report.getTitle());
        if (tvSub != null) tvSub.setText("Generated On: " + report.getDateGenerated());
        if (tvTotal != null) tvTotal.setText(String.format(Locale.getDefault(), "%.1f kg", report.getTotalCo2()));
        if (tvAvg != null) tvAvg.setText(String.format(Locale.getDefault(), "%.1f kg", report.getDailyAvg()));
        if (tvOver != null) tvOver.setText(String.format(Locale.getDefault(), "%d days", report.getDaysOverBudget()));

        double catSum = report.getElectricityCo2() + report.getTransportCo2() + report.getMealsCo2() + report.getCookingCo2();
        int ePct = catSum > 0 ? (int) Math.round((report.getElectricityCo2() / catSum) * 100) : 0;
        int tPct = catSum > 0 ? (int) Math.round((report.getTransportCo2() / catSum) * 100) : 0;
        int mPct = catSum > 0 ? (int) Math.round((report.getMealsCo2() / catSum) * 100) : 0;
        int cPct = catSum > 0 ? (int) Math.round((report.getCookingCo2() / catSum) * 100) : 0;

        if (pbElectricity != null) pbElectricity.setProgress(ePct);
        if (pbTransport != null) pbTransport.setProgress(tPct);
        if (pbMeals != null) pbMeals.setProgress(mPct);
        if (pbCooking != null) pbCooking.setProgress(cPct);

        if (tvElectricityVal != null) {
            tvElectricityVal.setText(String.format(Locale.getDefault(), "%.2f kg (%d%%)", report.getElectricityCo2(), ePct));
        }
        if (tvTransportVal != null) {
            tvTransportVal.setText(String.format(Locale.getDefault(), "%.2f kg (%d%%)", report.getTransportCo2(), tPct));
        }
        if (tvMealsVal != null) {
            tvMealsVal.setText(String.format(Locale.getDefault(), "%.2f kg (%d%%)", report.getMealsCo2(), mPct));
        }
        if (tvCookingVal != null) {
            tvCookingVal.setText(String.format(Locale.getDefault(), "%.2f kg (%d%%)", report.getCookingCo2(), cPct));
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        Button btnClose = dialogView.findViewById(R.id.btnCloseDialog);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
    }

    private void confirmDeleteReport(SavedReport report) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Report")
                .setMessage("Delete \"" + report.getTitle() + "\" generated on " + report.getDateGenerated() + "?")
                .setPositiveButton("Delete", (dialog, which) -> repository.deleteReport(report.getId(), new FirestoreRepository.OperationCallback() {
                    @Override public void onSuccess(String message) { Toast.makeText(ReportsActivity.this, message, Toast.LENGTH_SHORT).show(); }
                    @Override public void onError(String error) { Toast.makeText(ReportsActivity.this, "Delete failed: " + error, Toast.LENGTH_SHORT).show(); }
                }))
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }
}