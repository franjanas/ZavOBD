package com.example.zavobd;

import androidx.appcompat.app.AppCompatActivity;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.github.anastr.speedviewlib.AwesomeSpeedometer;
import com.github.anastr.speedviewlib.SpeedView;

import com.example.zavobd.obd.RpmCommand;
import com.example.zavobd.obd.SpeedCommand;

public class DashboardActivity extends AppCompatActivity {

    private static final String TAG = "ZavOBD_Dashboard";

    private TextView tvConnectionStatus;
    private SpeedView speedView;
    private AwesomeSpeedometer rpmGauge;
    private CommunicationThread communicationThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        // All views are now part of the included layout, but we can still find them
        tvConnectionStatus = findViewById(R.id.tv_connection_status);
        speedView = findViewById(R.id.speed_view);
        rpmGauge = findViewById(R.id.rpm_gauge);

        BluetoothSocket socket = BluetoothConnectionManager.getInstance().getSocket();
        if (socket != null && socket.isConnected()) {
            communicationThread = new CommunicationThread(socket);
            communicationThread.start();
        } else {
            Toast.makeText(this, "Connection Error", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (communicationThread != null) {
            communicationThread.cancel();
        }
    }

    private class CommunicationThread extends Thread {
        private final BluetoothSocket socket;
        private final InputStream inputStream;
        private final OutputStream outputStream;

        public CommunicationThread(BluetoothSocket socket) {
            this.socket = socket;
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
            // Simplified command execution for setup
            outputStream.write((command + "\r").getBytes());
            outputStream.flush();
            try { Thread.sleep(200); } catch (InterruptedException e) { /* ignore */ }
            while(inputStream.available() > 0) { inputStream.read(); }
        }

        public void run() {
            final SpeedCommand speedCmd = new SpeedCommand();
            final RpmCommand rpmCmd = new RpmCommand();

            try {
                // Simplified Init
                executeSimpleCommand("ATE0");

                runOnUiThread(() -> tvConnectionStatus.setText("Status: Live Data"));

                while (!Thread.currentThread().isInterrupted()) {
                    speedCmd.run(inputStream, outputStream);
                    rpmCmd.run(inputStream, outputStream);

                    runOnUiThread(() -> {
                        speedView.speedTo(speedCmd.getResultValue());
                        rpmGauge.speedTo(rpmCmd.getResultValue() / 1000f);
                    });

                    Thread.sleep(500);
                }
            } catch (IOException | InterruptedException e) {
                Log.e(TAG, "Communication failed", e);
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