package com.example.zavobd;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "ZavOBD_Main"; // A tag for logging

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

        // Initialize the permission launcher.
        // It defines what happens after the user responds to the permission dialog.
        requestMultiplePermissionsLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                permissions -> {
                    if (Boolean.TRUE.equals(permissions.get(Manifest.permission.BLUETOOTH_CONNECT))
                            && Boolean.TRUE.equals(permissions.get(Manifest.permission.BLUETOOTH_SCAN))) {
                        // Both permissions were granted. We can now proceed.
                        Toast.makeText(this, "Permissions granted! You can now scan.", Toast.LENGTH_SHORT).show();
                        listPairedDevices(); // Try listing devices again now that we have permission
                    } else {
                        // One or more permissions were denied.
                        Toast.makeText(this, "Bluetooth permissions were denied. Cannot proceed.", Toast.LENGTH_LONG).show();
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

        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // MODIFIED: We now call a new method that handles checking first.
                checkAndRequestPermissions();
            }
        });
        devicesListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                DeviceItem selectedDevice = deviceList.get(position);
                String deviceAddress = selectedDevice.getDeviceAddress();

                Log.d(TAG, "User selected device: " + selectedDevice.getDeviceName() + " with address: " + deviceAddress);
                Toast.makeText(MainActivity.this, "Connecting to " + selectedDevice.getDeviceName(), Toast.LENGTH_SHORT).show();

                connectToDevice(deviceAddress);
            }
        });
    }
    private void connectToDevice(String macAddress) {
        // IMPORTANT: Bluetooth connection must be done in a background thread.
        new Thread(() -> {
            // Add a check for the BLUETOOTH_CONNECT permission before trying to connect
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "BLUETOOTH_CONNECT permission not granted", Toast.LENGTH_SHORT).show());
                return;
            }

            try {
                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(macAddress);
                UUID sppUuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
                BluetoothSocket socket = device.createRfcommSocketToServiceRecord(sppUuid);

                Log.d(TAG, "Attempting to connect to socket...");
                socket.connect();
                Log.d(TAG, "Connection successful!");

                // Instead of closing the socket, we store it in our manager
                BluetoothConnectionManager.getInstance().setSocket(socket);

                // Now, start the DashboardActivity
                runOnUiThread(() -> {
                    Intent intent = new Intent(MainActivity.this, DashboardActivity.class);
                    startActivity(intent);
                });

            } catch (IOException e) {
                Log.e(TAG, "Connection failed: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Connection Failed", Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    //A new method to centralize the permission logic.
    private void checkAndRequestPermissions() {
        // We only need to ask for permissions on Android 12 (API 31) and above.
        // On older versions, the permissions from the Manifest are enough.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                // If we don't have permission, launch the dialog.
                requestMultiplePermissionsLauncher.launch(new String[]{
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                });
            } else {
                // We already have permission, so we can list the devices.
                listPairedDevices();
            }
        } else {
            // For older Android versions, no runtime permission is needed.
            listPairedDevices();
        }
    }


    private void listPairedDevices() {
        deviceList.clear();

        //We add one final check here, but the main check is now in checkAndRequestPermissions()
        // This is mainly for safety.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                // This toast should not appear now, because we ask for permission first.
                Toast.makeText(this, "Bluetooth Connect permission not granted!", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                String deviceName = device.getName();
                String deviceHardwareAddress = device.getAddress();
                deviceList.add(new DeviceItem(deviceName, deviceHardwareAddress));
            }
            deviceListAdapter.notifyDataSetChanged();
        } else {
            Toast.makeText(this, "No paired devices found.", Toast.LENGTH_LONG).show();
        }
    }
}