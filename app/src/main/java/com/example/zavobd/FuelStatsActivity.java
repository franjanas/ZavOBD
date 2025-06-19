package com.example.zavobd;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
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
import java.util.LinkedList;
import java.util.Locale;
import java.util.Queue;

public class FuelStatsActivity extends AppCompatActivity {

    private static final String TAG = "ZavOBD_FuelStats";

    // Handler Message Codes
    private static final int MSG_UPDATE_STATS = 1;
    private static final int MSG_CONNECTION_LOST = 2;
    private static final int MSG_INVALID_DEVICE = 3;

    private TextView tvCurrentConsumption, tvIdleConsumption, tvFuelLevel;
    private ProgressBar fuelLevelProgress;
    private CommunicationThread communicationThread;

    private final Handler uiHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_UPDATE_STATS:
                    Bundle bundle = (Bundle) msg.obj;
                    updateFuelUi(
                            bundle.getInt("fuelLevel"),
                            bundle.getDouble("smoothedConsumption"),
                            bundle.getDouble("idleConsumption"),
                            bundle.getDouble("speed")
                    );
                    break;
                case MSG_CONNECTION_LOST:
                    showErrorDialog("Connection Lost", "Communication with the device has been lost.");
                    break;
                case MSG_INVALID_DEVICE:
                    showErrorDialog("Invalid Device", "This does not appear to be a valid OBD-II adapter. Please connect to the correct device.");
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fuel_stats);

        tvCurrentConsumption = findViewById(R.id.tv_current_consumption);
        tvIdleConsumption = findViewById(R.id.tv_idle_consumption);
        tvFuelLevel = findViewById(R.id.tv_fuel_level);
        fuelLevelProgress = findViewById(R.id.fuel_level_progress);

        BluetoothSocket socket = BluetoothConnectionManager.getInstance().getSocket();
        if (socket != null && socket.isConnected()) {
            communicationThread = new CommunicationThread(socket, uiHandler);
            communicationThread.start();
        } else {
            Toast.makeText(this, "Connection not established.", Toast.LENGTH_SHORT).show();
            finish();
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (communicationThread != null) {
            communicationThread.cancel();
        }
    }

    private static class CommunicationThread extends Thread {
        private final BluetoothSocket socket;
        private final InputStream inputStream;
        private final OutputStream outputStream;
        private final Handler handler;

        private final double AIR_FUEL_RATIO = 14.7;
        private final double FUEL_DENSITY_GRAMS_PER_LITER = 720.0;
        private final Queue<Double> consumptionReadings = new LinkedList<>();
        private final int SMOOTHING_WINDOW_SIZE = 5;

        public CommunicationThread(BluetoothSocket socket, Handler handler) {
            this.socket = socket;
            this.handler = handler;
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
            if(outputStream == null) return "";
            outputStream.write((command + "\r").getBytes());
            outputStream.flush();
            try { Thread.sleep(200); } catch (InterruptedException e) { /* ignore */ }
            if(inputStream == null) return "";
            StringBuilder response = new StringBuilder();
            while(inputStream.available() > 0) {
                response.append((char) inputStream.read());
            }
            return response.toString().replaceAll("\\s", "");
        }

        @Override
        public void run() {
            try {
                // Sanity Check
                String testResponse = executeSimpleCommand("0100");
                if (!testResponse.contains("4100")) {
                    handler.sendEmptyMessage(MSG_INVALID_DEVICE);
                    return;
                }

                final SpeedCommand speedCmd = new SpeedCommand();
                final MafCommand mafCmd = new MafCommand();
                final FuelLevelCommand fuelCmd = new FuelLevelCommand();

                while (!Thread.currentThread().isInterrupted()) {
                    speedCmd.run(inputStream, outputStream);
                    mafCmd.run(inputStream, outputStream);
                    fuelCmd.run(inputStream, outputStream);

                    final double speedKmh = speedCmd.getResultValue();
                    final double mafGramsPerSec = mafCmd.getMaf();
                    final int fuelLevelPercent = fuelCmd.getResultValue();
                    final double fuelLitersPerHour = (mafGramsPerSec * 3600) / (FUEL_DENSITY_GRAMS_PER_LITER * AIR_FUEL_RATIO);
                    final double instantaneousLitersPer100km = (speedKmh > 0) ? (fuelLitersPerHour / speedKmh) * 100 : 0.0;

                    consumptionReadings.add(instantaneousLitersPer100km);
                    if (consumptionReadings.size() > SMOOTHING_WINDOW_SIZE) {
                        consumptionReadings.poll();
                    }
                    double sum = 0;
                    for (double reading : consumptionReadings) { sum += reading; }
                    final double smoothedLitersPer100km = sum / consumptionReadings.size();

                    Bundle bundle = new Bundle();
                    bundle.putInt("fuelLevel", fuelLevelPercent);
                    bundle.putDouble("smoothedConsumption", smoothedLitersPer100km);
                    bundle.putDouble("idleConsumption", fuelLitersPerHour);
                    bundle.putDouble("speed", speedKmh);

                    Message link = handler.obtainMessage(MSG_UPDATE_STATS, bundle);
                    handler.sendMessage(link);

                    Thread.sleep(1000);
                }
            } catch (IOException | InterruptedException e) {
                Log.e(TAG, "Communication lost.", e);
                handler.sendEmptyMessage(MSG_CONNECTION_LOST);
            }
        }

        public void cancel() {
            try {
                interrupt();
                if (socket != null) {
                    socket.close();
                }
            } catch (IOException e) { Log.e(TAG, "Could not close the client socket", e); }
        }
    }
}