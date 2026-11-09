package com.example.carbonwise;

import android.graphics.Color;

/** Shared daily-budget thresholds so the dashboard and widget show the same status. */
public final class CarbonBudgetStatus {
    private static final int COLOR_ON_TRACK = Color.parseColor("#BBF7D0");
    private static final int COLOR_NEAR_LIMIT = Color.parseColor("#FDE68A");
    private static final int COLOR_OVER_BUDGET = Color.parseColor("#FECACA");
    private static final int COLOR_UNAVAILABLE = Color.WHITE;

    private final String label;
    private final int color;

    private CarbonBudgetStatus(String label, int color) {
        this.label = label;
        this.color = color;
    }

    public static CarbonBudgetStatus from(double emissionsKg, double dailyBudgetKg) {
        if (dailyBudgetKg <= 0) {
            return new CarbonBudgetStatus("Budget unavailable", COLOR_UNAVAILABLE);
        }

        double safeEmissions = Math.max(0, emissionsKg);
        long displayedPercent = Math.round(safeEmissions / dailyBudgetKg * 100.0);
        if (safeEmissions > dailyBudgetKg) {
            return new CarbonBudgetStatus("⚠ Over your budget limit", COLOR_OVER_BUDGET);
        }
        if (displayedPercent >= 80L) {
            return new CarbonBudgetStatus("● Near your budget limit", COLOR_NEAR_LIMIT);
        }
        return new CarbonBudgetStatus("✓ You're all good", COLOR_ON_TRACK);
    }

    public String getLabel() {
        return label;
    }

    public int getWidgetColor() {
        return color;
    }

    public int getDashboardColor() {
        return color;
    }
}
