package com.example.carbonwise;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ContextThemeWrapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ActivityDialogHelper {

    /**
     * Returns exact "lat, lon" if the field still shows a place picked on the map,
     * otherwise whatever the user typed.
     */
    private static String routeQuery(EditText field) {
        if (field == null) return "";
        String text = field.getText().toString().trim();
        Object tag = field.getTag();
        if (tag instanceof String[]) {
            String[] picked = (String[]) tag;
            if (picked.length == 2 && text.equals(picked[0])) return picked[1];
        }
        return text;
    }

    public interface OnLogSaveListener {
        void onSave(String title, String category, double co2);
    }

    public interface OnMapPickerRequested {
        void onRequestPicker(EditText targetField);
    }

    public interface OnOutageReported {
        void onReport(double outageHours);
    }

    public static void showAddOrEditDialog(
            Context context,
            ActivityLog existingLog,
            Map<String, ApplianceConfig> userAppliances,
            double userGreenPercent,
            OnMapPickerRequested mapPickerCallback,
            OnLogSaveListener saveCallback
    ) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.activity_dialog_add_log, null);

        TextView tvHeaderTitle = dialogView.findViewById(R.id.tvDialogHeaderTitle);
        Spinner spCategory = dialogView.findViewById(R.id.spCategory);
        Spinner spAppliance = dialogView.findViewById(R.id.spAppliance);
        Spinner spTransportMode = dialogView.findViewById(R.id.spTransportMode);
        Spinner spMealType = dialogView.findViewById(R.id.spMealType);
        Spinner spPortionSize = dialogView.findViewById(R.id.spPortionSize);
        Spinner spWasteOption = dialogView.findViewById(R.id.spWasteOption);
        Spinner spCookingFuel = dialogView.findViewById(R.id.spCookingFuel);

        CheckBox cbTakeoutPackaging = dialogView.findViewById(R.id.cbTakeoutPackaging);
        CheckBox cbDairyBeverage = dialogView.findViewById(R.id.cbDairyBeverage);
        CheckBox cbSoftDrink = dialogView.findViewById(R.id.cbSoftDrink);

        EditText etHoursUsed = dialogView.findViewById(R.id.etHoursUsed);
        EditText etOrigin = dialogView.findViewById(R.id.etOrigin);
        EditText etDestination = dialogView.findViewById(R.id.etDestination);
        EditText etDistance = dialogView.findViewById(R.id.etDistance);
        EditText etMealCount = dialogView.findViewById(R.id.etMealCount);
        EditText etCookingHours = dialogView.findViewById(R.id.etCookingHours);

        Button btnPickOriginMap = dialogView.findViewById(R.id.btnPickOriginMap);
        Button btnPickDestMap = dialogView.findViewById(R.id.btnPickDestMap);
        Button btnCalculateDistance = dialogView.findViewById(R.id.btnCalculateDistance);
        Button btnSubmitLog = dialogView.findViewById(R.id.btnSubmitLog);
        Button btnCancelDialog = dialogView.findViewById(R.id.btnCancelDialog);

        ProgressBar pbLoading = dialogView.findViewById(R.id.pbLoading);
        TextView tvCalculatedCo2 = dialogView.findViewById(R.id.tvCalculatedCo2);

        LinearLayout layoutElectricity = dialogView.findViewById(R.id.layoutElectricity);
        LinearLayout layoutTransport = dialogView.findViewById(R.id.layoutTransport);
        LinearLayout layoutMeals = dialogView.findViewById(R.id.layoutMeals);
        LinearLayout layoutCooking = dialogView.findViewById(R.id.layoutCooking);

        // Styling inputs for high contrast
        int darkText = Color.parseColor("#0F172A");
        int hintText = Color.parseColor("#94A3B8");
        ColorStateList blueTint = ColorStateList.valueOf(Color.parseColor("#1D4ED8"));

        if (etHoursUsed != null) { etHoursUsed.setTextColor(darkText); etHoursUsed.setHintTextColor(hintText); }
        if (etOrigin != null) { etOrigin.setTextColor(darkText); etOrigin.setHintTextColor(hintText); }
        if (etDestination != null) { etDestination.setTextColor(darkText); etDestination.setHintTextColor(hintText); }
        if (etDistance != null) { etDistance.setTextColor(darkText); etDistance.setHintTextColor(hintText); }
        if (etMealCount != null) { etMealCount.setTextColor(darkText); etMealCount.setHintTextColor(hintText); etMealCount.setText("1"); }
        if (etCookingHours != null) { etCookingHours.setTextColor(darkText); etCookingHours.setHintTextColor(hintText); }

        if (cbTakeoutPackaging != null) cbTakeoutPackaging.setButtonTintList(blueTint);
        if (cbDairyBeverage != null) cbDairyBeverage.setButtonTintList(blueTint);
        if (cbSoftDrink != null) cbSoftDrink.setButtonTintList(blueTint);

        if (tvHeaderTitle != null) {
            tvHeaderTitle.setText(existingLog == null ? "Log New Activity" : "Edit Activity Log");
        }
        if (btnSubmitLog != null) {
            btnSubmitLog.setText(existingLog == null ? "Calculate & Log" : "Update Log");
        }

        // Category Spinner
        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(context, R.layout.spinner_item, new String[]{"Electricity", "Transport", "Meals", "Cooking"});
        catAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spCategory.setAdapter(catAdapter);

        // Appliance Spinner
        ArrayAdapter<String> appAdapter = new ArrayAdapter<>(context, R.layout.spinner_item, getAvailableManualApplianceNames(userAppliances));
        appAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spAppliance.setAdapter(appAdapter);

        // Transport Modes Spinner
        String[] transportOptions = new String[]{
                "Petrol Car", "Diesel Car", "CNG Car", "Electric Car (EV)",
                "Petrol Motorbike", "Electric Scooter / Bike", "CNG Auto-Rickshaw (3-Wheeler)",
                "Electric Easybike / Auto-Rickshaw", "Manual Cycle Rickshaw", "Leguna / Human Hauler",
                "Diesel Bus", "CNG Bus", "Electric Bus", "Train / Metro Rail", "Bicycle / Walking"
        };
        ArrayAdapter<String> transportAdapter = new ArrayAdapter<>(context, R.layout.spinner_item, transportOptions);
        transportAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spTransportMode.setAdapter(transportAdapter);

        // Meal Type Spinner
        ArrayAdapter<String> mealAdapter = new ArrayAdapter<>(context, R.layout.spinner_item, new String[]{"High Meat (Beef / Lamb)", "Low Meat (Poultry / Fish)", "Vegetarian", "Vegan"});
        mealAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spMealType.setAdapter(mealAdapter);

        // Portion Size Spinner
        String[] portionOptions = new String[]{"Standard (1.0x)", "Snack / Light (0.6x)", "Large / Heavy (1.4x)", "Feast / Buffet (1.8x)"};
        ArrayAdapter<String> portionAdapter = new ArrayAdapter<>(context, R.layout.spinner_item, portionOptions);
        portionAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        if (spPortionSize != null) spPortionSize.setAdapter(portionAdapter);

        // Waste Option Spinner
        String[] wasteOptions = new String[]{"Finished / Clean Plate", "Saved Leftovers (-15%)", "Wasted >25% (+30%)"};
        ArrayAdapter<String> wasteAdapter = new ArrayAdapter<>(context, R.layout.spinner_item, wasteOptions);
        wasteAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        if (spWasteOption != null) spWasteOption.setAdapter(wasteAdapter);

        // Cooking Fuel Spinner
        String[] cookingFuelOptions = new String[]{
                "LPG (Cylinder Gas)", "Piped Natural Gas", "Firewood (Wood Stove)",
                "Dry Twigs & Crop Biomass", "Kerosene Stove"
        };
        ArrayAdapter<String> cookingAdapter = new ArrayAdapter<>(context, R.layout.spinner_item, cookingFuelOptions);
        cookingAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        if (spCookingFuel != null) spCookingFuel.setAdapter(cookingAdapter);

        final int[] lastDurationMins = {0};

        Runnable updateLiveEmissions = () -> {
            if (etDistance == null || spTransportMode == null || tvCalculatedCo2 == null) return;
            String distStr = etDistance.getText().toString().trim();
            if (distStr.isEmpty()) return;

            try {
                double dist = Double.parseDouble(distStr);
                if (dist > 0) {
                    String mode = spTransportMode.getSelectedItem().toString();
                    double co2Emitted = CarbonCalculator.calculateTransportCo2(mode, dist);
                    if (lastDurationMins[0] > 0) {
                        tvCalculatedCo2.setText(String.format(Locale.getDefault(), "Distance: %.1f km (%d mins) • %.2f kg CO₂", dist, lastDurationMins[0], co2Emitted));
                    } else {
                        tvCalculatedCo2.setText(String.format(Locale.getDefault(), "Distance: %.1f km • %.2f kg CO₂", dist, co2Emitted));
                    }
                }
            } catch (NumberFormatException ignored) {}
        };

        spTransportMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { updateLiveEmissions.run(); }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        if (etDistance != null) {
            etDistance.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updateLiveEmissions.run(); }
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        if (existingLog != null) {
            int selIndex = 0;
            if ("Transport".equalsIgnoreCase(existingLog.getCategory())) selIndex = 1;
            else if ("Meals".equalsIgnoreCase(existingLog.getCategory())) selIndex = 2;
            else if ("Cooking".equalsIgnoreCase(existingLog.getCategory())) selIndex = 3;
            spCategory.setSelection(selIndex);
        }

        spCategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                layoutElectricity.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
                layoutTransport.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
                layoutMeals.setVisibility(position == 2 ? View.VISIBLE : View.GONE);
                if (layoutCooking != null) {
                    layoutCooking.setVisibility(position == 3 ? View.VISIBLE : View.GONE);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        if (btnPickOriginMap != null && etOrigin != null) {
            btnPickOriginMap.setOnClickListener(v -> mapPickerCallback.onRequestPicker(etOrigin));
        }

        if (btnPickDestMap != null && etDestination != null) {
            btnPickDestMap.setOnClickListener(v -> mapPickerCallback.onRequestPicker(etDestination));
        }

        if (btnCalculateDistance != null) {
            btnCalculateDistance.setOnClickListener(v -> {
                String origin = routeQuery(etOrigin);
                String destination = routeQuery(etDestination);

                if (TextUtils.isEmpty(origin) || TextUtils.isEmpty(destination)) {
                    Toast.makeText(context, "Please enter origin and destination", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (pbLoading != null) pbLoading.setVisibility(View.VISIBLE);
                btnCalculateDistance.setEnabled(false);

                MapApiClient.fetchDistance(origin, destination, new MapApiClient.DistanceCallback() {
                    @Override
                    public void onSuccess(double distanceKm, int durationMinutes) {
                        if (pbLoading != null) pbLoading.setVisibility(View.GONE);
                        btnCalculateDistance.setEnabled(true);
                        lastDurationMins[0] = durationMinutes;
                        if (etDistance != null) etDistance.setText(String.format(Locale.getDefault(), "%.1f", distanceKm));
                        updateLiveEmissions.run();
                    }

                    @Override
                    public void onError(String errorMessage) {
                        if (pbLoading != null) pbLoading.setVisibility(View.GONE);
                        btnCalculateDistance.setEnabled(true);
                        Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show();
                    }
                });
            });
        }

        AlertDialog dialog = new AlertDialog.Builder(
                new ContextThemeWrapper(context, androidx.appcompat.R.style.Theme_AppCompat_Light_Dialog_Alert)
        )
                .setView(dialogView)
                .create();

        if (btnCancelDialog != null) {
            btnCancelDialog.setOnClickListener(v -> dialog.dismiss());
        }

        if (btnSubmitLog != null) {
            btnSubmitLog.setOnClickListener(v -> {
                String category = spCategory.getSelectedItem().toString();
                double calculatedCo2 = 0.0;
                String title = "";

                if ("Electricity".equalsIgnoreCase(category)) {
                    String applianceName = spAppliance.getSelectedItem().toString();
                    int watts = userAppliances.containsKey(applianceName) ? userAppliances.get(applianceName).getWattage() : CarbonCalculator.DEFAULT_APPLIANCE_WATTS.getOrDefault(applianceName, 100);

                    String hoursStr = etHoursUsed.getText().toString().trim();
                    if (TextUtils.isEmpty(hoursStr)) {
                        Toast.makeText(context, "Please enter hours used", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    double activeHours = Double.parseDouble(hoursStr);
                    if (activeHours <= 0 || activeHours > 24.0) {
                        Toast.makeText(context, "Hours used must be between 0.1 and 24.0 hours", Toast.LENGTH_LONG).show();
                        return;
                    }

                    calculatedCo2 = CarbonCalculator.calculateElectricityCo2(watts, activeHours, userGreenPercent);
                    title = applianceName;

                } else if ("Transport".equalsIgnoreCase(category)) {
                    String mode = spTransportMode.getSelectedItem().toString();
                    String distStr = etDistance.getText().toString().trim();

                    if (TextUtils.isEmpty(distStr)) {
                        Toast.makeText(context, "Please enter distance traveled", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    double dist = Double.parseDouble(distStr);
                    if (dist <= 0) {
                        Toast.makeText(context, "Distance must be greater than 0 km", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    calculatedCo2 = CarbonCalculator.calculateTransportCo2(mode, dist);
                    title = mode + " Travel";

                } else if ("Cooking".equalsIgnoreCase(category)) {
                    String fuelType = (spCookingFuel != null) ? spCookingFuel.getSelectedItem().toString() : "LPG (Cylinder Gas)";
                    String hoursStr = (etCookingHours != null) ? etCookingHours.getText().toString().trim() : "";

                    if (TextUtils.isEmpty(hoursStr)) {
                        Toast.makeText(context, "Please enter cooking duration in hours", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    double cookingHours = Double.parseDouble(hoursStr);
                    if (cookingHours <= 0 || cookingHours > 24.0) {
                        Toast.makeText(context, "Cooking hours must be between 0.1 and 24.0 hours", Toast.LENGTH_LONG).show();
                        return;
                    }

                    calculatedCo2 = CarbonCalculator.calculateCookingCo2(fuelType, cookingHours);
                    title = fuelType + " Cooking";

                } else { // Meals
                    String mealType = spMealType.getSelectedItem().toString();
                    String portionSize = spPortionSize != null ? spPortionSize.getSelectedItem().toString() : "Standard (1.0x)";
                    String wasteOption = spWasteOption != null ? spWasteOption.getSelectedItem().toString() : "Finished / Clean Plate";

                    boolean isTakeout = cbTakeoutPackaging != null && cbTakeoutPackaging.isChecked();
                    boolean hasDairyBeverage = cbDairyBeverage != null && cbDairyBeverage.isChecked();
                    boolean hasSoftDrink = cbSoftDrink != null && cbSoftDrink.isChecked();

                    String countStr = etMealCount.getText().toString().trim();
                    if (TextUtils.isEmpty(countStr)) {
                        Toast.makeText(context, "Please enter meal count", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    int count = Integer.parseInt(countStr);
                    if (count <= 0) {
                        Toast.makeText(context, "Meal count must be at least 1", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    calculatedCo2 = CarbonCalculator.calculateMealCo2(
                            mealType, portionSize, wasteOption, isTakeout, hasDairyBeverage, hasSoftDrink, count
                    );

                    StringBuilder titleBuilder = new StringBuilder();
                    if (portionSize.contains("Snack")) titleBuilder.append("Light ");
                    else if (portionSize.contains("Large")) titleBuilder.append("Large ");
                    else if (portionSize.contains("Feast")) titleBuilder.append("Feast ");
                    titleBuilder.append(mealType).append(" Meal");

                    if (isTakeout) titleBuilder.append(" (Delivery)");
                    title = titleBuilder.toString();
                }

                saveCallback.onSave(title, category, calculatedCo2);
                dialog.dismiss();
            });
        }

        dialog.show();
    }

    public static void showReportOutageDialog(Context context, String selectedDateStr, OnOutageReported callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(
                new ContextThemeWrapper(context, androidx.appcompat.R.style.Theme_AppCompat_Light_Dialog_Alert)
        );
        builder.setTitle("Report Power Outage");
        builder.setMessage("Enter blackout hours experienced on " + selectedDateStr + " to adjust Baseload:");

        final EditText etOutage = new EditText(context);
        etOutage.setHint("Outage hours (e.g., 2.5)");
        etOutage.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etOutage.setTextColor(Color.parseColor("#0F172A"));
        etOutage.setHintTextColor(Color.parseColor("#94A3B8"));
        etOutage.setTextSize(16);
        etOutage.setPadding(32, 24, 32, 24);

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(40, 10, 40, 10);
        container.addView(etOutage);

        builder.setView(container);

        builder.setPositiveButton("Update Baseload", (dialog, which) -> {
            String input = etOutage.getText().toString().trim();
            if (TextUtils.isEmpty(input)) return;

            double outageHours = Double.parseDouble(input);
            if (outageHours < 0.0 || outageHours > 24.0) {
                Toast.makeText(context, "Outage hours must be between 0.0 and 24.0", Toast.LENGTH_SHORT).show();
                return;
            }

            callback.onReport(outageHours);
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.show();

        Button positiveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Button negativeBtn = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);

        if (positiveBtn != null) {
            positiveBtn.setTextColor(Color.parseColor("#1D4ED8"));
            positiveBtn.setTextSize(14);
        }
        if (negativeBtn != null) {
            negativeBtn.setTextColor(Color.parseColor("#64748B"));
            negativeBtn.setTextSize(14);
        }
    }

    private static List<String> getAvailableManualApplianceNames(Map<String, ApplianceConfig> userAppliances) {
        List<String> list = new ArrayList<>();
        if (userAppliances != null && !userAppliances.isEmpty()) {
            for (ApplianceConfig config : userAppliances.values()) {
                if (config.isOwned() && !config.isAlwaysOn()) {
                    list.add(config.getName());
                }
            }
        }
        if (list.isEmpty()) {
            for (String name : CarbonCalculator.getSortedApplianceList()) {
                ApplianceConfig cfg = userAppliances != null ? userAppliances.get(name) : null;
                if (cfg == null || !cfg.isAlwaysOn()) {
                    list.add(name);
                }
            }
        }
        Collections.sort(list);
        return list;
    }
}