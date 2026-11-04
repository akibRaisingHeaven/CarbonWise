package com.example.carbonwise;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.example.carbonwise.db.AppDatabase;
import com.example.carbonwise.db.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;

import java.util.concurrent.Executors;

public class SignUpActivity extends AppCompatActivity {

    private EditText etName, etEmail, etPassword;
    private Button btnRegister;
    private TextView tvAlreadyHaveAccount;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        // Safe Firebase Auth initialization
        try {
            mAuth = FirebaseAuth.getInstance();
        } catch (Exception e) {
            Toast.makeText(this, "Firebase not configured yet.", Toast.LENGTH_SHORT).show();
        }

        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnRegister = findViewById(R.id.btnRegister);
        tvAlreadyHaveAccount = findViewById(R.id.tvAlreadyHaveAccount);

        btnRegister.setOnClickListener(v -> registerUser());

        tvAlreadyHaveAccount.setOnClickListener(v -> finish());
    }

    private void registerUser() {
        String name = etName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.length() < 6) {
            Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
            return;
        }

        if (mAuth != null) {
            // Real Firebase Registration
            mAuth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, task -> {
                        if (task.isSuccessful()) {
                            FirebaseUser firebaseUser = mAuth.getCurrentUser();
                            if (firebaseUser != null) {
                                UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                        .setDisplayName(name)
                                        .build();
                                firebaseUser.updateProfile(profileUpdates);
                                saveUserToRoomDatabase(firebaseUser.getUid(), name);
                            }
                            Toast.makeText(SignUpActivity.this, "Account created successfully!", Toast.LENGTH_SHORT).show();
                            navigateToDashboard();
                        } else {
                            String errorMsg = task.getException() != null ? task.getException().getMessage() : "Registration failed";
                            Toast.makeText(SignUpActivity.this, "Error: " + errorMsg, Toast.LENGTH_LONG).show();
                        }
                    });
        } else {
            // Offline / Local Room Registration Fallback
            saveUserToRoomDatabase("local_user_" + System.currentTimeMillis(), name);
            Toast.makeText(this, "Account created locally!", Toast.LENGTH_SHORT).show();
            navigateToDashboard();
        }
    }

    private void saveUserToRoomDatabase(String uid, String name) {
        Executors.newSingleThreadExecutor().execute(() -> {
            User newUser = new User(
                    uid,
                    name,
                    "chicken",
                    "petrol",
                    1,
                    "default",
                    10.0,
                    1
            );
            AppDatabase.getInstance(getApplicationContext()).userDao().upsert(newUser);
        });
    }

    private void navigateToDashboard() {
        Intent intent = new Intent(SignUpActivity.this, DashboardActivity.class);
        startActivity(intent);
        finishAffinity();
    }
}