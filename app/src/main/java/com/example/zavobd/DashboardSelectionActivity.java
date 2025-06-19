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

        Button btnFullDashboard = findViewById(R.id.btn_full_dashboard);
        // Rename the old button variable for clarity
        Button btnFuelStats = findViewById(R.id.btn_fuel_stats);
        // Find the new button
        Button btnCheckCodes = findViewById(R.id.btn_check_codes);

        btnFullDashboard.setOnClickListener(v -> {
            Intent intent = new Intent(DashboardSelectionActivity.this, DashboardActivity.class);
            startActivity(intent);
        });

        // Add the listener for the new button
        btnCheckCodes.setOnClickListener(v -> {
            Intent intent = new Intent(DashboardSelectionActivity.this, DtcActivity.class);
            startActivity(intent);
        });

        btnFuelStats.setOnClickListener(v -> {
            Intent intent = new Intent(DashboardSelectionActivity.this, FuelStatsActivity.class);
            startActivity(intent);
        });
    }
}