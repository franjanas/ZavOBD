package com.example.zavobd;

import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.example.zavobd.obd.AbstractObdCommand;
import com.example.zavobd.obd.DtcCommand;
import com.example.zavobd.obd.FuelLevelCommand;
import com.example.zavobd.obd.MafCommand;
import com.example.zavobd.obd.RpmCommand;
import com.example.zavobd.obd.SpeedCommand;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;

public class CommunicationThread extends Thread {
    private static final String TAG = "CommunicationThread";

    // Handler Message Codes
    public static final int MSG_UPDATE_DASHBOARD = 1;
    public static final int MSG_UPDATE_FUEL_STATS = 2;
    public static final int MSG_UPDATE_DTC_RESULT = 3;
    public static final int MSG_UPDATE_CUSTOM_RESULTS = 4;
    public static final int MSG_CONNECTION_LOST = 98;
    public static final int MSG_INVALID_DEVICE = 99;

    // Mode constants
    public static final int MODE_IDLE = 0;
    public static final int MODE_DASHBOARD = 1;
    public static final int MODE_FUEL_STATS = 2;
    public static final int MODE_DTC_SCAN = 3;
    public static final int MODE_CUSTOM_SCAN = 4;

    private final BluetoothSocket socket;
    private final InputStream inputStream;
    private final OutputStream outputStream;
    private final Handler serviceHandler;
    private volatile int currentMode = MODE_IDLE;
    private volatile ArrayList<PID> customScanPids = null;

    private final double AIR_FUEL_RATIO = 14.7;
    private final double FUEL_DENSITY_GRAMS_PER_LITER = 720.0;
    private final Queue<Double> consumptionReadings = new LinkedList<>();
    private final int SMOOTHING_WINDOW_SIZE = 5;

    public CommunicationThread(BluetoothSocket socket, Handler serviceHandler) {
        this.socket = socket;
        this.serviceHandler = serviceHandler;
        InputStream tmpIn = null;
        OutputStream tmpOut = null;
        try {
            tmpIn = socket.getInputStream();
            tmpOut = socket.getOutputStream();
        } catch (IOException e) { Log.e(TAG, "Error obtaining streams", e); }
        inputStream = tmpIn;
        outputStream = tmpOut;
    }

    public void setMode(int mode) {
        this.currentMode = mode;
    }

    public void setCustomScanPids(ArrayList<PID> pids) {
        this.customScanPids = pids;
    }

    private String executeSimpleCommand(String command) throws IOException {
        if(outputStream == null || inputStream == null) return "";
        outputStream.write((command + "\r").getBytes());
        outputStream.flush();
        try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        StringBuilder response = new StringBuilder();
        while(inputStream.available() > 0) {
            response.append((char) inputStream.read());
        }
        return response.toString().replaceAll("\\s", "");
    }

    // --- THIS IS THE METHOD WITH THE FIX ---
    public String performSanityCheck() throws IOException {
        executeSimpleCommand("ATE0"); // Echo off
        executeSimpleCommand("ATSP6"); // Set protocol to CAN

        // --- THE FIX ---
        // Add a crucial delay here. This gives the adapter time to lock onto the
        // car's ECU before we ask it for data. This prevents the race condition.
        try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        return executeSimpleCommand("0100"); // Now, check for supported PIDs
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                switch (currentMode) {
                    case MODE_DASHBOARD:
                        pollDashboardData();
                        break;
                    case MODE_FUEL_STATS:
                        pollFuelStatsData();
                        break;
                    case MODE_DTC_SCAN:
                        pollDtcData();
                        currentMode = MODE_IDLE;
                        break;
                    case MODE_CUSTOM_SCAN:
                        pollCustomData();
                        break;
                    case MODE_IDLE:
                    default:
                        Thread.sleep(1000);
                        break;
                }
            }
        } catch (IOException | InterruptedException e) {
            Log.e(TAG, "Communication lost.", e);
            serviceHandler.sendEmptyMessage(MSG_CONNECTION_LOST);
        }
    }

    private void pollDashboardData() throws IOException, InterruptedException {
        SpeedCommand speedCmd = new SpeedCommand();
        RpmCommand rpmCmd = new RpmCommand();
        speedCmd.run(inputStream, outputStream);
        rpmCmd.run(inputStream, outputStream);
        Bundle bundle = new Bundle();
        bundle.putInt("speed", speedCmd.getResultValue());
        bundle.putInt("rpm", rpmCmd.getResultValue());
        Message msg = serviceHandler.obtainMessage(MSG_UPDATE_DASHBOARD, bundle);
        serviceHandler.sendMessage(msg);
        Thread.sleep(500);
    }

    private void pollFuelStatsData() throws IOException, InterruptedException {
        SpeedCommand speedCmd = new SpeedCommand();
        MafCommand mafCmd = new MafCommand();
        FuelLevelCommand fuelCmd = new FuelLevelCommand();
        speedCmd.run(inputStream, outputStream);
        mafCmd.run(inputStream, outputStream);
        fuelCmd.run(inputStream, outputStream);
        final double speedKmh = speedCmd.getResultValue();
        final double mafGramsPerSec = mafCmd.getMaf();
        final int fuelLevelPercent = fuelCmd.getResultValue();
        final double fuelLitersPerHour = (mafGramsPerSec * 3600) / (FUEL_DENSITY_GRAMS_PER_LITER * AIR_FUEL_RATIO);
        final double instantaneousLitersPer100km = (speedKmh > 0) ? (fuelLitersPerHour / speedKmh) * 100 : 0.0;
        consumptionReadings.add(instantaneousLitersPer100km);
        if (consumptionReadings.size() > SMOOTHING_WINDOW_SIZE) consumptionReadings.poll();
        double sum = 0;
        for (double reading : consumptionReadings) sum += reading;
        final double smoothedLitersPer100km = sum / consumptionReadings.size();
        Bundle bundle = new Bundle();
        bundle.putInt("fuelLevel", fuelLevelPercent);
        bundle.putDouble("smoothedConsumption", smoothedLitersPer100km);
        bundle.putDouble("idleConsumption", fuelLitersPerHour);
        bundle.putDouble("speed", speedKmh);
        Message msg = serviceHandler.obtainMessage(MSG_UPDATE_FUEL_STATS, bundle);
        serviceHandler.sendMessage(msg);
        Thread.sleep(1000);
    }

    private void pollDtcData() throws IOException, InterruptedException {
        DtcCommand dtcCmd = new DtcCommand();
        dtcCmd.run(inputStream, outputStream);
        Bundle bundle = new Bundle();
        bundle.putStringArrayList("dtcCodes", new ArrayList<>(dtcCmd.getFormattedCodes()));
        Message msg = serviceHandler.obtainMessage(MSG_UPDATE_DTC_RESULT, bundle);
        serviceHandler.sendMessage(msg);
    }

    private void pollCustomData() throws IOException, InterruptedException {
        if (customScanPids == null || customScanPids.isEmpty()) {
            setMode(MODE_IDLE);
            return;
        }
        ArrayList<String> results = new ArrayList<>();
        for (PID pid : customScanPids) {
            AbstractObdCommand command = new AbstractObdCommand(pid.getCommand(), pid.getDescription(), "") {
                @Override
                protected void performCalculations() { }
                @Override
                public String getFormattedResult() { return rawResponse != null ? rawResponse : "NO DATA"; }
            };
            command.run(inputStream, outputStream);
            results.add(command.getFormattedResult());
        }
        Bundle bundle = new Bundle();
        bundle.putStringArrayList("customResults", results);
        Message msg = serviceHandler.obtainMessage(MSG_UPDATE_CUSTOM_RESULTS, bundle);
        serviceHandler.sendMessage(msg);
        Thread.sleep(1000);
    }

    public void cancel() {
        try {
            interrupt();
            if (socket != null) socket.close();
        } catch (IOException e) { Log.e(TAG, "Could not close socket", e); }
    }
}