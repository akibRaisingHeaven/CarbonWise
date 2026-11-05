package com.example.carbonwise;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            FirebaseAuth mAuth = FirebaseAuth.getInstance();
            FirebaseUser currentUser = mAuth.getCurrentUser();

            Intent intent;
            if (currentUser != null) {
                // User is already logged in -> Navigate directly to Dashboard
                intent = new Intent(SplashActivity.this, DashboardActivity.class);
            } else {
                // No session found -> Navigate to Login screen
                intent = new Intent(SplashActivity.this, LoginActivity.class);
            }
            startActivity(intent);
            finish();
        }, 1500);
    }
}