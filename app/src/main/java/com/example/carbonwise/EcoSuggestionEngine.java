package com.example.carbonwise;

import java.util.Locale;

public class EcoSuggestionEngine {

    /**
     * Generates a dynamic, quantitative eco-suggestion using all activity parameters.
     *
     * @param category          "Energy", "Transport", or "Meals"
     * @param title             Specific item (e.g., "Air Conditioner (AC)", "Car Travel", "High Meat Meal")
     * @param hoursOrKmOrCount Quantity metric (Hours active, KM traveled, or meal count)
     * @param co2               Calculated CO2 emitted in kg for this specific log
     * @return Dynamic tip with quantified savings potential
     */
    public static String getSuggestion(String category, String title, double hoursOrKmOrCount, double co2) {
        if (co2 <= 0) {
            return "🌱 Zero-Emission Activity! Excellent job keeping your carbon footprint clean.";
        }

        switch (category.toUpperCase()) {
            case "ENERGY":
                return getEnergySuggestion(title, hoursOrKmOrCount, co2);

            case "TRANSPORT":
                return getTransportSuggestion(title, hoursOrKmOrCount, co2);

            case "MEALS":
                return getMealsSuggestion(title, hoursOrKmOrCount, co2);

            default:
                double defaultPotentialSavings = co2 * 0.20;
                return String.format(Locale.getDefault(),
                        "Tip: Trimming this usage by 20%% will save ~%.2f kg CO₂.", defaultPotentialSavings);
        }
    }

    private static String getEnergySuggestion(String title, double hours, double co2) {
        if (title.contains("AC") || title.contains("Air Conditioner")) {
            double savings = co2 * 0.15; // ~15% energy reduction by setting temp 1-2°C higher
            if (hours >= 8) {
                return String.format(Locale.getDefault(),
                        "💡 High AC Usage: Running for %.1fh logged %.2f kg CO₂. Setting a 6h off-timer or raising temp 2°C saves ~%.2f kg CO₂ daily!",
                        hours, co2, savings);
            }
            return String.format(Locale.getDefault(),
                    "💡 AC Tip: Raising your thermostat by 1°C during this %.1fh run saves ~%.2f kg CO₂.",
                    hours, savings);
        }

        if (title.contains("Heater")) {
            double savings = co2 * 0.12;
            return String.format(Locale.getDefault(),
                    "💡 Heating Tip: Lowering space heating 2°C during this %.1fh window cuts emissions by ~%.2f kg CO₂.",
                    hours, savings);
        }

        if (title.contains("Router") || title.contains("Refrigerator") || title.contains("Always On")) {
            double nightSavings = (co2 / Math.max(1.0, hours)) * 8.0; // 8 hours of nighttime standby savings
            return String.format(Locale.getDefault(),
                    "🔌 Standby Power: Drew %.2f kg CO₂ over %.1fh. Smart-plug night schedules save ~%.2f kg CO₂ daily.",
                    co2, hours, nightSavings);
        }

        if (title.contains("Washing Machine")) {
            double coldWaterSavings = co2 * 0.75; // Cold water saves ~75% of cycle energy
            return String.format(Locale.getDefault(),
                    "🧺 Laundry Tip: Switching to a cold-water wash for this load saves ~%.2f kg CO₂!",
                    coldWaterSavings);
        }

        double standardSavings = co2 * 0.25;
        return String.format(Locale.getDefault(),
                "💡 Energy Tip: Powering off idle electronics during this %.1fh stretch saves ~%.2f kg CO₂.",
                hours, standardSavings);
    }

    private static String getTransportSuggestion(String title, double km, double co2) {
        if (title.contains("Car")) {
            if (km <= 5.0) {
                return String.format(Locale.getDefault(),
                        "🚲 Short Drive Alert: Driving %.1f km emitted %.2f kg CO₂. Cycling or walking saves the full %.2f kg CO₂!",
                        km, co2, co2);
            }
            double transitSavings = km * (CarbonCalculator.CO2_PER_KM_CAR - CarbonCalculator.CO2_PER_KM_BUS);
            double percentSaved = (transitSavings / co2) * 100;
            return String.format(Locale.getDefault(),
                    "🚌 Commute Tip: Your %.1f km drive logged %.2f kg CO₂. Switching to public transit saves ~%.2f kg CO₂ (%.0f%% reduction)!",
                    km, co2, transitSavings, percentSaved);
        }

        if (title.contains("Bus") || title.contains("Train")) {
            double savedVsCar = km * (CarbonCalculator.CO2_PER_KM_CAR - CarbonCalculator.CO2_PER_KM_BUS);
            return String.format(Locale.getDefault(),
                    "🌱 Great Choice! Taking public transit for %.1f km saved ~%.2f kg CO₂ compared to driving solo.",
                    km, savedVsCar);
        }

        if (title.contains("Motorbike")) {
            double bikeSavings = co2 * 0.40;
            return String.format(Locale.getDefault(),
                    "🏍 Transit Tip: Taking a bus for this %.1f km trip instead saves ~%.2f kg CO₂.",
                    km, bikeSavings);
        }

        return String.format(Locale.getDefault(),
                "🌱 Clean Travel: Driving this %.1f km route would have added %.2f kg CO₂. Great decision!",
                km, km * CarbonCalculator.CO2_PER_KM_CAR);
    }

    private static String getMealsSuggestion(String title, double count, double co2) {
        int mealCount = (int) Math.max(1, count);
        if (title.contains("High Meat") || title.contains("Beef")) {
            double swapSavings = mealCount * (CarbonCalculator.CO2_PER_MEAL_BEEF - CarbonCalculator.CO2_PER_MEAL_POULTRY);
            return String.format(Locale.getDefault(),
                    "🥩 Diet Tip: %d beef meal(s) logged %.2f kg CO₂. Swapping to poultry/fish saves ~%.2f kg CO₂!",
                    mealCount, co2, swapSavings);
        }

        if (title.contains("Low Meat") || title.contains("Poultry")) {
            double vegSavings = mealCount * (CarbonCalculator.CO2_PER_MEAL_POULTRY - CarbonCalculator.CO2_PER_MEAL_VEGETARIAN);
            return String.format(Locale.getDefault(),
                    "🍗 Diet Tip: Swapping this meal for a vegetarian option saves an extra ~%.2f kg CO₂.",
                    vegSavings);
        }

        double savedVsRedMeat = mealCount * (CarbonCalculator.CO2_PER_MEAL_BEEF - CarbonCalculator.CO2_PER_MEAL_VEGAN);
        return String.format(Locale.getDefault(),
                "🥗 Eco Star! Plant-based choices saved ~%.2f kg CO₂ compared to standard red meat meals.",
                savedVsRedMeat);
    }
}