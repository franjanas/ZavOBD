package com.example.zavobd;

import android.Manifest;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager; // Added import

import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;

import com.example.zavobd.PID;

public class ObdService extends Service {
    private static final String TAG = "ObdService";

    public static final String ACTION_CONNECTION_SUCCESS = "com.example.zavobd.ACTION_CONNECTION_SUCCESS";
    public static final String ACTION_CONNECTION_FAILURE = "com.example.zavobd.ACTION_CONNECTION_FAILURE";
    public static final String EXTRA_FAILURE_MESSAGE = "EXTRA_FAILURE_MESSAGE";
    public static final String ACTION_CONNECT = "com.example.zavobd.ACTION_CONNECT";
    public static final String ACTION_DISCONNECT = "com.example.zavobd.ACTION_DISCONNECT";
    public static final String EXTRA_DEVICE_ADDRESS = "EXTRA_DEVICE_ADDRESS";

    private BluetoothSocket socket;
    private CommunicationThread communicationThread;
    private final IBinder binder = new ObdServiceBinder();
    private Handler activityHandler = null;

    public class ObdServiceBinder extends Binder {
        public ObdService getService() {
            return ObdService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_CONNECT.equals(intent.getAction())) {
            final String deviceAddress = intent.getStringExtra(EXTRA_DEVICE_ADDRESS);
            if (deviceAddress != null) {
                new Thread(() -> connectToDevice(deviceAddress)).start();
            }
        } else if (intent != null && ACTION_DISCONNECT.equals(intent.getAction())) {
            stopService();
        }
        return START_NOT_STICKY;
    }

    private void connectToDevice(String macAddress) {
        try {
            if (socket != null && socket.isConnected()) socket.close();
            if (communicationThread != null && communicationThread.isAlive()) communicationThread.cancel();

            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(macAddress);
            UUID sppUuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            socket = device.createRfcommSocketToServiceRecord(sppUuid);

            Log.d(TAG, "Attempting to connect socket...");
            socket.connect();
            Log.d(TAG, "Socket connected. Performing sanity check...");

            Handler serviceHandler = new Handler(Looper.getMainLooper()) {
                @Override
                public void handleMessage(Message msg) {
                    if (activityHandler != null) {
                        activityHandler.sendMessage(Message.obtain(msg));
                    }
                }
            };

            communicationThread = new CommunicationThread(socket, serviceHandler);

            // The service now performs the sanity check using the thread
            String initialResponse = communicationThread.performSanityCheck();

            if (initialResponse.contains("4100")) {
                Log.d(TAG, "Sanity check PASSED. Broadcasting success.");
                LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(ACTION_CONNECTION_SUCCESS)); // MODIFIED
                // If successful, we now start the thread's main polling loop
                communicationThread.start();
            } else {
                Log.e(TAG, "Sanity check FAILED. Broadcasting failure.");
                sendFailureBroadcast("This does not appear to be a valid OBD-II adapter.");
                // Add a delay before stopping the service
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    Log.d(TAG, "Stopping service after sanity check failure (delayed).");
                    stopSelf();
                }, 2000); // 100ms delay
            }
        } catch (Exception e) {
            // Log 1: Entry into catch block
            Log.e(TAG, "CatchBlock: Entered. Exception: " + e.toString());
            // Log 2: About to call sendFailureBroadcast
            Log.d(TAG, "CatchBlock: About to call sendFailureBroadcast.");

            sendFailureBroadcast("Could not connect. Is the device on and in range?");

            // Log 3: Returned from sendFailureBroadcast
            Log.d(TAG, "CatchBlock: Returned from sendFailureBroadcast.");

            // Add a delay before stopping the service
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                Log.d(TAG, "Stopping service after connection exception (delayed).");
                stopSelf();
            }, 2000);
        }

    }

    private void sendFailureBroadcast(String message) {
        Intent intent = new Intent(ACTION_CONNECTION_FAILURE);
        intent.putExtra(EXTRA_FAILURE_MESSAGE, message);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent); // MODIFIED
        Log.d(TAG, "sendFailureBroadcast (Local): Broadcast sent for action: " + ACTION_CONNECTION_FAILURE + ", message: " + message); // MODIFIED Log
    }

    public void registerClient(Handler handler) { this.activityHandler = handler; }
    public void unregisterClient() { this.activityHandler = null; }
    public void setCommunicationMode(int mode) {
        if (communicationThread != null && communicationThread.isAlive()) {
            communicationThread.setMode(mode);
        }
    }

    public void clearDtcCodes() {
        if (communicationThread != null && communicationThread.isAlive()) {
            communicationThread.setMode(CommunicationThread.MODE_DTC_CLEAR);
        }
    }

    // --- NEW METHOD to handle the request from PidResultsActivity ---
    public void startCustomScan(ArrayList<PID> pids) {
        if (communicationThread != null && communicationThread.isAlive()) {
            communicationThread.startCustomScan(pids);
        }
    }

    private void stopService() {
        if (communicationThread != null) {
            communicationThread.cancel();
            communicationThread = null;
        }
        if (socket != null) {
            try { socket.close(); } catch (IOException e) { /* ignore */ }
        }
        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopService();
        Log.d(TAG, "ObdService destroyed.");
    }
}