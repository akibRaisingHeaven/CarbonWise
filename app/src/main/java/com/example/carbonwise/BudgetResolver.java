package com.example.carbonwise;

import java.util.Map;

/**
 * Decides which daily budget applies to a given day, so every screen judges a day against the
 * budget that was in force on that day. Changing the budget today never affects past days.
 */
public final class BudgetResolver {

    /** Budget for days that have no saved budget (they were logged before budgets were recorded). */
    public static final double LEGACY_BUDGET_KG = 15.0;

    private BudgetResolver() {}

    /**
     * @param savedBudgets  budgets saved per day, keyed by date text such as "Nov 8, 2026"
     * @param dateKey       the day to look up, in the same format
     * @param todayKey      today's date text; today falls back to the current budget
     * @param currentBudget the user's current daily budget
     */
    public static double budgetForDate(Map<String, Double> savedBudgets, String dateKey,
                                       String todayKey, double currentBudget) {
        // The latest settings document is authoritative for today, including a budget change
        // made earlier today. Saved day records are used only for dates in the past.
        if (dateKey != null && dateKey.equals(todayKey)) {
            return CarbonCalculator.clampBudget(currentBudget);
        }
        Double saved = savedBudgets != null ? savedBudgets.get(dateKey) : null;
        if (saved != null) return CarbonCalculator.clampBudget(saved);
        return LEGACY_BUDGET_KG;
    }
}
