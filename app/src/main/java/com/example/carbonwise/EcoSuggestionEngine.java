package com.example.carbonwise;

import java.util.Locale;

public class EcoSuggestionEngine {

    public static String getSuggestion(String category, String title, double hoursOrKmOrCount, double co2) {
        if (co2 <= 0) {
            return "🌱 Zero-Emission Activity! Excellent job keeping your carbon footprint clean.";
        }

        switch (category.toUpperCase()) {
            case "ELECTRICITY":
            case "ENERGY":
                return getElectricitySuggestion(title, hoursOrKmOrCount, co2);

            case "TRANSPORT":
                return getTransportSuggestion(title, hoursOrKmOrCount, co2);

            case "MEALS":
                return getMealsSuggestion(title, hoursOrKmOrCount, co2);

            case "COOKING":
                return getCookingSuggestion(title, hoursOrKmOrCount, co2);

            default:
                double defaultPotentialSavings = co2 * 0.20;
                return String.format(Locale.getDefault(),
                        "Tip: Trimming this usage by 20%% will save ~%.2f kg CO₂.", defaultPotentialSavings);
        }
    }

    private static String getElectricitySuggestion(String title, double hours, double co2) {
        if (title.contains("AC") || title.contains("Air Conditioner")) {
            double savings = co2 * 0.15;
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
            double nightSavings = (co2 / Math.max(1.0, hours)) * 8.0;
            return String.format(Locale.getDefault(),
                    "🔌 Standby Power: Drew %.2f kg CO₂ over %.1fh. Smart-plug night schedules save ~%.2f kg CO₂ daily.",
                    co2, hours, nightSavings);
        }

        if (title.contains("Washing Machine")) {
            double coldWaterSavings = co2 * 0.75;
            return String.format(Locale.getDefault(),
                    "🧺 Laundry Tip: Switching to a cold-water wash for this load saves ~%.2f kg CO₂!",
                    coldWaterSavings);
        }

        double standardSavings = co2 * 0.25;
        return String.format(Locale.getDefault(),
                "💡 Electricity Tip: Powering off idle electronics during this %.1fh stretch saves ~%.2f kg CO₂.",
                hours, standardSavings);
    }

    private static String getTransportSuggestion(String title, double km, double co2) {
        if (title.contains("Rickshaw") || title.contains("Bicycle") || title.contains("Walking")) {
            return String.format(Locale.getDefault(),
                    "🌱 Zero Emissions! Traveling %.1f km via non-motorized transport saved ~%.2f kg CO₂ compared to driving.",
                    km, km * CarbonCalculator.CO2_PER_KM_PETROL_CAR);
        }

        if (title.contains("EV") || title.contains("Electric") || title.contains("Easybike")) {
            double savingsVsPetrol = km * (CarbonCalculator.CO2_PER_KM_PETROL_CAR - CarbonCalculator.calculateTransportCo2(title, 1.0));
            return String.format(Locale.getDefault(),
                    "⚡ Clean Mobility: Driving %.1f km with electric transport emitted only %.2f kg CO₂ (saved ~%.2f kg CO₂ vs petrol car)!",
                    km, co2, Math.max(0, savingsVsPetrol));
        }

        if (title.contains("Car")) {
            if (km <= 5.0) {
                return String.format(Locale.getDefault(),
                        "🚲 Short Drive Alert: Driving %.1f km emitted %.2f kg CO₂. Cycling, walking, or taking a rickshaw saves up to %.2f kg CO₂!",
                        km, co2, co2);
            }
            double modeFactor = CarbonCalculator.calculateTransportCo2(title, 1.0);
            double transitSavings = km * Math.max(0.0, modeFactor - CarbonCalculator.CO2_PER_KM_DIESEL_BUS);
            double percentSaved = (co2 > 0) ? (transitSavings / co2) * 100 : 0;
            return String.format(Locale.getDefault(),
                    "🚌 Commute Tip: Your %.1f km drive logged %.2f kg CO₂. Switching to public transit saves ~%.2f kg CO₂ (%.0f%% reduction)!",
                    km, co2, transitSavings, percentSaved);
        }

        if (title.contains("Auto-Rickshaw") || title.contains("Leguna")) {
            if (km <= 3.0) {
                return String.format(Locale.getDefault(),
                        "🛺 Short Trip Tip: Taking a 3-wheeler for %.1f km emitted %.2f kg CO₂. Walking or manual rickshaw saves %.2f kg CO₂!",
                        km, co2, co2);
            }
            double ebikeSavings = km * Math.max(0.0, CarbonCalculator.CO2_PER_KM_CNG_AUTORICKSHAW - CarbonCalculator.CO2_PER_KM_EASYBIKE);
            return String.format(Locale.getDefault(),
                    "⚡ Local Transit Tip: Taking an electric Easybike instead of CNG auto for %.1f km cuts ~%.2f kg CO₂.",
                    km, ebikeSavings);
        }

        if (title.contains("Bus") || title.contains("Train") || title.contains("Metro")) {
            double modeFactor = CarbonCalculator.calculateTransportCo2(title, 1.0);
            double savedVsCar = km * Math.max(0.0, CarbonCalculator.CO2_PER_KM_PETROL_CAR - modeFactor);
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

        double savedVsPetrol = km * (CarbonCalculator.CO2_PER_KM_PETROL_CAR - CarbonCalculator.calculateTransportCo2(title, 1.0));
        return String.format(Locale.getDefault(),
                "🌱 Travel Choice: Traveling %.1f km logged %.2f kg CO₂. (Saved ~%.2f kg CO₂ compared to a petrol car).",
                km, co2, Math.max(0.0, savedVsPetrol));
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

    private static String getCookingSuggestion(String title, double hours, double co2) {
        if (title.contains("Firewood") || title.contains("Twigs") || title.contains("Biomass")) {
            double icsSavings = co2 * 0.40;
            return String.format(Locale.getDefault(),
                    "🪵 Biomass Cooking: Burning wood/twigs for %.1fh logged %.2f kg CO₂. Using an Improved Cookstove (Bondhu Chula) or pressure cooker saves ~%.2f kg CO₂!",
                    hours, co2, icsSavings);
        }

        if (title.contains("LPG") || title.contains("Natural Gas")) {
            double pressureCookerSavings = co2 * 0.30;
            return String.format(Locale.getDefault(),
                    "🔥 Gas Cooking Tip: Using a pressure cooker or covering pots with lids during this %.1fh cook time saves ~%.2f kg CO₂.",
                    hours, pressureCookerSavings);
        }

        double standardSavings = co2 * 0.20;
        return String.format(Locale.getDefault(),
                "🍳 Cooking Tip: Soaking pulses before cooking cuts stove run-time, saving ~%.2f kg CO₂.",
                standardSavings);
    }
}