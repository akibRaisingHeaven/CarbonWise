package com.example.carbonwise;

import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SettingsActivity extends AppCompatActivity {

    private EditText etGreenPercent;

    private CheckBox cbEnableDailyReminder, cbEnableWeeklyReport, cbEnableMonthlyReport;
    private Spinner spWeeklyDay, spMonthlyDay;
    private Button btnDailyReminderTime, btnWeeklyReportTime, btnMonthlyReportTime;

    private LinearLayout containerApplianceList;
    private FirebaseFirestore db;
    private String userId;

    private int dailyHour = 20, dailyMinute = 0;
    private int weeklyHour = 20, weeklyMinute = 0;
    private int monthlyHour = 20, monthlyMinute = 0;

    private final Map<String, View> applianceRowViews = new HashMap<>();

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

        cbEnableDailyReminder = findViewById(R.id.cbEnableDailyReminder);
        cbEnableWeeklyReport = findViewById(R.id.cbEnableWeeklyReport);
        cbEnableMonthlyReport = findViewById(R.id.cbEnableMonthlyReport);

        spWeeklyDay = findViewById(R.id.spWeeklyDay);
        spMonthlyDay = findViewById(R.id.spMonthlyDay);

        btnDailyReminderTime = findViewById(R.id.btnDailyReminderTime);
        btnWeeklyReportTime = findViewById(R.id.btnWeeklyReportTime);
        btnMonthlyReportTime = findViewById(R.id.btnMonthlyReportTime);

        containerApplianceList = findViewById(R.id.containerApplianceList);

        setupSpinners();

        btnDailyReminderTime.setOnClickListener(v -> showTimePicker(0));
        btnWeeklyReportTime.setOnClickListener(v -> showTimePicker(1));
        btnMonthlyReportTime.setOnClickListener(v -> showTimePicker(2));

        findViewById(R.id.btnSaveSettings).setOnClickListener(v -> saveUserSettings());

        populateApplianceRows();
        loadExistingSettings();
    }

    private void setupSpinners() {
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
                            etGreenPercent.setText(String.valueOf(settings.getGreenEnergyPercent()));

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
                                        holder.cbOwned.setChecked(entry.getValue().isOwned());
                                        holder.etWatt.setText(String.valueOf(entry.getValue().getWattage()));
                                        holder.cbAlwaysOn.setChecked(entry.getValue().isAlwaysOn());
                                    }
                                }
                            }
                        }
                    } else {
                        updateButtonLabels();
                    }
                });
    }

    private void saveUserSettings() {
        String greenStr = etGreenPercent.getText().toString().trim();
        double greenPercent = TextUtils.isEmpty(greenStr) ? 0.0 : Double.parseDouble(greenStr);

        if (greenPercent < 0 || greenPercent > 100) {
            Toast.makeText(this, "Green energy ratio must be between 0% and 100%", Toast.LENGTH_SHORT).show();
            return;
        }

        int selectedWeeklyDay = spWeeklyDay.getSelectedItemPosition() + 1;
        int selectedMonthlyDay = spMonthlyDay.getSelectedItemPosition() + 1;

        Map<String, ApplianceConfig> applianceMap = new HashMap<>();
        for (Map.Entry<String, View> entry : applianceRowViews.entrySet()) {
            ApplianceRowHolder holder = (ApplianceRowHolder) entry.getValue().getTag();
            boolean isOwned = holder.cbOwned.isChecked();
            String wattText = holder.etWatt.getText().toString().trim();
            int watt = TextUtils.isEmpty(wattText) ? 100 : Integer.parseInt(wattText);
            boolean alwaysOn = holder.cbAlwaysOn.isChecked();

            applianceMap.put(entry.getKey(), new ApplianceConfig(entry.getKey(), watt, alwaysOn, isOwned));
        }

        UserSettings settings = new UserSettings(
                greenPercent,
                cbEnableDailyReminder.isChecked(), dailyHour, dailyMinute,
                cbEnableWeeklyReport.isChecked(), selectedWeeklyDay, weeklyHour, weeklyMinute,
                cbEnableMonthlyReport.isChecked(), selectedMonthlyDay, monthlyHour, monthlyMinute,
                applianceMap
        );

        db.collection("users").document(userId).collection("config").document("settings")
                .set(settings)
                .addOnSuccessListener(aVoid -> {
                    ReminderScheduler.scheduleAllReminders(SettingsActivity.this, settings);
                    Toast.makeText(SettingsActivity.this, "Settings & Schedules Saved!", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(SettingsActivity.this, DashboardActivity.class));
                    finish();
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