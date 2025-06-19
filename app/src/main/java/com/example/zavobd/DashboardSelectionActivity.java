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
        Button btnFuelOnly = findViewById(R.id.btn_fuel_only);

        btnFullDashboard.setOnClickListener(v -> {
            // Launch the DashboardActivity and tell it to show everything
            Intent intent = new Intent(DashboardSelectionActivity.this, DashboardActivity.class);
            intent.putExtra("DASHBOARD_TYPE", "FULL");
            startActivity(intent);
        });

        btnFuelOnly.setOnClickListener(v -> {
            // Launch the DashboardActivity and tell it to show only the fuel gauge
            Intent intent = new Intent(DashboardSelectionActivity.this, FuelStatsActivity.class);
            startActivity(intent);
        });
    }
}