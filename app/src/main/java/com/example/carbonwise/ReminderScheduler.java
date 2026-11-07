package com.example.carbonwise;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

public class ReminderScheduler {

    public static void scheduleAllReminders(Context context, UserSettings settings) {
        if (settings == null) return;
        scheduleDailyReminder(context, settings);
        scheduleWeeklyReport(context, settings);
        scheduleMonthlyReport(context, settings);
        scheduleDailyBaseloadWork(context);
    }

    public static void scheduleDailyBaseloadWork(Context context) {
        Calendar currentDate = Calendar.getInstance();
        Calendar dueDate = Calendar.getInstance();
        dueDate.set(Calendar.HOUR_OF_DAY, 0);
        dueDate.set(Calendar.MINUTE, 5);
        dueDate.set(Calendar.SECOND, 0);

        if (dueDate.before(currentDate)) {
            dueDate.add(Calendar.HOUR_OF_DAY, 24);
        }
        long initialDelay = dueDate.getTimeInMillis() - currentDate.getTimeInMillis();

        PeriodicWorkRequest baseloadWorkRequest =
                new PeriodicWorkRequest.Builder(BaseloadWorker.class, 24, TimeUnit.HOURS)
                        .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                        .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "DailyBaseloadWorker",
                ExistingPeriodicWorkPolicy.KEEP,
                baseloadWorkRequest
        );
    }

    public static void scheduleDailyReminder(Context context, UserSettings settings) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, ReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, 100, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        if (!settings.isReminderEnabled()) {
            if (alarmManager != null) alarmManager.cancel(pendingIntent);
            return;
        }

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, settings.getReminderHour());
        calendar.set(Calendar.MINUTE, settings.getReminderMinute());
        calendar.set(Calendar.SECOND, 0);

        if (calendar.before(Calendar.getInstance())) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }

        if (alarmManager != null) {
            setExactAlarm(alarmManager, calendar.getTimeInMillis(), pendingIntent);
        }
    }

    public static void scheduleWeeklyReport(Context context, UserSettings settings) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, WeeklyReportReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, 200, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        if (!settings.isWeeklyReminderEnabled()) {
            if (alarmManager != null) alarmManager.cancel(pendingIntent);
            return;
        }

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_WEEK, settings.getWeeklyReminderDay());
        calendar.set(Calendar.HOUR_OF_DAY, settings.getWeeklyReminderHour());
        calendar.set(Calendar.MINUTE, settings.getWeeklyReminderMinute());
        calendar.set(Calendar.SECOND, 0);

        if (calendar.before(Calendar.getInstance())) {
            calendar.add(Calendar.WEEK_OF_YEAR, 1);
        }

        if (alarmManager != null) {
            setExactAlarm(alarmManager, calendar.getTimeInMillis(), pendingIntent);
        }
    }

    public static void scheduleMonthlyReport(Context context, UserSettings settings) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, MonthlyReportReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, 300, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        if (!settings.isMonthlyReminderEnabled()) {
            if (alarmManager != null) alarmManager.cancel(pendingIntent);
            return;
        }

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_MONTH, Math.min(28, settings.getMonthlyReminderDay()));
        calendar.set(Calendar.HOUR_OF_DAY, settings.getMonthlyReminderHour());
        calendar.set(Calendar.MINUTE, settings.getMonthlyReminderMinute());
        calendar.set(Calendar.SECOND, 0);

        if (calendar.before(Calendar.getInstance())) {
            calendar.add(Calendar.MONTH, 1);
        }

        if (alarmManager != null) {
            setExactAlarm(alarmManager, calendar.getTimeInMillis(), pendingIntent);
        }
    }

    private static void setExactAlarm(AlarmManager alarmManager, long triggerAtMillis, PendingIntent pendingIntent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }
}