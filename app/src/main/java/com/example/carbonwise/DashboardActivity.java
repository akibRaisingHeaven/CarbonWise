package com.example.carbonwise;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DashboardActivity extends AppCompatActivity {

    private RecyclerView rvActivityLogs;
    private Button btnLogActivity, btnLogout;
    private TextView tvTotalCo2;
    private ActivityLogAdapter adapter;
    private List<ActivityLog> logList;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private DatabaseReference rtdbRef;
    private String userId;

    private double currentTotalCo2 = 0.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            startActivity(new Intent(DashboardActivity.this, LoginActivity.class));
            finish();
            return;
        }

        userId = currentUser.getUid();
        db = FirebaseFirestore.getInstance();
        rtdbRef = FirebaseDatabase.getInstance().getReference("users").child(userId).child("summary");

        rvActivityLogs = findViewById(R.id.rvActivityLogs);
        btnLogActivity = findViewById(R.id.btnLogActivity);
        btnLogout = findViewById(R.id.btnLogout);
        tvTotalCo2 = findViewById(R.id.tvTotalCo2);

        // 1. Setup RecyclerView
        rvActivityLogs.setLayoutManager(new LinearLayoutManager(this));
        logList = new ArrayList<>();
        adapter = new ActivityLogAdapter(logList);
        rvActivityLogs.setAdapter(adapter);

        // 2. Click Listeners
        btnLogActivity.setOnClickListener(v -> showAddActivityDialog());

        btnLogout.setOnClickListener(v -> {
            mAuth.signOut();
            startActivity(new Intent(DashboardActivity.this, LoginActivity.class));
            finish();
        });

        // 3. Start Background Foreground Service (Week 10 Component)
        Intent serviceIntent = new Intent(this, CarbonGoalService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        // 4. Attach Database Listeners
        loadFirestoreLogs();
        listenToRealtimeSummary();
    }

    // Read full detailed logs from Cloud Firestore
    private void loadFirestoreLogs() {
        db.collection("users").document(userId).collection("activity_logs")
                .orderBy("date", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;

                    logList.clear();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        ActivityLog log = doc.toObject(ActivityLog.class);
                        if (log != null) {
                            logList.add(log);
                        }
                    }
                    adapter.notifyDataSetChanged();
                });
    }

    // Listen to live total CO2 emission aggregate from Firebase Realtime Database
    private void listenToRealtimeSummary() {
        rtdbRef.child("total_co2_kg").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Double total = snapshot.getValue(Double.class);
                    if (total != null) {
                        currentTotalCo2 = total;
                        tvTotalCo2.setText(String.format(Locale.getDefault(), "%.1f kg CO₂", currentTotalCo2));
                    }
                } else {
                    tvTotalCo2.setText("0.0 kg CO₂");
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                // Read error handling
            }
        });
    }

    // Show input dialog when tapping + Log New Activity
    private void showAddActivityDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.activity_dialog_add_log, null);

        EditText etTitle = dialogView.findViewById(R.id.etActivityTitle);
        EditText etCategory = dialogView.findViewById(R.id.etActivityCategory);
        EditText etCo2 = dialogView.findViewById(R.id.etCo2Emitted);

        builder.setView(dialogView)
                .setPositiveButton("Add Log", (dialog, which) -> {
                    String title = etTitle.getText().toString().trim();
                    String category = etCategory.getText().toString().trim();
                    String co2Str = etCo2.getText().toString().trim();

                    if (TextUtils.isEmpty(title) || TextUtils.isEmpty(category) || TextUtils.isEmpty(co2Str)) {
                        Toast.makeText(this, "Please fill out all fields", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    double co2Amount = Double.parseDouble(co2Str);
                    saveActivityLog(title, category, co2Amount);
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .create()
                .show();
    }

    // Save individual log to Firestore and update overall total in Realtime Database
    private void saveActivityLog(String title, String category, double co2Amount) {
        String logId = db.collection("users").document(userId).collection("activity_logs").document().getId();
        String currentDate = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(new Date());

        ActivityLog newLog = new ActivityLog(logId, category, title, co2Amount, currentDate);

        // 1. Write document into Cloud Firestore
        db.collection("users").document(userId)
                .collection("activity_logs")
                .document(logId)
                .set(newLog)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(DashboardActivity.this, "Activity logged successfully!", Toast.LENGTH_SHORT).show();

                    // 2. Update aggregate total in Firebase Realtime Database
                    double updatedTotal = currentTotalCo2 + co2Amount;
                    rtdbRef.child("total_co2_kg").setValue(updatedTotal);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(DashboardActivity.this, "Failed to log activity: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }
}