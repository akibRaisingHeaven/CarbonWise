package com.example.carbonwise;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class UserSettings {
    private double greenElectricityPercent;

    // Daily Carbon Budget Settings
    private String budgetTier; // "ECO", "STANDARD", "LENIENT", "CUSTOM"
    private double customBudgetKg;
    private double dailyBudgetKg;

    // Persistent Live Service Notification
    private boolean liveTrackerEnabled;

    // Daily Reminder
    private boolean reminderEnabled;
    private int reminderHour;
    private int reminderMinute;

    // Weekly Report
    private boolean weeklyReminderEnabled;
    private int weeklyReminderDay;
    private int weeklyReminderHour;
    private int weeklyReminderMinute;

    // Monthly Report
    private boolean monthlyReminderEnabled;
    private int monthlyReminderDay;
    private int monthlyReminderHour;
    private int monthlyReminderMinute;

    private Map<String, ApplianceConfig> appliances;

    public UserSettings() {
        this.greenElectricityPercent = 0.0;
        this.budgetTier = "STANDARD";
        this.customBudgetKg = 15.0;
        this.dailyBudgetKg = 15.0;

        this.liveTrackerEnabled = true;

        this.reminderEnabled = true;
        this.reminderHour = 20;
        this.reminderMinute = 0;

        this.weeklyReminderEnabled = true;
        this.weeklyReminderDay = Calendar.SUNDAY;
        this.weeklyReminderHour = 20;
        this.weeklyReminderMinute = 0;

        this.monthlyReminderEnabled = true;
        this.monthlyReminderDay = 1;
        this.monthlyReminderHour = 20;
        this.monthlyReminderMinute = 0;

        this.appliances = new HashMap<>();
    }

    public UserSettings(double greenElectricityPercent, String budgetTier, double customBudgetKg, double dailyBudgetKg,
                        boolean liveTrackerEnabled,
                        boolean reminderEnabled, int reminderHour, int reminderMinute,
                        boolean weeklyReminderEnabled, int weeklyReminderDay, int weeklyReminderHour, int weeklyReminderMinute,
                        boolean monthlyReminderEnabled, int monthlyReminderDay, int monthlyReminderHour, int monthlyReminderMinute,
                        Map<String, ApplianceConfig> appliances) {
        this.greenElectricityPercent = greenElectricityPercent;
        this.budgetTier = budgetTier;
        this.customBudgetKg = CarbonCalculator.clampBudget(customBudgetKg);
        this.dailyBudgetKg = CarbonCalculator.clampBudget(dailyBudgetKg);
        this.liveTrackerEnabled = liveTrackerEnabled;
        this.reminderEnabled = reminderEnabled;
        this.reminderHour = reminderHour;
        this.reminderMinute = reminderMinute;
        this.weeklyReminderEnabled = weeklyReminderEnabled;
        this.weeklyReminderDay = weeklyReminderDay;
        this.weeklyReminderHour = weeklyReminderHour;
        this.weeklyReminderMinute = weeklyReminderMinute;
        this.monthlyReminderEnabled = monthlyReminderEnabled;
        this.monthlyReminderDay = monthlyReminderDay;
        this.monthlyReminderHour = monthlyReminderHour;
        this.monthlyReminderMinute = monthlyReminderMinute;
        this.appliances = appliances;
    }

    public double getGreenElectricityPercent() { return greenElectricityPercent; }
    public void setGreenElectricityPercent(double greenElectricityPercent) { this.greenElectricityPercent = greenElectricityPercent; }

    public String getBudgetTier() { return budgetTier; }
    public void setBudgetTier(String budgetTier) { this.budgetTier = budgetTier; }

    public double getCustomBudgetKg() { return customBudgetKg; }
    // Setters cap the value, so an old over-limit value stored in Firestore is capped when loaded
    public void setCustomBudgetKg(double customBudgetKg) { this.customBudgetKg = CarbonCalculator.clampBudget(customBudgetKg); }

    public double getDailyBudgetKg() { return dailyBudgetKg; }
    public void setDailyBudgetKg(double dailyBudgetKg) { this.dailyBudgetKg = CarbonCalculator.clampBudget(dailyBudgetKg); }

    public boolean isLiveTrackerEnabled() { return liveTrackerEnabled; }
    public void setLiveTrackerEnabled(boolean liveTrackerEnabled) { this.liveTrackerEnabled = liveTrackerEnabled; }

    public boolean isReminderEnabled() { return reminderEnabled; }
    public void setReminderEnabled(boolean reminderEnabled) { this.reminderEnabled = reminderEnabled; }

    public int getReminderHour() { return reminderHour; }
    public void setReminderHour(int reminderHour) { this.reminderHour = reminderHour; }

    public int getReminderMinute() { return reminderMinute; }
    public void setReminderMinute(int reminderMinute) { this.reminderMinute = reminderMinute; }

    public boolean isWeeklyReminderEnabled() { return weeklyReminderEnabled; }
    public void setWeeklyReminderEnabled(boolean weeklyReminderEnabled) { this.weeklyReminderEnabled = weeklyReminderEnabled; }

    public int getWeeklyReminderDay() { return weeklyReminderDay; }
    public void setWeeklyReminderDay(int weeklyReminderDay) { this.weeklyReminderDay = weeklyReminderDay; }

    public int getWeeklyReminderHour() { return weeklyReminderHour; }
    public void setWeeklyReminderHour(int weeklyReminderHour) { this.weeklyReminderHour = weeklyReminderHour; }

    public int getWeeklyReminderMinute() { return weeklyReminderMinute; }
    public void setWeeklyReminderMinute(int weeklyReminderMinute) { this.weeklyReminderMinute = weeklyReminderMinute; }

    public boolean isMonthlyReminderEnabled() { return monthlyReminderEnabled; }
    public void setMonthlyReminderEnabled(boolean monthlyReminderEnabled) { this.monthlyReminderEnabled = monthlyReminderEnabled; }

    public int getMonthlyReminderDay() { return monthlyReminderDay; }
    public void setMonthlyReminderDay(int monthlyReminderDay) { this.monthlyReminderDay = monthlyReminderDay; }

    public int getMonthlyReminderHour() { return monthlyReminderHour; }
    public void setMonthlyReminderHour(int monthlyReminderHour) { this.monthlyReminderHour = monthlyReminderHour; }

    public int getMonthlyReminderMinute() { return monthlyReminderMinute; }
    public void setMonthlyReminderMinute(int monthlyReminderMinute) { this.monthlyReminderMinute = monthlyReminderMinute; }

    public Map<String, ApplianceConfig> getAppliances() { return appliances; }
    public void setAppliances(Map<String, ApplianceConfig> appliances) { this.appliances = appliances; }
}