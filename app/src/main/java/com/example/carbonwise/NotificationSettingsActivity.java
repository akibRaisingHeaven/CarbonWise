package com.example.carbonwise;

import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Spinner;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Live tracker notification plus the daily, weekly and monthly reminder schedule. */
public class NotificationSettingsActivity extends BaseSettingsActivity {

    private CheckBox cbEnableLiveTracker, cbEnableDailyReminder, cbEnableWeeklyReport, cbEnableMonthlyReport;
    private Spinner spWeeklyDay, spMonthlyDay;
    private Button btnDailyReminderTime, btnWeeklyReportTime, btnMonthlyReportTime;
    private Button btnSave;

    private int dailyHour = 20, dailyMinute = 0;
    private int weeklyHour = 20, weeklyMinute = 0;
    private int monthlyHour = 20, monthlyMinute = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings_notifications);

        cbEnableLiveTracker = findViewById(R.id.cbEnableLiveTracker);
        cbEnableDailyReminder = findViewById(R.id.cbEnableDailyReminder);
        cbEnableWeeklyReport = findViewById(R.id.cbEnableWeeklyReport);
        cbEnableMonthlyReport = findViewById(R.id.cbEnableMonthlyReport);

        spWeeklyDay = findViewById(R.id.spWeeklyDay);
        spMonthlyDay = findViewById(R.id.spMonthlyDay);

        btnDailyReminderTime = findViewById(R.id.btnDailyReminderTime);
        btnWeeklyReportTime = findViewById(R.id.btnWeeklyReportTime);
        btnMonthlyReportTime = findViewById(R.id.btnMonthlyReportTime);
        btnSave = findViewById(R.id.btnSave);

        setupSpinners();

        btnDailyReminderTime.setOnClickListener(v -> showTimePicker(0));
        btnWeeklyReportTime.setOnClickListener(v -> showTimePicker(1));
        btnMonthlyReportTime.setOnClickListener(v -> showTimePicker(2));

        // Time and day controls only matter while their reminder is switched on
        cbEnableDailyReminder.setOnCheckedChangeListener((b, checked) -> updateEnabledStates());
        cbEnableWeeklyReport.setOnCheckedChangeListener((b, checked) -> updateEnabledStates());
        cbEnableMonthlyReport.setOnCheckedChangeListener((b, checked) -> updateEnabledStates());

        btnSave.setOnClickListener(v -> saveNotifications());

        updateButtonLabels();
        updateEnabledStates();

        initScreen("Notifications", btnSave);
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

    @Override
    protected void bindSettings(UserSettings s) {
        cbEnableLiveTracker.setChecked(s.isLiveTrackerEnabled());

        cbEnableDailyReminder.setChecked(s.isReminderEnabled());
        dailyHour = s.getReminderHour();
        dailyMinute = s.getReminderMinute();

        cbEnableWeeklyReport.setChecked(s.isWeeklyReminderEnabled());
        spWeeklyDay.setSelection(Math.max(1, Math.min(7, s.getWeeklyReminderDay())) - 1);
        weeklyHour = s.getWeeklyReminderHour();
        weeklyMinute = s.getWeeklyReminderMinute();

        cbEnableMonthlyReport.setChecked(s.isMonthlyReminderEnabled());
        spMonthlyDay.setSelection(Math.max(1, Math.min(28, s.getMonthlyReminderDay())) - 1);
        monthlyHour = s.getMonthlyReminderHour();
        monthlyMinute = s.getMonthlyReminderMinute();

        updateButtonLabels();
        updateEnabledStates();
    }

    @Override
    protected String snapshot() {
        return cbEnableLiveTracker.isChecked() + "|"
                + cbEnableDailyReminder.isChecked() + "|" + dailyHour + ":" + dailyMinute + "|"
                + cbEnableWeeklyReport.isChecked() + "|" + spWeeklyDay.getSelectedItemPosition() + "|" + weeklyHour + ":" + weeklyMinute + "|"
                + cbEnableMonthlyReport.isChecked() + "|" + spMonthlyDay.getSelectedItemPosition() + "|" + monthlyHour + ":" + monthlyMinute;
    }

    private void updateEnabledStates() {
        setGroupEnabled(cbEnableDailyReminder.isChecked(), btnDailyReminderTime);
        setGroupEnabled(cbEnableWeeklyReport.isChecked(), btnWeeklyReportTime, spWeeklyDay);
        setGroupEnabled(cbEnableMonthlyReport.isChecked(), btnMonthlyReportTime, spMonthlyDay);
    }

    private void setGroupEnabled(boolean enabled, View... views) {
        for (View v : views) {
            v.setEnabled(enabled);
            v.setAlpha(enabled ? 1f : 0.4f);
        }
    }

    private void showTimePicker(int mode) {
        int initHour = (mode == 0) ? dailyHour : (mode == 1 ? weeklyHour : monthlyHour);
        int initMinute = (mode == 0) ? dailyMinute : (mode == 1 ? weeklyMinute : monthlyMinute);

        new TimePickerDialog(
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
        ).show();
    }

    private String formatTimeStr(int hour, int minute) {
        int displayHour = hour % 12;
        if (displayHour == 0) displayHour = 12;
        String amPm = hour >= 12 ? "PM" : "AM";
        return String.format(Locale.getDefault(), "%d:%02d %s", displayHour, minute, amPm);
    }

    private void updateButtonLabels() {
        btnDailyReminderTime.setText("⏰ " + formatTimeStr(dailyHour, dailyMinute) + "  ·  Change time");
        btnWeeklyReportTime.setText("⏰ " + formatTimeStr(weeklyHour, weeklyMinute) + "  ·  Change time");
        btnMonthlyReportTime.setText("⏰ " + formatTimeStr(monthlyHour, monthlyMinute) + "  ·  Change time");

        btnDailyReminderTime.setContentDescription(
                "Change daily logging reminder time. Currently " + formatTimeStr(dailyHour, dailyMinute));
        btnWeeklyReportTime.setContentDescription(
                "Change weekly summary report time. Currently " + formatTimeStr(weeklyHour, weeklyMinute));
        btnMonthlyReportTime.setContentDescription(
                "Change monthly summary report time. Currently " + formatTimeStr(monthlyHour, monthlyMinute));
    }

    private void saveNotifications() {
        if (settings == null) return;

        settings.setLiveTrackerEnabled(cbEnableLiveTracker.isChecked());

        settings.setReminderEnabled(cbEnableDailyReminder.isChecked());
        settings.setReminderHour(dailyHour);
        settings.setReminderMinute(dailyMinute);

        settings.setWeeklyReminderEnabled(cbEnableWeeklyReport.isChecked());
        settings.setWeeklyReminderDay(spWeeklyDay.getSelectedItemPosition() + 1);
        settings.setWeeklyReminderHour(weeklyHour);
        settings.setWeeklyReminderMinute(weeklyMinute);

        settings.setMonthlyReminderEnabled(cbEnableMonthlyReport.isChecked());
        settings.setMonthlyReminderDay(spMonthlyDay.getSelectedItemPosition() + 1);
        settings.setMonthlyReminderHour(monthlyHour);
        settings.setMonthlyReminderMinute(monthlyMinute);

        saveAndFinish(false, "Notification settings saved!");
    }
}
