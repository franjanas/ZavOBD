package com.example.zavobd;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

// The import for DashboardActivity might be missing in your file.
import com.example.zavobd.DashboardActivity;

public class DashboardSelectionActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard_selection);

        Button btnFullDashboard = findViewById(R.id.btn_full_dashboard);
        Button btnFuelStats = findViewById(R.id.btn_fuel_stats);
        Button btnCheckCodes = findViewById(R.id.btn_check_codes);

        btnFullDashboard.setOnClickListener(v -> {
            // This line will now work because of the import statement above
            Intent intent = new Intent(DashboardSelectionActivity.this, DashboardActivity.class);
            startActivity(intent);
        });

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