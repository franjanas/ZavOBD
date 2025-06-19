package com.example.zavobd;

import androidx.appcompat.app.AppCompatActivity;

import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.zavobd.obd.FuelLevelCommand;
import com.example.zavobd.obd.MafCommand;
import com.example.zavobd.obd.SpeedCommand;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

public class FuelStatsActivity extends AppCompatActivity {

    private static final String TAG = "ZavOBD_FuelStats";
    // These are the declarations, which are correct.
    private TextView tvCurrentConsumption, tvIdleConsumption, tvFuelLevel;
    private ProgressBar fuelLevelProgress;
    private CommunicationThread communicationThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fuel_stats);

        // --- THIS IS THE FIX ---
        // These lines were missing. They link the Java variables to the XML views.
        tvCurrentConsumption = findViewById(R.id.tv_current_consumption);
        tvIdleConsumption = findViewById(R.id.tv_idle_consumption);
        tvFuelLevel = findViewById(R.id.tv_fuel_level);
        fuelLevelProgress = findViewById(R.id.fuel_level_progress);
        // --- END OF FIX ---


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

    // The entire CommunicationThread class below is correct and does not need changes.
    private class CommunicationThread extends Thread {
        private final BluetoothSocket socket;
        private final InputStream inputStream;
        private final OutputStream outputStream;
        // Constants for fuel calculation
        private final double AIR_FUEL_RATIO = 14.7;
        private final double FUEL_DENSITY_GRAMS_PER_LITER = 737.0;


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

        private String executeSimpleCommand(String command) throws IOException {
            // Simplified command execution for setup
            outputStream.write((command + "\r").getBytes());
            outputStream.flush();
            try { Thread.sleep(200); } catch (InterruptedException e) { /* ignore */ }
            StringBuilder response = new StringBuilder();
            while(inputStream.available() > 0) {
                response.append((char) inputStream.read());
            }
            return response.toString().replaceAll("\\s", "");
        }

        public void run() {
            // Create command instances
            final SpeedCommand speedCmd = new SpeedCommand();
            final MafCommand mafCmd = new MafCommand();
            final FuelLevelCommand fuelCmd = new FuelLevelCommand();

            try {
                // Simplified Init... assumes connection is already configured
                executeSimpleCommand("ATE0");

                while (!Thread.currentThread().isInterrupted()) {
                    // Run all necessary commands
                    speedCmd.run(inputStream, outputStream);
                    mafCmd.run(inputStream, outputStream);
                    fuelCmd.run(inputStream, outputStream);

                    // Get the raw values
                    final double speedKmh = speedCmd.getResultValue();
                    final double mafGramsPerSec = mafCmd.getMaf();
                    final int fuelLevelPercent = fuelCmd.getResultValue();

                    // --- Perform Fuel Calculations ---
                    // Liters per hour = (grams per second * 3600 seconds/hour) / (density * air/fuel ratio)
                    final double fuelLitersPerHour = (mafGramsPerSec * 3600) / (FUEL_DENSITY_GRAMS_PER_LITER * AIR_FUEL_RATIO);

                    // Liters per 100km = (Liters per hour / km per hour) * 100
                    final double fuelLitersPer100km = (speedKmh > 0) ? (fuelLitersPerHour / speedKmh) * 100 : 0.0;

                    runOnUiThread(() -> {
                        // Update Fuel Level
                        tvFuelLevel.setText(String.format(Locale.US, "Fuel Level: %d%%", fuelLevelPercent));
                        fuelLevelProgress.setProgress(fuelLevelPercent);

                        // Update Consumption TextViews
                        if (speedKmh > 0) {
                            tvCurrentConsumption.setText(String.format(Locale.US, "%.1f L/100km", fuelLitersPer100km));
                        } else {
                            tvCurrentConsumption.setText("-- L/100km"); // Or "N/A"
                        }
                        tvIdleConsumption.setText(String.format(Locale.US, "%.1f L/h", fuelLitersPerHour));
                    });

                    Thread.sleep(1000); // Slower update for fuel stats is fine
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