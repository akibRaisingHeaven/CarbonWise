package com.example.carbonwise;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.google.firebase.auth.FirebaseAuth;

/**
 * Restores everything that does not survive a reboot or an app update:
 * reminder alarms are rebuilt from the saved schedule, the live tracker is restarted only if
 * the user has it turned on, and the home-screen widget is redrawn.
 */
public class CarbonGoalReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            return;
        }

        // Alarms are wiped by a reboot or update: schedule them again from the saved settings
        ReminderScheduler.rescheduleFromCache(context);

        boolean signedIn = FirebaseAuth.getInstance().getCurrentUser() != null;
        if (signedIn && ReminderScheduler.isLiveTrackerEnabledCached(context)) {
            Intent serviceIntent = new Intent(context, CarbonGoalService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        }

        CarbonWidgetProvider.refreshAll(context);
    }
}
