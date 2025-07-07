package com.example.zavobd;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private Button scanButton;
    private ListView devicesListView;
    private ProgressDialog progressDialog;

    private BluetoothAdapter bluetoothAdapter;
    private ArrayList<DeviceItem> deviceList;
    private ArrayAdapter<DeviceItem> deviceListAdapter;
    private ActivityResultLauncher<String[]> requestMultiplePermissionsLauncher;

    private final BroadcastReceiver connectionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (progressDialog != null && progressDialog.isShowing()) {
                progressDialog.dismiss();
            }

            String action = intent.getAction();
            if (ObdService.ACTION_CONNECTION_SUCCESS.equals(action)) {
                Toast.makeText(MainActivity.this, "Connection Established", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(MainActivity.this, DashboardSelectionActivity.class));
            } else if (ObdService.ACTION_CONNECTION_FAILURE.equals(action)) {
                String errorMessage = intent.getStringExtra(ObdService.EXTRA_FAILURE_MESSAGE);
                showErrorDialog(errorMessage);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // --- THIS IS THE FIX ---
        // These lines link the Java variables to the views in the XML layout.
        scanButton = findViewById(R.id.btn_scan_devices);
        devicesListView = findViewById(R.id.list_view_devices);
        // --- END OF FIX ---

        devicesListView.setVisibility(View.GONE);
        scanButton.setText("Show Paired Devices");

        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Connecting to device...");
        progressDialog.setCancelable(false);

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

        scanButton.setOnClickListener(v -> {
            if (devicesListView.getVisibility() == View.VISIBLE) {
                devicesListView.setVisibility(View.GONE);
                scanButton.setText("Show Paired Devices");
            } else {
                checkAndRequestPermissions();
            }
        });

        devicesListView.setOnItemClickListener((parent, view, position, id) -> {
            DeviceItem selectedDevice = deviceList.get(position);
            progressDialog.show();

            Intent serviceIntent = new Intent(this, ObdService.class);
            serviceIntent.putExtra(ObdService.EXTRA_DEVICE_ADDRESS, selectedDevice.getDeviceAddress());
            serviceIntent.setAction(ObdService.ACTION_CONNECT);
            startService(serviceIntent);
        });
    }

    // NEW, STABLE CODE
    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction(ObdService.ACTION_CONNECTION_SUCCESS);
        filter.addAction(ObdService.ACTION_CONNECTION_FAILURE);

        // --- THIS IS THE FIX ---
        // We add the required security flag as the third argument.
        // ContextCompat handles checking the Android version for us.
        ContextCompat.registerReceiver(this, connectionReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(connectionReceiver);
    }

    private void showErrorDialog(String message) {
        new AlertDialog.Builder(this)
                .setTitle("Connection Failed")
                .setMessage(message)
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .create()
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
            return;
        }
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                String deviceName = device.getName() == null ? "Unknown Device" : device.getName();
                deviceList.add(new DeviceItem(deviceName, device.getAddress()));
            }
            deviceListAdapter.notifyDataSetChanged();
            devicesListView.setVisibility(View.VISIBLE);
            scanButton.setText("Hide Paired Devices");
        } else {
            Toast.makeText(this, "No paired devices found.", Toast.LENGTH_LONG).show();
        }
    }
}