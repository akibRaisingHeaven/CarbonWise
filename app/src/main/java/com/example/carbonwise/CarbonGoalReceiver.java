package com.example.carbonwise;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class CarbonGoalReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            // Start CarbonGoalService automatically when the device boots
            Intent serviceIntent = new Intent(context, CarbonGoalService.class);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        } else if ("com.example.carbonwise.GOAL_EXCEEDED".equals(intent.getAction())) {
            Toast.makeText(context, "Alert: Daily CO2 limit exceeded!", Toast.LENGTH_LONG).show();
        }
    }
}