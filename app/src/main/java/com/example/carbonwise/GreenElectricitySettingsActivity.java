package com.example.carbonwise;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import java.util.Locale;

/** Share of clean electricity on the user's grid (typed in or detected from the live grid). */
public class GreenElectricitySettingsActivity extends BaseSettingsActivity {

    private EditText etGreenPercent;
    private Button btnSave;

    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (GpsLocationHelper.hasPermission(this)) {
                    detectGridFromGps();
                } else {
                    Toast.makeText(this, "Location permission is needed to detect your local grid.", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings_green);

        etGreenPercent = findViewById(R.id.etGreenPercent);
        btnSave = findViewById(R.id.btnSave);
        Button btnAutoDetectGrid = findViewById(R.id.btnAutoDetectGrid);

        btnAutoDetectGrid.setOnClickListener(v -> {
            if (GpsLocationHelper.hasPermission(this)) {
                detectGridFromGps();
            } else {
                locationPermissionLauncher.launch(new String[]{
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION});
            }
        });

        btnSave.setOnClickListener(v -> saveGreenPercent());

        initScreen("Green Electricity", btnSave);
    }

    @Override
    protected void bindSettings(UserSettings loaded) {
        etGreenPercent.setText(String.format(Locale.US, "%.1f", loaded.getGreenElectricityPercent()));
    }

    @Override
    protected String snapshot() {
        return etGreenPercent.getText().toString().trim();
    }

    private void detectGridFromGps() {
        Toast.makeText(this, "Getting your location...", Toast.LENGTH_SHORT).show();

        GpsLocationHelper.getCurrentLocation(this, new GpsLocationHelper.LocationCallback() {
            @Override
            public void onLocation(double latitude, double longitude) {
                GridApiClient.fetchCleanPercentage(
                        GreenElectricitySettingsActivity.this,
                        latitude,
                        longitude,
                        new GridApiClient.GridCallback() {
                            @Override
                            public void onSuccess(double cleanPercent) {
                                runOnUiThread(() -> {
                                    if (isFinishing() || isDestroyed()) return;
                                    etGreenPercent.setText(String.format(Locale.US, "%.1f", cleanPercent));
                                    Toast.makeText(GreenElectricitySettingsActivity.this,
                                            String.format(Locale.getDefault(), "Live Grid Updated: %.1f%% Clean Electricity", cleanPercent),
                                            Toast.LENGTH_LONG).show();
                                });
                            }

                            @Override
                            public void onError(String reason) {
                                runOnUiThread(() -> applyGridFallback(reason));
                            }
                        }
                );
            }

            @Override
            public void onError(String reason) {
                applyGridFallback(reason);
            }
        });
    }

    private void applyGridFallback(String reason) {
        if (isFinishing() || isDestroyed()) return;
        final double regionalFallback = 1.0;
        if (etGreenPercent.getText().toString().trim().isEmpty()) {
            etGreenPercent.setText(String.format(Locale.US, "%.1f", regionalFallback));
            Toast.makeText(this, "Live data unavailable (" + reason + "). Using regional estimate 1.0%.", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Live data unavailable (" + reason + "). Keeping your current value.", Toast.LENGTH_LONG).show();
        }
    }

    private void saveGreenPercent() {
        if (settings == null) return;

        String text = etGreenPercent.getText().toString().trim();
        if (TextUtils.isEmpty(text)) {
            etGreenPercent.setError("Enter a percentage");
            etGreenPercent.requestFocus();
            return;
        }

        double percent;
        try {
            percent = Double.parseDouble(text);
        } catch (NumberFormatException e) {
            etGreenPercent.setError("Enter a valid number");
            etGreenPercent.requestFocus();
            return;
        }

        if (percent < 0 || percent > 100) {
            etGreenPercent.setError("Must be between 0% and 100%");
            etGreenPercent.requestFocus();
            return;
        }

        settings.setGreenElectricityPercent(percent);

        // Baseload and appliance emissions depend on this value, so today's baseload is recalculated
        saveAndFinish(true, "Green electricity saved!");
    }
}
