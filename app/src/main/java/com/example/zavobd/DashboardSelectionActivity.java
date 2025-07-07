package com.example.zavobd;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

public class DashboardSelectionActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard_selection);

        // Find all four buttons from our layout
        Button btnFullDashboard = findViewById(R.id.btn_full_dashboard);
        Button btnFuelStats = findViewById(R.id.btn_fuel_stats);
        Button btnCheckCodes = findViewById(R.id.btn_check_codes);
        Button btnAdvancedScan = findViewById(R.id.btn_advanced_scan); // The new button

        // --- Assign the correct listener to each button ---

        // Listener for the Live Gauges Dashboard
        btnFullDashboard.setOnClickListener(v -> {
            Intent intent = new Intent(DashboardSelectionActivity.this, DashboardActivity.class);
            startActivity(intent);
        });

        // Listener for the Fuel Economy screen (Restored to its original function)
        btnFuelStats.setOnClickListener(v -> {
            Intent intent = new Intent(DashboardSelectionActivity.this, FuelStatsActivity.class);
            startActivity(intent);
        });

        // Listener for the Check Engine (DTC) screen
        btnCheckCodes.setOnClickListener(v -> {
            Intent intent = new Intent(DashboardSelectionActivity.this, DtcActivity.class);
            startActivity(intent);
        });

        // Listener for our new Advanced PID Scan screen
        btnAdvancedScan.setOnClickListener(v -> {
            Intent intent = new Intent(DashboardSelectionActivity.this, PidSelectionActivity.class);
            startActivity(intent);
        });
    }
}