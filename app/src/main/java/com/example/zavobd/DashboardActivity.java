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

// These imports are for the gauge library. If they are red, you need to sync your Gradle file.
import com.github.anastr.speedviewlib.AwesomeSpeedometer;
import com.github.anastr.speedviewlib.SpeedView;

public class DashboardActivity extends AppCompatActivity {

    private static final String TAG = "ZavOBD_Dashboard";

    // Updated UI element declarations
    private TextView tvConnectionStatus, tvCoolant;
    private SpeedView speedView;
    private AwesomeSpeedometer rpmGauge;

    private CommunicationThread communicationThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        // Find all the UI elements by their ID from the XML layout
        tvConnectionStatus = findViewById(R.id.tv_connection_status);
        tvCoolant = findViewById(R.id.tv_coolant);
        speedView = findViewById(R.id.speed_view);
        rpmGauge = findViewById(R.id.rpm_gauge);

        // Get the live socket from our connection manager
        BluetoothSocket socket = BluetoothConnectionManager.getInstance().getSocket();

        // Check if the socket is valid and connected
        if (socket != null && socket.isConnected()) {
            tvConnectionStatus.setText("Status: Connected");
            // If everything is okay, create and start our communication thread
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
        // It's crucial to stop the background thread when the activity is destroyed
        // to prevent memory leaks and keep the app stable.
        if (communicationThread != null) {
            communicationThread.cancel();
        }
    }

    // This inner class handles all the background communication with the ELM327 adapter
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

                // Manually setting protocol to 6 (CAN 11-bit), the most common modern protocol.
                Log.d(TAG, "Manually setting protocol to 6 (CAN 11-bit)...");
                sendCommand("ATSP6\r");
                Thread.sleep(500);

                // Send a test command ("0100") to confirm the ECU is responsive on this protocol
                sendCommand("0100\r");
                String initResponse = readResponse();
                Log.d(TAG, "Initial negotiation response: " + initResponse);

                // A valid response must contain "4100"
                if (!initResponse.contains("4100")) {
                    Log.e(TAG, "Could not communicate with ECU on Protocol 6. Response: " + initResponse);
                    runOnUiThread(() -> tvConnectionStatus.setText("Status: ECU Failed (Proto 6)"));
                    return; // Stop the thread
                }

                Log.i(TAG, "SUCCESS! ECU is responsive on Protocol 6. Starting data loop.");
                runOnUiThread(() -> tvConnectionStatus.setText("Status: Live Data"));

                // --- Step 2: Continuous Data Loop ---
                while (!Thread.currentThread().isInterrupted()) {
                    // --- Get Vehicle Speed ---
                    sendCommand("010D\r");
                    String speedResponse = readResponse();
                    final int speed = parseSpeed(speedResponse);

                    // --- Get Engine RPM ---
                    sendCommand("010C\r");
                    String rpmResponse = readResponse();
                    final int rpm = parseRpm(rpmResponse);

                    // --- Get Engine Coolant Temperature ---
                    sendCommand("0105\r");
                    String coolantResponse = readResponse();
                    final int coolantTemp = parseCoolantTemp(coolantResponse);

                    // --- Update the UI ---
                    // This must be done on the main UI thread.
                    runOnUiThread(() -> {
                        speedView.speedTo(speed); // Update the speedometer gauge
                        rpmGauge.speedTo(rpm / 1000f); // Update the RPM gauge (divide by 1000)
                        tvCoolant.setText("Coolant: " + coolantTemp + " °C"); // Update the text view
                    });

                    Thread.sleep(500); // Pause before asking for the next set of data
                }

            } catch (IOException | InterruptedException e) {
                Log.e(TAG, "Connection lost or thread interrupted", e);
                runOnUiThread(() -> tvConnectionStatus.setText("Status: Connection Lost"));
            }
        }

        private void sendCommand(String command) throws IOException {
            // Clear any old data from the input stream before sending a new command
            while(inputStream.available() > 0) { inputStream.read(); }
            outputStream.write(command.getBytes());
            outputStream.flush();
        }

        private String readResponse() throws IOException, InterruptedException {
            responseBuffer.setLength(0);
            long startTime = System.currentTimeMillis();
            while ((System.currentTimeMillis() - startTime) < 2000) {
                if (inputStream.available() > 0) {
                    char c = (char) inputStream.read();
                    if (c == '>') {
                        break;
                    }
                    responseBuffer.append(c);
                } else {
                    Thread.sleep(20);
                }
            }
            return responseBuffer.toString().replaceAll("\\s", "").replaceAll("\r", "").replaceAll("\n", "").replaceAll(">", "");
        }


        private int parseSpeed(String response) {
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

        private int parseCoolantTemp(String response) {
            if (response.startsWith("4105")) {
                try {
                    String hexValue = response.substring(4, 6);
                    return Integer.parseInt(hexValue, 16) - 40;
                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse coolant temp: " + response, e);
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