package com.example.carbonwise;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Which appliances the user owns, their wattage, and which of them are always on (baseload). */
public class AppliancesSettingsActivity extends BaseSettingsActivity {

    private LinearLayout containerApplianceList;
    private Button btnSave;

    // Appliance name -> its row view (the row's tag holds the three controls)
    private final Map<String, View> applianceRowViews = new HashMap<>();
    private final List<String> applianceNames = CarbonCalculator.getSortedApplianceList();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings_appliances);

        containerApplianceList = findViewById(R.id.containerApplianceList);
        btnSave = findViewById(R.id.btnSave);

        populateApplianceRows();

        btnSave.setOnClickListener(v -> saveAppliances());

        initScreen("Owned Appliances", btnSave);
    }

    private void populateApplianceRows() {
        containerApplianceList.removeAllViews();

        for (String name : applianceNames) {
            LinearLayout itemLayout = new LinearLayout(this);
            itemLayout.setOrientation(LinearLayout.VERTICAL);
            itemLayout.setPadding(24, 20, 24, 20);
            itemLayout.setBackgroundColor(Color.WHITE);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, 16);
            itemLayout.setLayoutParams(lp);

            CheckBox cbOwned = new CheckBox(this);
            cbOwned.setText(name);
            cbOwned.setTextSize(16);
            cbOwned.setTextColor(Color.parseColor("#0F172A"));
            cbOwned.setButtonTintList(ColorStateList.valueOf(Color.parseColor("#1D4ED8")));
            cbOwned.setMinHeight(48);

            LinearLayout subLayout = new LinearLayout(this);
            subLayout.setOrientation(LinearLayout.HORIZONTAL);
            subLayout.setPadding(0, 8, 0, 0);

            EditText etWatt = new EditText(this);
            etWatt.setHint("Watts");
            etWatt.setText(String.valueOf(CarbonCalculator.DEFAULT_APPLIANCE_WATTS.getOrDefault(name, 100)));
            etWatt.setInputType(InputType.TYPE_CLASS_NUMBER);
            etWatt.setTextColor(Color.parseColor("#0F172A"));
            etWatt.setHintTextColor(Color.parseColor("#94A3B8"));
            etWatt.setTextSize(14);
            etWatt.setMinHeight(48);
            etWatt.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            CheckBox cbAlwaysOn = new CheckBox(this);
            cbAlwaysOn.setText("Always On (24h)");
            cbAlwaysOn.setTextSize(14);
            cbAlwaysOn.setTextColor(Color.parseColor("#334155"));
            cbAlwaysOn.setButtonTintList(ColorStateList.valueOf(Color.parseColor("#1D4ED8")));
            cbAlwaysOn.setMinHeight(48);

            // Same first-run defaults as before: a fridge and a router are always on
            if (name.contains("Refrigerator") || name.contains("Router")) {
                cbAlwaysOn.setChecked(true);
                cbOwned.setChecked(true);
            }

            boolean isOwned = cbOwned.isChecked();
            etWatt.setEnabled(isOwned);
            cbAlwaysOn.setEnabled(isOwned);

            cbOwned.setOnCheckedChangeListener((buttonView, isChecked) -> {
                etWatt.setEnabled(isChecked);
                cbAlwaysOn.setEnabled(isChecked);
                if (!isChecked) {
                    cbAlwaysOn.setChecked(false);
                }
            });

            subLayout.addView(etWatt);
            subLayout.addView(cbAlwaysOn);

            itemLayout.addView(cbOwned);
            itemLayout.addView(subLayout);

            containerApplianceList.addView(itemLayout);
            itemLayout.setTag(new ApplianceRowHolder(cbOwned, etWatt, cbAlwaysOn));
            applianceRowViews.put(name, itemLayout);
        }
    }

    @Override
    protected void bindSettings(UserSettings loaded) {
        if (loaded.getAppliances() == null) return;

        for (Map.Entry<String, ApplianceConfig> entry : loaded.getAppliances().entrySet()) {
            View row = applianceRowViews.get(entry.getKey());
            if (row == null) continue;

            ApplianceRowHolder holder = (ApplianceRowHolder) row.getTag();
            boolean isOwned = entry.getValue().isOwned();
            holder.cbOwned.setChecked(isOwned);
            holder.etWatt.setText(String.valueOf(entry.getValue().getWattage()));
            holder.etWatt.setEnabled(isOwned);
            holder.cbAlwaysOn.setChecked(isOwned && entry.getValue().isAlwaysOn());
            holder.cbAlwaysOn.setEnabled(isOwned);
        }
    }

    @Override
    protected String snapshot() {
        StringBuilder sb = new StringBuilder();
        for (String name : applianceNames) {
            ApplianceRowHolder holder = (ApplianceRowHolder) applianceRowViews.get(name).getTag();
            sb.append(name).append('=')
                    .append(holder.cbOwned.isChecked()).append(',')
                    .append(holder.etWatt.getText().toString().trim()).append(',')
                    .append(holder.cbAlwaysOn.isChecked()).append(';');
        }
        return sb.toString();
    }

    private void saveAppliances() {
        if (settings == null) return;

        Map<String, ApplianceConfig> applianceMap = new HashMap<>();
        for (String name : applianceNames) {
            ApplianceRowHolder holder = (ApplianceRowHolder) applianceRowViews.get(name).getTag();
            boolean isOwned = holder.cbOwned.isChecked();

            String wattText = holder.etWatt.getText().toString().trim();
            int watt;
            if (TextUtils.isEmpty(wattText)) {
                watt = CarbonCalculator.DEFAULT_APPLIANCE_WATTS.getOrDefault(name, 100);
            } else {
                try {
                    watt = Integer.parseInt(wattText);
                } catch (NumberFormatException e) {
                    watt = -1;
                }
            }

            if (watt <= 0) {
                holder.etWatt.setError("Enter watts above 0");
                holder.etWatt.requestFocus();
                return;
            }

            boolean alwaysOn = isOwned && holder.cbAlwaysOn.isChecked();
            applianceMap.put(name, new ApplianceConfig(name, watt, alwaysOn, isOwned));
        }

        settings.setAppliances(applianceMap);

        // Always-on devices define the baseload, so today's baseload is recalculated
        saveAndFinish(true, "Appliances saved!");
    }

    private static class ApplianceRowHolder {
        final CheckBox cbOwned;
        final EditText etWatt;
        final CheckBox cbAlwaysOn;

        ApplianceRowHolder(CheckBox cbOwned, EditText etWatt, CheckBox cbAlwaysOn) {
            this.cbOwned = cbOwned;
            this.etWatt = etWatt;
            this.cbAlwaysOn = cbAlwaysOn;
        }
    }
}
