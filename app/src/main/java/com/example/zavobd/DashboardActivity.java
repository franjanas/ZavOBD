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
import android.widget.TextView;
import android.widget.Toast;

import com.github.anastr.speedviewlib.AwesomeSpeedometer;
import com.github.anastr.speedviewlib.SpeedView;

public class DashboardActivity extends AppCompatActivity {
    private static final String TAG = "ZavOBD_Dashboard";

    // UI Elements
    private TextView tvConnectionStatus;
    private SpeedView speedView;
    private AwesomeSpeedometer rpmGauge;

    // Service-related variables
    private ObdService obdService;
    private boolean isServiceBound = false;

    // --- The Handler for receiving messages from the ObdService ---
    private final Handler uiHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case CommunicationThread.MSG_UPDATE_DASHBOARD:
                    // This case is triggered when new data arrives
                    Bundle bundle = (Bundle) msg.obj;
                    speedView.speedTo(bundle.getInt("speed"));
                    rpmGauge.speedTo(bundle.getInt("rpm") / 1000f);
                    break;
                case CommunicationThread.MSG_CONNECTION_LOST:
                    // This case is triggered if the connection drops during use
                    showErrorDialog("Connection Lost", "Communication with the device has been lost.");
                    break;
            }
        }
    };

    // --- The ServiceConnection manages the link between the Activity and the Service ---
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            ObdService.ObdServiceBinder binder = (ObdService.ObdServiceBinder) service;
            obdService = binder.getService();
            isServiceBound = true;

            // Now that we are bound, register this activity's handler with the service
            obdService.registerClient(uiHandler);
            // And tell the service to start sending us dashboard data
            obdService.setCommunicationMode(CommunicationThread.MODE_DASHBOARD);
            tvConnectionStatus.setText("Status: Live Data");
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            isServiceBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        // Find all the UI elements from the XML layout
        tvConnectionStatus = findViewById(R.id.tv_connection_status);
        speedView = findViewById(R.id.speed_view);
        rpmGauge = findViewById(R.id.rpm_gauge);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // When the activity becomes visible, bind to the ObdService
        Intent intent = new Intent(this, ObdService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // When the activity is no longer visible, unbind from the service
        if (isServiceBound) {
            if (obdService != null) {
                // Tell the service to stop sending data for this dashboard
                obdService.setCommunicationMode(CommunicationThread.MODE_IDLE);
                // Unregister this activity's handler
                obdService.unregisterClient();
            }
            unbindService(serviceConnection);
            isServiceBound = false;
        }
    }

    // A reusable method to show an error dialog and safely return to the main screen
    private void showErrorDialog(String title, String message) {
        // Ensure the activity is still running before showing a dialog
        if (!isFinishing()) {
            new AlertDialog.Builder(this)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton("Go to Connection Screen", (dialog, which) -> {
                        dialog.dismiss();
                        Intent intent = new Intent(DashboardActivity.this, MainActivity.class);
                        // These flags clear the activity history, creating a fresh start
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    })
                    .setCancelable(false)
                    .show();
        }
    }
}