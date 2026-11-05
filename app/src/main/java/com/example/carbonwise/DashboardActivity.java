package com.example.carbonwise;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.firebase.auth.FirebaseAuth;

public class DashboardActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        Button btnLogout = findViewById(R.id.btnLogout);
        btnLogout.setOnClickListener(v -> performLogout());
    }

    private void performLogout() {
        // 1. Firebase Auth Sign-Out
        FirebaseAuth.getInstance().signOut();

        // 2. Google Client Sign-Out (resets Google account selection prompt)
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build();
        GoogleSignIn.getClient(this, gso).signOut().addOnCompleteListener(task -> {
            Toast.makeText(DashboardActivity.this, "Logged out successfully", Toast.LENGTH_SHORT).show();

            // 3. Clear backstack and redirect to LoginActivity
            Intent intent = new Intent(DashboardActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }
}