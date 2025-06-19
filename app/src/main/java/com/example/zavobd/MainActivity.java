package com.example.zavobd;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "ZavOBD_Main";

    Button scanButton;
    ListView devicesListView;

    BluetoothAdapter bluetoothAdapter;
    ArrayList<DeviceItem> deviceList;
    ArrayAdapter<DeviceItem> deviceListAdapter;

    private ActivityResultLauncher<String[]> requestMultiplePermissionsLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        scanButton = findViewById(R.id.btn_scan_devices);
        devicesListView = findViewById(R.id.list_view_devices);

        // --- NEW: Start with the device list hidden ---
        devicesListView.setVisibility(View.GONE);
        scanButton.setText("Show Paired Devices");

        // Existing permission launcher logic...
        requestMultiplePermissionsLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                permissions -> {
                    if (Boolean.TRUE.equals(permissions.get(Manifest.permission.BLUETOOTH_CONNECT))
                            && Boolean.TRUE.equals(permissions.get(Manifest.permission.BLUETOOTH_SCAN))) {
                        listPairedDevices();
                    } else {
                        Toast.makeText(this, "Bluetooth permissions were denied.", Toast.LENGTH_LONG).show();
                    }
                });

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Device does not support Bluetooth", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        deviceList = new ArrayList<>();
        deviceListAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, deviceList);
        devicesListView.setAdapter(deviceListAdapter);

        // --- MODIFIED: Button now toggles visibility ---
        scanButton.setOnClickListener(v -> {
            if (devicesListView.getVisibility() == View.VISIBLE) {
                // If list is visible, hide it
                devicesListView.setVisibility(View.GONE);
                scanButton.setText("Show Paired Devices");
            } else {
                // If list is hidden, show it
                checkAndRequestPermissions(); // This will call listPairedDevices if permission is granted
            }
        });

        devicesListView.setOnItemClickListener((parent, view, position, id) -> {
            DeviceItem selectedDevice = deviceList.get(position);
            Toast.makeText(MainActivity.this, "Connecting to " + selectedDevice.getDeviceName(), Toast.LENGTH_SHORT).show();
            connectToDevice(selectedDevice.getDeviceAddress());
        });
    }

    private void connectToDevice(String macAddress) {
        new Thread(() -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return; // Permission check
            }
            try {
                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(macAddress);
                UUID sppUuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
                BluetoothSocket socket = device.createRfcommSocketToServiceRecord(sppUuid);

                Log.d(TAG, "Attempting to connect...");
                socket.connect();
                Log.d(TAG, "Connection successful!");

                BluetoothConnectionManager.getInstance().setSocket(socket);

                runOnUiThread(() -> {
                    Intent intent = new Intent(MainActivity.this, DashboardSelectionActivity.class);
                    startActivity(intent);
                });

            } catch (IOException e) {
                Log.e(TAG, "Connection failed: " + e.getMessage());
                // --- NEW: Show a "Try Again" dialog on failure ---
                runOnUiThread(() -> showConnectionFailedDialog());
            }
        }).start();
    }

    // --- NEW METHOD: Shows a user-friendly failure dialog ---
    private void showConnectionFailedDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Connection Failed")
                .setMessage("Could not connect to the device. Please ensure the device is on and in range. A device power-cycle (unplug/replug) may be required.")
                .setPositiveButton("Try Again", (dialog, which) -> {
                    // Just dismisses the dialog, allowing user to tap a device again
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                requestMultiplePermissionsLauncher.launch(new String[]{
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                });
            } else {
                listPairedDevices();
            }
        } else {
            listPairedDevices();
        }
    }

    private void listPairedDevices() {
        deviceList.clear();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return; // Permission check
        }
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                String deviceName = device.getName() == null ? "Unknown Device" : device.getName();
                deviceList.add(new DeviceItem(deviceName, device.getAddress()));
            }
            deviceListAdapter.notifyDataSetChanged();
            // --- NEW: After getting devices, make the list visible ---
            devicesListView.setVisibility(View.VISIBLE);
            scanButton.setText("Hide Paired Devices");
        } else {
            Toast.makeText(this, "No paired devices found.", Toast.LENGTH_LONG).show();
        }
    }
}