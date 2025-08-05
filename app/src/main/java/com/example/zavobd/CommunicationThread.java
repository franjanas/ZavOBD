package com.example.zavobd;

import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.example.zavobd.obd.AbstractObdCommand;
import com.example.zavobd.obd.CoolantCommand;
import com.example.zavobd.obd.DtcCommand;
import com.example.zavobd.obd.FuelLevelCommand;
import com.example.zavobd.obd.MafCommand;
import com.example.zavobd.obd.RpmCommand;
import com.example.zavobd.obd.SpeedCommand;
import com.example.zavobd.PID;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;

public class CommunicationThread extends Thread {
    private static final String TAG = "CommunicationThread";

    // These message codes are now used for AFTER a connection is established
    public static final int MSG_UPDATE_DASHBOARD = 1;
    public static final int MSG_UPDATE_FUEL_STATS = 2;
    public static final int MSG_UPDATE_DTC_RESULT = 3;
    public static final int MSG_UPDATE_PID_RESULT = 4;
    public static final int MSG_CONNECTION_LOST = 98;
    public static final int MSG_INVALID_DEVICE = 99; // This is now unused but safe to keep

    // Mode constants
    public static final int MODE_IDLE = 0;
    public static final int MODE_DASHBOARD = 1;
    public static final int MODE_FUEL_STATS = 2;
    public static final int MODE_DTC_SCAN = 3;
    public static final int MODE_CUSTOM_SCAN = 4;
    public static final int MODE_DTC_CLEAR = 5;

    private final BluetoothSocket socket;
    private final InputStream inputStream;
    private final OutputStream outputStream;
    private final Handler serviceHandler;

    private volatile int currentMode = MODE_IDLE;
    private volatile ArrayList<PID> customScanPids = null;

    // Fuel stats variables
    private final double AIR_FUEL_RATIO = 14.7;
    private final double FUEL_DENSITY_GRAMS_PER_LITER = 745.0;
    private final Queue<Double> consumptionReadings = new LinkedList<>();
    private final int SMOOTHING_WINDOW_SIZE = 12;

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

    public void setMode(int mode) { this.currentMode = mode; }
    public void startCustomScan(ArrayList<PID> pids) {
        this.customScanPids = pids;
        setMode(MODE_CUSTOM_SCAN);
    }

    private String executeSimpleCommand(String command) throws IOException {
        if(outputStream == null || inputStream == null) return "";
        outputStream.write((command + "\r").getBytes());
        outputStream.flush();
        try { Thread.sleep(300); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        StringBuilder response = new StringBuilder();
        // This read is blocking, which is what we want for this simple command
        while(inputStream.available() > 0) {
            response.append((char) inputStream.read());
        }
        return response.toString().replaceAll("\\s", "");
    }

    // --- The run() method is now JUST the polling loop ---
    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                switch (currentMode) {
                    case MODE_DASHBOARD: pollDashboardData(); break;
                    case MODE_FUEL_STATS: pollFuelStatsData(); break;
                    case MODE_DTC_SCAN:
                        pollDtcData();
                        currentMode = MODE_IDLE;
                        break;
                    case MODE_DTC_CLEAR:
                        clearDtcCodes();
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

    // --- THIS IS THE NEW PUBLIC SANITY CHECK METHOD ---
    public String performSanityCheck() throws IOException {
        executeSimpleCommand("ATE0");
        executeSimpleCommand("ATSP6");
        return executeSimpleCommand("0100");
    }

    private void pollDashboardData() throws IOException, InterruptedException {
        SpeedCommand speedCmd = new SpeedCommand();
        RpmCommand rpmCmd = new RpmCommand();
        speedCmd.run(inputStream, outputStream);
        rpmCmd.run(inputStream, outputStream);
        Bundle bundle = new Bundle();
        bundle.putInt("speed", speedCmd.getResultValue());
        bundle.putInt("rpm", rpmCmd.getResultValue());
        Message msg = serviceHandler.obtainMessage(MSG_UPDATE_DASHBOARD);
        msg.obj = bundle;
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
        Log.d(TAG, "pollFuelStatsData - MAF from getMaf(): " + mafGramsPerSec + " g/s");
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
        Message msg = serviceHandler.obtainMessage(MSG_UPDATE_FUEL_STATS);
        msg.obj = bundle;
        serviceHandler.sendMessage(msg);
        Thread.sleep(1000);
    }

    private void pollDtcData() throws IOException, InterruptedException {
        DtcCommand dtcCmd = new DtcCommand();
        Log.d(TAG, "Polling DTC data: Running DtcCommand...");
        dtcCmd.run(inputStream, outputStream);
        Bundle bundle = new Bundle();
        ArrayList<String> codes = new ArrayList<>(dtcCmd.getFormattedCodes());
        Log.i(TAG, "Polling DTC Data: Codes from DtcCommand.getFormattedCodes(): " + (codes != null ? codes.toString() : "null")); // SAFER LOG
        bundle.putStringArrayList("dtcCodes", codes);
        Message msg = serviceHandler.obtainMessage(MSG_UPDATE_DTC_RESULT);
        msg.obj = bundle;
        serviceHandler.sendMessage(msg);
    }

    private void pollCustomData() throws IOException, InterruptedException {
        if (customScanPids == null || customScanPids.isEmpty()) {
            setMode(MODE_IDLE);
            return;
        }
        HashMap<String, String> results = new HashMap<>();
        for (PID pid : customScanPids) {
            AbstractObdCommand command = null;
            // This "factory" creates the correct command object based on the PID's command string.
            switch (pid.getCommand()) {
                case "010C": command = new RpmCommand(); break;
                case "010D": command = new SpeedCommand(); break;
                case "0105": command = new CoolantCommand(); break;
                case "012F": command = new FuelLevelCommand(); break;
                case "0110": command = new MafCommand(); break;
                // Add more "case" statements here for other PIDs you support.
            }

            if (command != null) {
                command.run(inputStream, outputStream);
                // We put the PID's command string and its final formatted result in the map.
                results.put(pid.getCommand(), command.getFormattedResult());
            } else {
                // If the command is not supported by a specific class, get the raw value.
                String rawResponse = executeSimpleCommand(pid.getCommand());
                results.put(pid.getCommand(), "Raw: " + rawResponse);
            }
        }

        Bundle bundle = new Bundle();
        bundle.putSerializable("customResults", results);
        Message msg = serviceHandler.obtainMessage(MSG_UPDATE_PID_RESULT);
        msg.obj = bundle;
        serviceHandler.sendMessage(msg);

        Thread.sleep(1000);
    }

    private void clearDtcCodes() throws IOException, InterruptedException {
        Log.d(TAG, "Executing Clear DTC command (04)...");
        outputStream.write("04\r".getBytes());
        outputStream.flush();
        Thread.sleep(1000);
    }

    public void cancel() {
        try {
            interrupt();
            if (socket != null) socket.close();
        } catch (IOException e) { Log.e(TAG, "Could not close socket", e); }
    }
}