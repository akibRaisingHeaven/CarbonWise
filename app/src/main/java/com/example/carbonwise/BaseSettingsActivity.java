package com.example.carbonwise;

import android.graphics.Color;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ContextThemeWrapper;

/**
 * Shared behaviour of the four settings detail screens: header with back arrow, loading the
 * current UserSettings, a disabled Save button until loading is done, and an
 * "unsaved changes" prompt when leaving.
 *
 * A subclass builds its views and then calls initScreen(). It must implement:
 *  - bindSettings(): put the loaded values into the views
 *  - snapshot(): a string describing the current state of the views (used to detect edits)
 */
public abstract class BaseSettingsActivity extends AppCompatActivity {

    /** The settings loaded from Firestore. Null until loading has finished. */
    protected UserSettings settings;

    private String initialSnapshot = null;
    private Button saveButton;

    protected abstract void bindSettings(UserSettings loaded);

    protected abstract String snapshot();

    protected void initScreen(String title, Button saveButton) {
        this.saveButton = saveButton;

        TextView tvTitle = findViewById(R.id.tvScreenTitle);
        if (tvTitle != null) tvTitle.setText(title);

        TextView btnBack = findViewById(R.id.btnSettingsBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> handleReturnRequest());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleReturnRequest();
            }
        });

        // Save stays disabled until the current values are loaded, so nothing can be overwritten early
        saveButton.setEnabled(false);

        SettingsStore.load(new SettingsStore.LoadCallback() {
            @Override
            public void onLoaded(UserSettings loaded) {
                if (isFinishing() || isDestroyed()) return;
                settings = loaded;
                bindSettings(loaded);
                initialSnapshot = snapshot();
                BaseSettingsActivity.this.saveButton.setEnabled(true);
            }

            @Override
            public void onError(String reason) {
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(BaseSettingsActivity.this, "Could not load settings: " + reason, Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    protected boolean hasUnsavedChanges() {
        return initialSnapshot != null && !initialSnapshot.equals(snapshot());
    }

    protected void handleReturnRequest() {
        if (!hasUnsavedChanges()) {
            finish();
            return;
        }

        AlertDialog dialog = new AlertDialog.Builder(
                new ContextThemeWrapper(this, androidx.appcompat.R.style.Theme_AppCompat_Light_Dialog_Alert))
                .setTitle("Unsaved Changes")
                .setMessage("You have changes that are not saved. Discard them and go back?")
                .setPositiveButton("Discard & Go Back", (d, which) -> finish())
                .setNegativeButton("Keep Editing", (d, which) -> d.dismiss())
                .create();
        dialog.show();

        Button positiveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Button negativeBtn = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (positiveBtn != null) positiveBtn.setTextColor(Color.parseColor("#DC2626"));
        if (negativeBtn != null) negativeBtn.setTextColor(Color.parseColor("#2563EB"));
    }

    /** Standard save: runs SettingsStore.save, then closes the screen with a confirmation toast. */
    protected void saveAndFinish(boolean recomputeBaseload, String successMessage) {
        saveButton.setEnabled(false);
        SettingsStore.save(this, settings, recomputeBaseload, new SettingsStore.SaveCallback() {
            @Override
            public void onSaved() {
                Toast.makeText(BaseSettingsActivity.this, successMessage, Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onError(String reason) {
                saveButton.setEnabled(true);
                Toast.makeText(BaseSettingsActivity.this, "Save failed: " + reason, Toast.LENGTH_LONG).show();
            }
        });
    }
}
