package com.example.carbonwise;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class DashboardActivity extends AppCompatActivity {

    private RecyclerView rvRecentLogs;
    private Button btnLogActivity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        rvRecentLogs = findViewById(R.id.rvRecentLogs);
        btnLogActivity = findViewById(R.id.btnLogActivity);

        // Set LayoutManager for RecyclerView (Week 9 Requirement)
        rvRecentLogs.setLayoutManager(new LinearLayoutManager(this));

        btnLogActivity.setOnClickListener(v -> {
            Toast.makeText(this, "Log Activity screen opening...", Toast.LENGTH_SHORT).show();
        });
    }
}