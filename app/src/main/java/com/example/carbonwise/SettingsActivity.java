package com.example.carbonwise;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Settings hub: four cards, each opening its own screen. Every card shows a one-line summary of
 * the current value, refreshed each time this screen comes back to the front.
 */
public class SettingsActivity extends AppCompatActivity {

    private TextView tvGreenSummary, tvBudgetSummary, tvNotificationsSummary, tvAppliancesSummary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            finish();
            return;
        }

        TextView tvTitle = findViewById(R.id.tvScreenTitle);
        if (tvTitle != null) tvTitle.setText("Settings");

        TextView btnBack = findViewById(R.id.btnSettingsBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });

        tvGreenSummary = findViewById(R.id.tvGreenSummary);
        tvBudgetSummary = findViewById(R.id.tvBudgetSummary);
        tvNotificationsSummary = findViewById(R.id.tvNotificationsSummary);
        tvAppliancesSummary = findViewById(R.id.tvAppliancesSummary);

        openOnClick(R.id.cardGreen, GreenElectricitySettingsActivity.class);
        openOnClick(R.id.cardBudget, BudgetSettingsActivity.class);
        openOnClick(R.id.cardNotifications, NotificationSettingsActivity.class);
        openOnClick(R.id.cardAppliances, AppliancesSettingsActivity.class);
    }

    private void openOnClick(int cardId, Class<?> target) {
        View card = findViewById(cardId);
        if (card != null) card.setOnClickListener(v -> startActivity(new Intent(this, target)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        SettingsStore.load(new SettingsStore.LoadCallback() {
            @Override
            public void onLoaded(UserSettings settings) {
                if (isFinishing() || isDestroyed()) return;
                showSummaries(settings);
            }

            @Override
            public void onError(String reason) {
                // Summaries are only a convenience; the detail screens report their own load errors
            }
        });
    }

    private void showSummaries(UserSettings s) {
        tvGreenSummary.setText(String.format(Locale.getDefault(),
                "%.1f%% clean electricity on your grid", s.getGreenElectricityPercent()));

        String tier = s.getBudgetTier();
        String tierName = "ECO".equalsIgnoreCase(tier) ? "Eco / Strict"
                : "LENIENT".equalsIgnoreCase(tier) ? "Lenient"
                : "CUSTOM".equalsIgnoreCase(tier) ? "Custom"
                : "Standard";
        tvBudgetSummary.setText(String.format(Locale.getDefault(),
                "%s • %.1f kg CO₂ per day", tierName, s.getDailyBudgetKg()));

        List<String> on = new ArrayList<>();
        if (s.isLiveTrackerEnabled()) on.add("Live tracker");
        if (s.isReminderEnabled()) on.add("Daily reminder");
        if (s.isWeeklyReminderEnabled()) on.add("Weekly report");
        if (s.isMonthlyReminderEnabled()) on.add("Monthly report");
        tvNotificationsSummary.setText(on.isEmpty() ? "All notifications are off" : "On: " + TextUtils.join(", ", on));

        int owned = 0;
        int alwaysOn = 0;
        Map<String, ApplianceConfig> appliances = s.getAppliances();
        if (appliances != null) {
            for (ApplianceConfig config : appliances.values()) {
                if (config.isOwned()) {
                    owned++;
                    if (config.isAlwaysOn()) alwaysOn++;
                }
            }
        }
        tvAppliancesSummary.setText(owned == 0
                ? "No appliances selected yet"
                : String.format(Locale.getDefault(), "%d owned • %d always on", owned, alwaysOn));
    }
}
