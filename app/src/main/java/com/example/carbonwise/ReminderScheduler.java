package com.example.carbonwise;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.util.Calendar;

public class ReminderScheduler {

    public static void scheduleAllReminders(Context context, UserSettings settings) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        // 1. Daily Check-in Reminder
        Intent dailyIntent = new Intent(context, ReminderReceiver.class);
        PendingIntent dailyPending = PendingIntent.getBroadcast(
                context, 102, dailyIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        if (!settings.isReminderEnabled()) {
            alarmManager.cancel(dailyPending);
        } else {
            Calendar dailyCal = Calendar.getInstance();
            dailyCal.set(Calendar.HOUR_OF_DAY, settings.getReminderHour());
            dailyCal.set(Calendar.MINUTE, settings.getReminderMinute());
            dailyCal.set(Calendar.SECOND, 0);

            if (dailyCal.getTimeInMillis() <= System.currentTimeMillis()) {
                dailyCal.add(Calendar.DAY_OF_YEAR, 1);
            }

            alarmManager.setInexactRepeating(
                    AlarmManager.RTC_WAKEUP,
                    dailyCal.getTimeInMillis(),
                    AlarmManager.INTERVAL_DAY,
                    dailyPending
            );
        }

        // 2. Custom Weekly Report
        Intent weeklyIntent = new Intent(context, WeeklyReportReceiver.class);
        PendingIntent weeklyPending = PendingIntent.getBroadcast(
                context, 103, weeklyIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        if (!settings.isWeeklyReminderEnabled()) {
            alarmManager.cancel(weeklyPending);
        } else {
            Calendar weeklyCal = Calendar.getInstance();
            weeklyCal.set(Calendar.DAY_OF_WEEK, settings.getWeeklyReminderDay());
            weeklyCal.set(Calendar.HOUR_OF_DAY, settings.getWeeklyReminderHour());
            weeklyCal.set(Calendar.MINUTE, settings.getWeeklyReminderMinute());
            weeklyCal.set(Calendar.SECOND, 0);

            if (weeklyCal.getTimeInMillis() <= System.currentTimeMillis()) {
                weeklyCal.add(Calendar.WEEK_OF_YEAR, 1);
            }

            alarmManager.setInexactRepeating(
                    AlarmManager.RTC_WAKEUP,
                    weeklyCal.getTimeInMillis(),
                    AlarmManager.INTERVAL_DAY * 7,
                    weeklyPending
            );
        }

        // 3. Custom Monthly Report
        Intent monthlyIntent = new Intent(context, MonthlyReportReceiver.class);
        PendingIntent monthlyPending = PendingIntent.getBroadcast(
                context, 104, monthlyIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        if (!settings.isMonthlyReminderEnabled()) {
            alarmManager.cancel(monthlyPending);
        } else {
            Calendar monthlyCal = Calendar.getInstance();
            monthlyCal.set(Calendar.DAY_OF_MONTH, Math.min(28, Math.max(1, settings.getMonthlyReminderDay())));
            monthlyCal.set(Calendar.HOUR_OF_DAY, settings.getMonthlyReminderHour());
            monthlyCal.set(Calendar.MINUTE, settings.getMonthlyReminderMinute());
            monthlyCal.set(Calendar.SECOND, 0);

            if (monthlyCal.getTimeInMillis() <= System.currentTimeMillis()) {
                monthlyCal.add(Calendar.MONTH, 1);
            }

            alarmManager.setInexactRepeating(
                    AlarmManager.RTC_WAKEUP,
                    monthlyCal.getTimeInMillis(),
                    AlarmManager.INTERVAL_DAY * 30,
                    monthlyPending
            );
        }
    }
}