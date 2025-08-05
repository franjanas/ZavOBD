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
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.ArrayList;
import java.util.Set;

import com.example.zavobd.ObdService;

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
            android.util.Log.d("MainActivityReceiver", "onReceive triggered for action: " + intent.getAction());

            if (progressDialog != null) {
                if (progressDialog.isShowing()) {
                    android.util.Log.d("MainActivityReceiver", "ProgressDialog is showing, attempting to dismiss.");
                    progressDialog.dismiss();
                    android.util.Log.d("MainActivityReceiver", "ProgressDialog dismiss called. Is it still showing? " + progressDialog.isShowing());
                } else {
                    android.util.Log.d("MainActivityReceiver", "ProgressDialog was not showing.");
                }
            } else {
                android.util.Log.d("MainActivityReceiver", "ProgressDialog IS NULL at the start of onReceive.");
            }

            String action = intent.getAction();
            if (ObdService.ACTION_CONNECTION_SUCCESS.equals(action)) {
                android.util.Log.d("MainActivityReceiver", "Connection SUCCESS path.");
                Toast.makeText(MainActivity.this, "Connection Established", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(MainActivity.this, DashboardSelectionActivity.class));
            } else if (ObdService.ACTION_CONNECTION_FAILURE.equals(action)) {
                android.util.Log.d("MainActivityReceiver", "Connection FAILURE path.");
                String errorMessage = intent.getStringExtra(ObdService.EXTRA_FAILURE_MESSAGE);
                android.util.Log.d("MainActivityReceiver", "Error message: " + errorMessage);
                showErrorDialog(errorMessage); // This shows the AlertDialog
                // After calling showErrorDialog, check again if the progressDialog is somehow still around
                if (progressDialog != null) {
                    android.util.Log.d("MainActivityReceiver", "After showErrorDialog, is progressDialog showing? " + progressDialog.isShowing());
                }
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

        // ContextCompat.registerReceiver(this, connectionReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED); // OLD
        LocalBroadcastManager.getInstance(this).registerReceiver(connectionReceiver, filter); // NEW

        Log.d("MainActivityLifecycle", "Receiver REGISTERED in onResume (Local). Receiver: " + connectionReceiver.toString());
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d("MainActivityLifecycle", "Receiver UNREGISTERING in onPause (Local). Receiver: " + connectionReceiver.toString());
        // unregisterReceiver(connectionReceiver); // OLD
        LocalBroadcastManager.getInstance(this).unregisterReceiver(connectionReceiver); // NEW
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