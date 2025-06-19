package com.example.zavobd;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.github.anastr.speedviewlib.AwesomeSpeedometer;
import com.github.anastr.speedviewlib.SpeedView;

import com.example.zavobd.obd.RpmCommand;
import com.example.zavobd.obd.SpeedCommand;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

// --- NEW: Define the listener interface here, at the top level of the file ---
interface ConnectionLossListener {
    void onConnectionLost();
}

// Now, implementing the interface is clean and valid.
public class DashboardActivity extends AppCompatActivity implements ConnectionLossListener {

    private static final String TAG = "ZavOBD_Dashboard";

    private TextView tvConnectionStatus;
    private SpeedView speedView;
    private AwesomeSpeedometer rpmGauge;
    private CommunicationThread communicationThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        tvConnectionStatus = findViewById(R.id.tv_connection_status);
        speedView = findViewById(R.id.speed_view);
        rpmGauge = findViewById(R.id.rpm_gauge);

        BluetoothSocket socket = BluetoothConnectionManager.getInstance().getSocket();
        if (socket != null && socket.isConnected()) {
            // Pass 'this' (the listener) to the thread
            communicationThread = new CommunicationThread(socket, this);
            communicationThread.start();
        } else {
            Toast.makeText(this, "Connection Error", Toast.LENGTH_SHORT).show();
            finish(); // Go back if there's no connection
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (communicationThread != null) {
            communicationThread.cancel();
        }
    }

    // This method is required by our listener. It's called from the background thread.
    @Override
    public void onConnectionLost() {
        // Use runOnUiThread because this is called from a background thread
        runOnUiThread(() -> {
            new AlertDialog.Builder(this)
                    .setTitle("Connection Lost")
                    .setMessage("Communication with the OBD-II adapter has been lost.")
                    .setPositiveButton("Reconnect", (dialog, which) -> {
                        // Restart the connection process by going back to MainActivity
                        dialog.dismiss();
                        Intent intent = new Intent(DashboardActivity.this, MainActivity.class);
                        // These flags clear the activity history so you can't go "back" to a dead dashboard
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> {
                        dialog.dismiss();
                        finish(); // Go back to previous screen
                    })
                    .setCancelable(false) // Prevent user from dismissing it by tapping outside
                    .show();
        });
    }

    // The inner class no longer defines the interface, it just uses it.
    private static class CommunicationThread extends Thread {
        private final BluetoothSocket socket;
        private final InputStream inputStream;
        private final OutputStream outputStream;
        private final ConnectionLossListener listener; // Listener instance

        public CommunicationThread(BluetoothSocket socket, ConnectionLossListener listener) {
            this.socket = socket;
            this.listener = listener; // Store the listener
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { Log.e(TAG, "Error obtaining streams", e); }
            inputStream = tmpIn;
            outputStream = tmpOut;
        }

        private void executeSimpleCommand(String command) throws IOException {
            if(outputStream == null) return;
            outputStream.write((command + "\r").getBytes());
            outputStream.flush();
            try { Thread.sleep(200); } catch (InterruptedException e) { /* ignore */ }
            if(inputStream == null) return;
            while(inputStream.available() > 0) { inputStream.read(); }
        }

        public void run() {
            final SpeedCommand speedCmd = new SpeedCommand();
            final RpmCommand rpmCmd = new RpmCommand();
            try {
                executeSimpleCommand("ATE0");

                // --- CORRECTED: Use the listener to update the UI safely ---
                if (listener instanceof DashboardActivity) {
                    ((DashboardActivity) listener).runOnUiThread(() -> {
                        ((DashboardActivity) listener).tvConnectionStatus.setText("Status: Live Data");
                    });
                }

                while (!Thread.currentThread().isInterrupted()) {
                    speedCmd.run(inputStream, outputStream);
                    rpmCmd.run(inputStream, outputStream);

                    // --- CORRECTED: Use the listener to update the UI safely ---
                    if (listener instanceof DashboardActivity) {
                        ((DashboardActivity) listener).runOnUiThread(() -> {
                            ((DashboardActivity) listener).speedView.speedTo(speedCmd.getResultValue());
                            ((DashboardActivity) listener).rpmGauge.speedTo(rpmCmd.getResultValue() / 1000f);
                        });
                    }

                    Thread.sleep(500);
                }
            } catch (IOException | InterruptedException e) {
                Log.e(TAG, "Communication failed. Notifying listener.", e);
                // Call the listener's onConnectionLost method
                if (listener != null) {
                    listener.onConnectionLost();
                }
            }
        }

        public void cancel() {
            try {
                interrupt();
                socket.close();
            } catch (IOException e) { /* ignore */ }
        }
    }
}