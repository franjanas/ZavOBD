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
import java.nio.charset.StandardCharsets;

public class DashboardActivity extends AppCompatActivity {

    private static final String TAG = "ZavOBD_Dashboard";

    private TextView tvConnectionStatus, tvSpeed, tvRpm;
    private CommunicationThread communicationThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        tvConnectionStatus = findViewById(R.id.tv_connection_status);
        tvSpeed = findViewById(R.id.tv_speed);
        tvRpm = findViewById(R.id.tv_rpm);

        BluetoothSocket socket = BluetoothConnectionManager.getInstance().getSocket();

        if (socket != null && socket.isConnected()) {
            tvConnectionStatus.setText("Status: Connected");
            // START THE MAGIC: Create and start our communication thread
            communicationThread = new CommunicationThread(socket);
            communicationThread.start();
        } else {
            tvConnectionStatus.setText("Status: Disconnected");
            Toast.makeText(this, "Failed to get a live connection.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // IMPORTANT: Stop the thread and close the connection when the activity is destroyed
        if (communicationThread != null) {
            communicationThread.cancel();
        }
        // The manager will handle closing the socket itself if needed,
        // but stopping the thread is our responsibility.
    }

    // ####################################################################
    // ## THE COMMUNICATION THREAD - V3 - BULLETPROOF INIT               ##
    // ####################################################################
    private class CommunicationThread extends Thread {
        private final BluetoothSocket socket;
        private final InputStream inputStream;
        private final OutputStream outputStream;
        private final StringBuilder responseBuffer = new StringBuilder();

        public CommunicationThread(BluetoothSocket socket) {
            this.socket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "Error obtaining streams", e);
            }
            inputStream = tmpIn;
            outputStream = tmpOut;
        }

        public void run() {
            try {
                // --- Step 1: Manual Protocol Initialization ---
                Log.d(TAG, "--- Starting MANUAL Protocol Initialization ---");
                sendCommand("ATZ\r");
                Thread.sleep(1500);
                sendCommand("ATE0\r");
                Thread.sleep(500);

                // --- THIS IS THE CRITICAL CHANGE ---
                // We are no longer using ATSP0 (auto). We are manually setting it to 6 (CAN 11-bit).
                Log.d(TAG, "Manually setting protocol to 6 (CAN 11-bit)...");
                sendCommand("ATSP6\r");
                Thread.sleep(500);

                // Send one test command to confirm it worked.
                sendCommand("0100\r");
                String initResponse = readResponse();
                Log.d(TAG, "Initial negotiation response: " + initResponse);

                // A valid response will start with "4100"
                if (!initResponse.contains("4100")) {
                    Log.e(TAG, "Could not communicate with ECU on Protocol 6. Response: " + initResponse);
                    runOnUiThread(() -> tvConnectionStatus.setText("Status: ECU Failed (Proto 6)"));
                    return; // Halting thread
                }

                Log.i(TAG, "SUCCESS! ECU is responsive on Protocol 6. Starting data loop.");
                runOnUiThread(() -> tvConnectionStatus.setText("Status: Live Data"));

                // --- Step 2: Data Loop ---
                while (!Thread.currentThread().isInterrupted()) {
                    sendCommand("010D\r");
                    String speedResponse = readResponse();
                    Log.d(TAG, "Raw Speed Response: " + speedResponse);
                    final int speed = parseSpeed(speedResponse);

                    sendCommand("010C\r");
                    String rpmResponse = readResponse();
                    Log.d(TAG, "Raw RPM Response: " + rpmResponse);
                    final int rpm = parseRpm(rpmResponse);

                    runOnUiThread(() -> {
                        tvSpeed.setText("Speed: " + speed + " km/h");
                        tvRpm.setText("RPM: " + rpm);
                    });
                    Thread.sleep(500);
                }

            } catch (IOException | InterruptedException e) {
                Log.e(TAG, "Connection lost or thread interrupted", e);
                runOnUiThread(() -> tvConnectionStatus.setText("Status: Connection Lost"));
            }
        }

        private void sendCommand(String command) throws IOException {
            while(inputStream.available() > 0) { inputStream.read(); }
            outputStream.write(command.getBytes());
            outputStream.flush();
        }

        private String readResponse() throws IOException, InterruptedException {
            responseBuffer.setLength(0); // Clear the buffer
            long startTime = System.currentTimeMillis();

            // Read until we see the '>' prompt or time out after 2 seconds
            while ((System.currentTimeMillis() - startTime) < 2000) {
                if (inputStream.available() > 0) {
                    char c = (char) inputStream.read();
                    if (c == '>') {
                        break; // End of response
                    }
                    responseBuffer.append(c);
                } else {
                    Thread.sleep(20); // Wait a bit for more data
                }
            }
            // Clean up the response: remove echoes, whitespace, etc.
            return responseBuffer.toString().replaceAll("\\s", "").replaceAll("\r", "").replaceAll("\n", "").replaceAll(">", "");
        }


        private int parseSpeed(String response) {
            // Updated to look for 410D at the start of the string, which is more reliable with headers on
            if (response.startsWith("410D")) {
                try {
                    String hexValue = response.substring(4, 6);
                    return Integer.parseInt(hexValue, 16);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse speed: " + response, e);
                    return 0;
                }
            }
            return 0;
        }

        private int parseRpm(String response) {
            // Updated to look for 410C at the start of the string
            if (response.startsWith("410C")) {
                try {
                    String hexValue = response.substring(4, 8);
                    int a = Integer.parseInt(hexValue.substring(0, 2), 16);
                    int b = Integer.parseInt(hexValue.substring(2, 4), 16);
                    return ((a * 256) + b) / 4;
                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse RPM: " + response, e);
                    return 0;
                }
            }
            return 0;
        }

        public void cancel() {
            try {
                interrupt();
                socket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the client socket", e);
            }
        }
    }
}