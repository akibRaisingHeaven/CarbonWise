package com.example.carbonwise;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private Button btnLogin, btnGoogleSignIn;
    private TextView tvSignUp, tvForgotPassword;

    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;
    private ActivityResultLauncher<Intent> googleSignInLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn);
        tvSignUp = findViewById(R.id.tvSignUp);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);

        btnLogin.setOnClickListener(v -> loginUser());

        tvForgotPassword.setOnClickListener(v -> sendPasswordResetEmail());

        tvSignUp.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, SignUpActivity.class));
        });

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                        try {
                            GoogleSignInAccount account = task.getResult(ApiException.class);
                            if (account != null) {
                                firebaseAuthWithGoogle(account.getIdToken());
                            }
                        } catch (ApiException e) {
                            Toast.makeText(LoginActivity.this, "Google sign in failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );

        btnGoogleSignIn.setOnClickListener(v -> {
            Intent signInIntent = mGoogleSignInClient.getSignInIntent();
            googleSignInLauncher.launch(signInIntent);
        });
    }

    private void loginUser() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            etEmail.setError("Email is required");
            return;
        }

        if (TextUtils.isEmpty(password)) {
            etPassword.setError("Password is required");
            return;
        }

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(LoginActivity.this, "Login successful!", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(LoginActivity.this, DashboardActivity.class));
                        finishAffinity();
                    } else {
                        Toast.makeText(LoginActivity.this, "Authentication failed: " +
                                (task.getException() != null ? task.getException().getMessage() : ""), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void sendPasswordResetEmail() {
        String email = etEmail.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            etEmail.setError("Enter your email address first");
            etEmail.requestFocus();
            return;
        }

        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(LoginActivity.this,
                                "Password reset email sent! Check your inbox to set a password.",
                                Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(LoginActivity.this, "Failed: " +
                                        (task.getException() != null ? task.getException().getMessage() : ""),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(LoginActivity.this, "Signed in with Google!", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(LoginActivity.this, DashboardActivity.class));
                        finishAffinity();
                    } else {
                        Toast.makeText(LoginActivity.this, "Google authentication failed.", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}