package com.example.carbonwise;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class UserSettings {
    private double greenEnergyPercent;

    // Daily Reminder
    private boolean reminderEnabled;
    private int reminderHour;
    private int reminderMinute;

    // Weekly Report
    private boolean weeklyReminderEnabled;
    private int weeklyReminderDay; // 1 = Sunday, 2 = Monday, ... 7 = Saturday
    private int weeklyReminderHour;
    private int weeklyReminderMinute;

    // Monthly Report
    private boolean monthlyReminderEnabled;
    private int monthlyReminderDay; // 1 to 28
    private int monthlyReminderHour;
    private int monthlyReminderMinute;

    private Map<String, ApplianceConfig> appliances;

    public UserSettings() {
        this.greenEnergyPercent = 0.0;

        this.reminderEnabled = true;
        this.reminderHour = 20; // 8:00 PM
        this.reminderMinute = 0;

        this.weeklyReminderEnabled = true;
        this.weeklyReminderDay = Calendar.SUNDAY; // Default Sunday
        this.weeklyReminderHour = 20;
        this.weeklyReminderMinute = 0;

        this.monthlyReminderEnabled = true;
        this.monthlyReminderDay = 1; // Default 1st of month
        this.monthlyReminderHour = 20;
        this.monthlyReminderMinute = 0;

        this.appliances = new HashMap<>();
    }

    public UserSettings(double greenEnergyPercent, boolean reminderEnabled, int reminderHour, int reminderMinute,
                        boolean weeklyReminderEnabled, int weeklyReminderDay, int weeklyReminderHour, int weeklyReminderMinute,
                        boolean monthlyReminderEnabled, int monthlyReminderDay, int monthlyReminderHour, int monthlyReminderMinute,
                        Map<String, ApplianceConfig> appliances) {
        this.greenEnergyPercent = greenEnergyPercent;
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

    public double getGreenEnergyPercent() { return greenEnergyPercent; }
    public void setGreenEnergyPercent(double greenEnergyPercent) { this.greenEnergyPercent = greenEnergyPercent; }

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