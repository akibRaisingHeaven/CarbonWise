package com.example.carbonwise;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.Locale;

/** Daily carbon budget goal. Tiers are fixed; a custom goal may not exceed the 20 kg cap. */
public class BudgetSettingsActivity extends BaseSettingsActivity {

    private static final double MAX_KG = CarbonCalculator.MAX_DAILY_BUDGET_KG;

    private Spinner spBudgetTier;
    private EditText etCustomBudget;
    private TextView tvBudgetHint;
    private Button btnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings_budget);

        spBudgetTier = findViewById(R.id.spBudgetTier);
        etCustomBudget = findViewById(R.id.etCustomBudget);
        tvBudgetHint = findViewById(R.id.tvBudgetHint);
        btnSave = findViewById(R.id.btnSave);

        tvBudgetHint.setText(String.format(Locale.getDefault(),
                "The daily budget can be at most %.0f kg CO₂, so the goal stays meaningful.", MAX_KG));

        String[] budgetTiers = new String[]{
                "🌱 Eco / Strict (10.0 kg CO₂/day)",
                "⚖️ Standard (15.0 kg CO₂/day)",
                "🛋️ Lenient (20.0 kg CO₂/day)",
                String.format(Locale.getDefault(), "✏️ Custom Budget Goal (max %.0f kg)", MAX_KG)
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.spinner_item, budgetTiers);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spBudgetTier.setAdapter(adapter);

        spBudgetTier.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                etCustomBudget.setVisibility(position == 3 ? View.VISIBLE : View.GONE);
                if (position != 3) etCustomBudget.setError(null);
            }

            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Tell the user right away when the typed goal is over the limit
        etCustomBudget.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String error = validateCustom(s.toString().trim());
                etCustomBudget.setError(TextUtils.isEmpty(s) ? null : error);
            }
        });

        btnSave.setOnClickListener(v -> saveBudget());

        initScreen("Carbon Budget Goal", btnSave);
    }

    @Override
    protected void bindSettings(UserSettings loaded) {
        String tier = loaded.getBudgetTier();
        if ("ECO".equalsIgnoreCase(tier)) spBudgetTier.setSelection(0);
        else if ("LENIENT".equalsIgnoreCase(tier)) spBudgetTier.setSelection(2);
        else if ("CUSTOM".equalsIgnoreCase(tier)) spBudgetTier.setSelection(3);
        else spBudgetTier.setSelection(1);

        // US locale: a numberDecimal field only accepts '.' as the separator
        etCustomBudget.setText(String.format(Locale.US, "%.1f", loaded.getCustomBudgetKg()));
        etCustomBudget.setVisibility("CUSTOM".equalsIgnoreCase(tier) ? View.VISIBLE : View.GONE);
    }

    @Override
    protected String snapshot() {
        return spBudgetTier.getSelectedItemPosition() + "|" + etCustomBudget.getText().toString().trim();
    }

    /** Returns an error message for a custom budget text, or null when it is valid. */
    private String validateCustom(String text) {
        if (text.isEmpty()) return "Enter a daily budget";
        double value;
        try {
            value = Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return "Enter a valid number";
        }
        if (value <= 0) return "Budget must be greater than 0 kg CO₂";
        if (value > MAX_KG) return String.format(Locale.getDefault(), "Maximum allowed is %.0f kg CO₂ per day", MAX_KG);
        return null;
    }

    private void saveBudget() {
        if (settings == null) return;

        int tierPos = spBudgetTier.getSelectedItemPosition();
        String tier = (tierPos == 0) ? "ECO" : (tierPos == 2 ? "LENIENT" : (tierPos == 3 ? "CUSTOM" : "STANDARD"));

        double custom = settings.getCustomBudgetKg();
        double active;
        if (tierPos == 3) {
            String text = etCustomBudget.getText().toString().trim();
            String error = validateCustom(text);
            if (error != null) {
                etCustomBudget.setError(error);
                etCustomBudget.requestFocus();
                return;
            }
            custom = Double.parseDouble(text);
            active = custom;
        } else {
            active = (tierPos == 0) ? 10.0 : (tierPos == 2 ? 20.0 : 15.0);
        }

        settings.setBudgetTier(tier);
        settings.setCustomBudgetKg(custom);
        settings.setDailyBudgetKg(active);

        saveAndFinish(false, "Budget goal saved!");
    }
}
