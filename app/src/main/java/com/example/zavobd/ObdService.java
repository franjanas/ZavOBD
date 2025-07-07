package com.example.zavobd;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;

public class ObdService extends Service {
    private static final String TAG = "ObdService";

    // Public constants for Broadcasts
    public static final String ACTION_CONNECTION_SUCCESS = "com.example.zavobd.ACTION_CONNECTION_SUCCESS";
    public static final String ACTION_CONNECTION_FAILURE = "com.example.zavobd.ACTION_CONNECTION_FAILURE";
    public static final String EXTRA_FAILURE_MESSAGE = "EXTRA_FAILURE_MESSAGE";

    // Public constants for Intent Actions
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
    public IBinder onBind(Intent intent) { return binder; }

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

            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(macAddress);
            UUID sppUuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
            socket = device.createRfcommSocketToServiceRecord(sppUuid);

            socket.connect();

            Handler serviceHandler = new Handler(Looper.getMainLooper()) {
                @Override
                public void handleMessage(Message msg) {
                    if (activityHandler != null) {
                        activityHandler.sendMessage(Message.obtain(msg));
                    }
                }
            };

            communicationThread = new CommunicationThread(socket, serviceHandler);
            String initialResponse = communicationThread.performSanityCheck();

            if (initialResponse != null && initialResponse.contains("4100")) {
                sendSuccessBroadcast();
                communicationThread.start();
            } else {
                sendFailureBroadcast("This does not appear to be a valid OBD-II adapter.");
                stopSelf();
            }

        } catch (IOException | SecurityException e) {
            sendFailureBroadcast("Could not connect to the device. Please ensure it is on and in range.");
            stopSelf();
        }
    }

    // A separate method for success to keep things clean
    private void sendSuccessBroadcast() {
        Intent intent = new Intent(ACTION_CONNECTION_SUCCESS);
        // --- THIS IS THE FIX ---
        // We explicitly set the package to our own app's package.
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    private void sendFailureBroadcast(String message) {
        Intent intent = new Intent(ACTION_CONNECTION_FAILURE);
        intent.putExtra(EXTRA_FAILURE_MESSAGE, message);
        // --- THIS IS THE FIX ---
        // We explicitly set the package to our own app's package.
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    public void registerClient(Handler handler) { this.activityHandler = handler; }
    public void unregisterClient() { this.activityHandler = null; }
    public void setCommunicationMode(int mode) {
        if (communicationThread != null && communicationThread.isAlive()) {
            communicationThread.setMode(mode);
        }
    }

    public void startCustomScan(ArrayList<PID> pids) {
        if (communicationThread != null && communicationThread.isAlive()) {
            communicationThread.setCustomScanPids(pids);
            communicationThread.setMode(CommunicationThread.MODE_CUSTOM_SCAN);
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
    }
}