package com.example.carbonwise;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CarbonCalculator {

    public static final double DAILY_CARBON_BUDGET_KG = 15.0;
    public static final double DEFAULT_GRID_CO2_PER_KWH = 0.50; // Standard grid emission factor

    public static final double CO2_PER_KM_CAR = 0.192;
    public static final double CO2_PER_KM_BUS = 0.089;
    public static final double CO2_PER_KM_BIKE = 0.103;

    public static final double CO2_PER_MEAL_BEEF = 3.50;
    public static final double CO2_PER_MEAL_POULTRY = 1.20;
    public static final double CO2_PER_MEAL_VEGETARIAN = 0.60;
    public static final double CO2_PER_MEAL_VEGAN = 0.40;

    // Expanded default appliance wattage dictionary
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

    /**
     * Calculates CO2 incorporating green energy ratio and active hours.
     */
    public static double calculateElectricityCo2(double watts, double hours, double greenEnergyPercent) {
        double cleanRatio = Math.min(100.0, Math.max(0.0, greenEnergyPercent)) / 100.0;
        double effectiveEmissionFactor = DEFAULT_GRID_CO2_PER_KWH * (1.0 - cleanRatio);
        double kWh = (watts * hours) / 1000.0;
        return kWh * effectiveEmissionFactor;
    }

    public static double calculateTransportCo2(String transportMode, double distanceKm) {
        switch (transportMode) {
            case "Car":
                return distanceKm * CO2_PER_KM_CAR;
            case "Bus":
                return distanceKm * CO2_PER_KM_BUS;
            case "Motorbike":
                return distanceKm * CO2_PER_KM_BIKE;
            case "Bicycle / Walking":
            default:
                return 0.0;
        }
    }

    public static double calculateMealCo2(String mealType, int mealCount) {
        double factor;
        switch (mealType) {
            case "High Meat (Beef / Lamb)":
                factor = CO2_PER_MEAL_BEEF;
                break;
            case "Low Meat (Poultry / Fish)":
                factor = CO2_PER_MEAL_POULTRY;
                break;
            case "Vegetarian":
                factor = CO2_PER_MEAL_VEGETARIAN;
                break;
            case "Vegan":
            default:
                factor = CO2_PER_MEAL_VEGAN;
                break;
        }
        return factor * mealCount;
    }
}