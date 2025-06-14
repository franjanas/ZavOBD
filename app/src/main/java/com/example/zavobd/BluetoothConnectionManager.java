package com.example.zavobd;

import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.IOException;

// A Singleton class to manage the single Bluetooth socket connection
public class BluetoothConnectionManager {

    private static final String TAG = "BluetoothManager";
    private static BluetoothConnectionManager instance;
    private BluetoothSocket socket;

    // Private constructor to prevent instantiation from other classes
    private BluetoothConnectionManager() {}

    // The static method that controls access to the singleton instance
    public static synchronized BluetoothConnectionManager getInstance() {
        if (instance == null) {
            instance = new BluetoothConnectionManager();
        }
        return instance;
    }

    public void setSocket(BluetoothSocket socket) {
        this.socket = socket;
    }

    public BluetoothSocket getSocket() {
        return socket;
    }

    // A method to close the socket from anywhere
    public void closeConnection() {
        if (socket != null) {
            try {
                socket.close();
                socket = null; // Set to null after closing
                Log.d(TAG, "Bluetooth socket closed.");
            } catch (IOException e) {
                Log.e(TAG, "Error closing Bluetooth socket", e);
            }
        }
    }
}