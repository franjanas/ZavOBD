package com.example.zavobd;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.util.Locale;

public class FuelStatsActivity extends AppCompatActivity {

    private TextView tvCurrentConsumption, tvIdleConsumption, tvFuelLevel;
    private ProgressBar fuelLevelProgress;

    private ObdService obdService;
    private boolean isServiceBound = false;

    private final Handler uiHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case CommunicationThread.MSG_UPDATE_FUEL_STATS:
                    Bundle bundle = (Bundle) msg.obj;
                    updateFuelUi(
                            bundle.getInt("fuelLevel"),
                            bundle.getDouble("smoothedConsumption"),
                            bundle.getDouble("idleConsumption"),
                            bundle.getDouble("speed")
                    );
                    break;
                // --- THIS IS THE FIX ---
                case CommunicationThread.MSG_CONNECTION_LOST:
                    showErrorDialog("Connection Lost", "Communication with the device has been lost.");
                    break;
                case CommunicationThread.MSG_INVALID_DEVICE:
                    showErrorDialog("Invalid Device", "This is not a valid OBD-II adapter.");
                    break;
            }
        }
    };

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            ObdService.ObdServiceBinder binder = (ObdService.ObdServiceBinder) service;
            obdService = binder.getService();
            isServiceBound = true;
            obdService.registerClient(uiHandler);
            obdService.setCommunicationMode(CommunicationThread.MODE_FUEL_STATS);
        }
        @Override
        public void onServiceDisconnected(ComponentName arg0) { isServiceBound = false; }
    };

    private void showErrorDialog(String title, String message) {
        if (!isFinishing()) {
            new AlertDialog.Builder(this)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton("Go to Connection Screen", (dialog, which) -> {
                        dialog.dismiss();
                        Intent intent = new Intent(FuelStatsActivity.this, MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    })
                    .setCancelable(false)
                    .show();
        }
    }

    // --- The rest of the file is mostly the same ---
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fuel_stats);
        tvCurrentConsumption = findViewById(R.id.tv_current_consumption);
        tvIdleConsumption = findViewById(R.id.tv_idle_consumption);
        tvFuelLevel = findViewById(R.id.tv_fuel_level);
        fuelLevelProgress = findViewById(R.id.fuel_level_progress);
    }
    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, ObdService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }
    @Override
    protected void onStop() {
        super.onStop();
        if (isServiceBound) {
            if (obdService != null) {
                obdService.setCommunicationMode(CommunicationThread.MODE_IDLE);
                obdService.unregisterClient();
            }
            unbindService(serviceConnection);
            isServiceBound = false;
        }
    }
    private void updateFuelUi(int fuelLevelPercent, double smoothedLitersPer100km, double fuelLitersPerHour, double speedKmh) {
        tvFuelLevel.setText(String.format(Locale.US, "Fuel Level: %d%%", fuelLevelPercent));
        fuelLevelProgress.setProgress(fuelLevelPercent);
        if (speedKmh > 0) {
            tvCurrentConsumption.setText(String.format(Locale.US, "%.1f L/100km", smoothedLitersPer100km));
        } else {
            tvCurrentConsumption.setText("-- L/100km");
        }
        tvIdleConsumption.setText(String.format(Locale.US, "%.1f L/h", fuelLitersPerHour));
    }
}