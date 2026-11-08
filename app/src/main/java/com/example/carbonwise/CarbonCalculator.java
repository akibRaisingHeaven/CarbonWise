package com.example.carbonwise;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CarbonCalculator {

    public static final double DAILY_CARBON_BUDGET_KG = 15.0;

    // Hard ceiling for any daily budget. A higher goal makes the budget meaningless.
    public static final double MAX_DAILY_BUDGET_KG = 20.0;

    /** Caps a budget at MAX_DAILY_BUDGET_KG. Used on every read and write of a budget. */
    public static double clampBudget(double budgetKg) {
        return Math.min(budgetKg, MAX_DAILY_BUDGET_KG);
    }
    public static final double DEFAULT_GRID_CO2_PER_KWH = 0.50; // Standard grid emission factor

    // Extended Transport Emission Factors (kg CO2 / km)
    public static final double CO2_PER_KM_PETROL_CAR = 0.192;
    public static final double CO2_PER_KM_DIESEL_CAR = 0.171;
    public static final double CO2_PER_KM_CNG_CAR = 0.140;
    public static final double CO2_PER_KM_EV_CAR = 0.053;

    public static final double CO2_PER_KM_PETROL_BIKE = 0.103;
    public static final double CO2_PER_KM_E_BIKE = 0.025;

    public static final double CO2_PER_KM_CNG_AUTORICKSHAW = 0.065;
    public static final double CO2_PER_KM_EASYBIKE = 0.020;
    public static final double CO2_PER_KM_RICKSHAW = 0.0;
    public static final double CO2_PER_KM_LEGUNA = 0.050;

    public static final double CO2_PER_KM_DIESEL_BUS = 0.089;
    public static final double CO2_PER_KM_CNG_BUS = 0.075;
    public static final double CO2_PER_KM_ELECTRIC_BUS = 0.030;
    public static final double CO2_PER_KM_TRAIN = 0.035;

    // Meals Emission Base Factors (kg CO2 / meal)
    public static final double CO2_PER_MEAL_BEEF = 3.50;
    public static final double CO2_PER_MEAL_POULTRY = 1.20;
    public static final double CO2_PER_MEAL_VEGETARIAN = 0.60;
    public static final double CO2_PER_MEAL_VEGAN = 0.40;

    // Meal Overheads & Add-ons (kg CO2)
    public static final double CO2_TAKEOUT_PACKAGING = 0.35;
    public static final double CO2_DAIRY_BEVERAGE = 0.40;
    public static final double CO2_SOFT_DRINK = 0.18;

    // Cooking Fuel Emission Factors (kg CO2 / hour of single-burner use)
    public static final double CO2_PER_HOUR_LPG = 0.54;
    public static final double CO2_PER_HOUR_NATURAL_GAS = 0.34;
    public static final double CO2_PER_HOUR_FIREWOOD = 1.46;
    public static final double CO2_PER_HOUR_BIOMASS = 1.00;
    public static final double CO2_PER_HOUR_KEROSENE = 0.51;

    // Default appliance wattage dictionary
    public static final Map<String, Integer> DEFAULT_APPLIANCE_WATTS = new HashMap<String, Integer>() {{
        put("Air Conditioner (AC)", 1500);
        put("Air Fryer", 1500);
        put("Blender / Food Processor", 400);
        put("Ceiling Fan", 75);
        put("Desktop Computer", 300);
        put("Electric Kettle", 1500);
        put("Electric Oven / Stove", 2000);
        put("Electric Vehicle (EV Charger)", 3300);
        put("Laptop", 65);
        put("LED Light Bulb", 15);
        put("Microwave Oven", 1200);
        put("Refrigerator", 150);
        put("Room Heater", 2000);
        put("Television", 120);
        put("Washing Machine", 500);
        put("Water Heater / Geyser", 2000);
        put("Water Pump", 750);
        put("WiFi Router", 10);
    }};

    public static List<String> getSortedApplianceList() {
        List<String> list = new ArrayList<>(DEFAULT_APPLIANCE_WATTS.keySet());
        Collections.sort(list);
        return list;
    }

    public static double calculateElectricityCo2(double watts, double hours, double greenElectricityPercent) {
        double cleanRatio = Math.min(100.0, Math.max(0.0, greenElectricityPercent)) / 100.0;
        double effectiveEmissionFactor = DEFAULT_GRID_CO2_PER_KWH * (1.0 - cleanRatio);
        double kWh = (watts * hours) / 1000.0;
        return kWh * effectiveEmissionFactor;
    }

    public static double calculateTransportCo2(String transportMode, double distanceKm) {
        if (transportMode == null) return 0.0;

        double factor;
        switch (transportMode) {
            case "Petrol Car":
            case "Car":
            case "Gas Car":
                factor = CO2_PER_KM_PETROL_CAR;
                break;
            case "Diesel Car":
                factor = CO2_PER_KM_DIESEL_CAR;
                break;
            case "CNG Car":
                factor = CO2_PER_KM_CNG_CAR;
                break;
            case "Electric Car (EV)":
            case "Electric Vehicle (EV)":
            case "EV":
                factor = CO2_PER_KM_EV_CAR;
                break;
            case "Petrol Motorbike":
            case "Motorbike":
                factor = CO2_PER_KM_PETROL_BIKE;
                break;
            case "Electric Scooter / Bike":
                factor = CO2_PER_KM_E_BIKE;
                break;
            case "CNG Auto-Rickshaw (3-Wheeler)":
                factor = CO2_PER_KM_CNG_AUTORICKSHAW;
                break;
            case "Electric Easybike / Auto-Rickshaw":
                factor = CO2_PER_KM_EASYBIKE;
                break;
            case "Leguna / Human Hauler":
                factor = CO2_PER_KM_LEGUNA;
                break;
            case "Diesel Bus":
            case "Bus":
                factor = CO2_PER_KM_DIESEL_BUS;
                break;
            case "CNG Bus":
                factor = CO2_PER_KM_CNG_BUS;
                break;
            case "Electric Bus":
                factor = CO2_PER_KM_ELECTRIC_BUS;
                break;
            case "Train / Metro Rail":
            case "Train":
                factor = CO2_PER_KM_TRAIN;
                break;
            case "Manual Cycle Rickshaw":
            case "Bicycle / Walking":
            default:
                factor = CO2_PER_KM_RICKSHAW;
                break;
        }
        return distanceKm * factor;
    }

    /**
     * Backward-compatible meal calculation.
     */
    public static double calculateMealCo2(String mealType, int mealCount) {
        return calculateMealCo2(mealType, "Standard (1.0x)", "Finished / Clean Plate", false, false, false, mealCount);
    }

    /**
     * Dynamic meal calculation with portion sizing, waste multipliers, delivery packaging, and beverage add-ons.
     */
    public static double calculateMealCo2(String mealType, String portionSize, String wasteOption,
                                          boolean isTakeout, boolean hasDairyBeverage, boolean hasSoftDrink, int mealCount) {
        if (mealType == null || mealCount <= 0) return 0.0;

        double baseFactor;
        switch (mealType) {
            case "High Meat (Beef / Lamb)":
                baseFactor = CO2_PER_MEAL_BEEF;
                break;
            case "Low Meat (Poultry / Fish)":
                baseFactor = CO2_PER_MEAL_POULTRY;
                break;
            case "Vegetarian":
                baseFactor = CO2_PER_MEAL_VEGETARIAN;
                break;
            case "Vegan":
            default:
                baseFactor = CO2_PER_MEAL_VEGAN;
                break;
        }

        // Portion Sizing Multipliers
        double portionMultiplier = 1.0;
        if (portionSize != null) {
            if (portionSize.contains("Snack") || portionSize.contains("0.6x")) {
                portionMultiplier = 0.6;
            } else if (portionSize.contains("Large") || portionSize.contains("1.4x")) {
                portionMultiplier = 1.4;
            } else if (portionSize.contains("Feast") || portionSize.contains("1.8x")) {
                portionMultiplier = 1.8;
            }
        }

        // Food Waste & Leftover Modifiers
        double wasteMultiplier = 1.0;
        if (wasteOption != null) {
            if (wasteOption.contains("Saved Leftovers") || wasteOption.contains("-15%")) {
                wasteMultiplier = 0.85; // 15% bonus for zero waste & saving leftovers
            } else if (wasteOption.contains("Wasted") || wasteOption.contains("+30%")) {
                wasteMultiplier = 1.30; // 30% penalty for landfill food waste methane emissions
            }
        }

        double singleMealCo2 = baseFactor * portionMultiplier * wasteMultiplier;

        // Add packaging and beverage overheads
        if (isTakeout) singleMealCo2 += CO2_TAKEOUT_PACKAGING;
        if (hasDairyBeverage) singleMealCo2 += CO2_DAIRY_BEVERAGE;
        if (hasSoftDrink) singleMealCo2 += CO2_SOFT_DRINK;

        return singleMealCo2 * mealCount;
    }

    public static double calculateCookingCo2(String fuelType, double hours) {
        if (fuelType == null || hours <= 0) return 0.0;

        double factor;
        switch (fuelType) {
            case "LPG (Cylinder Gas)":
                factor = CO2_PER_HOUR_LPG;
                break;
            case "Piped Natural Gas":
                factor = CO2_PER_HOUR_NATURAL_GAS;
                break;
            case "Firewood (Wood Stove)":
                factor = CO2_PER_HOUR_FIREWOOD;
                break;
            case "Dry Twigs & Crop Biomass":
                factor = CO2_PER_HOUR_BIOMASS;
                break;
            case "Kerosene Stove":
                factor = CO2_PER_HOUR_KEROSENE;
                break;
            default:
                factor = CO2_PER_HOUR_LPG;
                break;
        }
        return hours * factor;
    }
}