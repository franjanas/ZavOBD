package com.example.zavobd;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

public class DtcActivity extends AppCompatActivity {
    private TextView tvStatus;
    private ListView lvDtc;
    private Button btnClearCodes, btnRefreshScan;

    private ArrayAdapter<String> dtcListAdapter;
    private ArrayList<String> dtcList = new ArrayList<>();

    private ObdService obdService;
    private boolean isServiceBound = false;

    private final Handler uiHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == CommunicationThread.MSG_UPDATE_DTC_RESULT) {
                Log.d("DtcActivity", "uiHandler received MSG_UPDATE_DTC_RESULT");
                // When we receive the results, update the UI
                tvStatus.setText("Scan complete.");
                Bundle bundle = (Bundle) msg.obj;
                List<String> codes = bundle.getStringArrayList("dtcCodes");
                Log.i("DtcActivity", "Received DTC codes in Activity: " + (codes != null ? codes.toString() : "null list"));
                dtcList.clear();
                if (codes == null || codes.isEmpty()) {
                    Log.d("DtcActivity", "No DTC codes to display or list was empty/null.");
                    tvStatus.append(" No trouble codes found.");
                } else {
                    tvStatus.append(" " + codes.size() + " code(s) found:");
                    dtcList.addAll(codes);
                    Log.d("DtcActivity", "Displaying codes: " + dtcList.toString());
                }
                dtcListAdapter.notifyDataSetChanged();
            }
        }
    };

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            ObdService.ObdServiceBinder binder = (ObdService.ObdServiceBinder) service;
            obdService = binder.getService();
            isServiceBound = true;
            obdService.registerClient(uiHandler);
            // After connecting, immediately trigger the refresh button's logic
            btnRefreshScan.performClick();
        }
        @Override
        public void onServiceDisconnected(ComponentName arg0) { isServiceBound = false; }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dtc);

        tvStatus = findViewById(R.id.tv_dtc_status);
        lvDtc = findViewById(R.id.list_view_dtc);
        btnClearCodes = findViewById(R.id.btn_clear_codes);
        btnRefreshScan = findViewById(R.id.btn_refresh_scan);

        dtcListAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, dtcList);
        lvDtc.setAdapter(dtcListAdapter);

        btnRefreshScan.setOnClickListener(v -> {
            if (isServiceBound) {
                // When the button is clicked, clear the old results and tell the service to scan
                tvStatus.setText("Scanning for codes...");
                dtcList.clear();
                dtcListAdapter.notifyDataSetChanged();
                obdService.setCommunicationMode(CommunicationThread.MODE_DTC_SCAN);
            } else {
                Toast.makeText(this, "Service not connected.", Toast.LENGTH_SHORT).show();
            }
        });

        btnClearCodes.setOnClickListener(v -> {
            if (isServiceBound) {
                showClearCodesConfirmationDialog();
            } else {
                Toast.makeText(this, "Service not connected.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showClearCodesConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Clear Trouble Codes?")
                .setMessage("WARNING: This will turn off the Check Engine Light and erase important diagnostic data.")
                .setPositiveButton("Clear Codes", (dialog, which) -> {
                    if (isServiceBound) {
                        tvStatus.setText("Clearing codes and re-scanning...");
                        dtcList.clear();
                        dtcListAdapter.notifyDataSetChanged();
                        obdService.clearDtcCodes();
                    }
                })
                .setNegativeButton("Cancel", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, ObdService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isServiceBound) {
            obdService.setCommunicationMode(CommunicationThread.MODE_IDLE);
            obdService.unregisterClient();
            unbindService(serviceConnection);
            isServiceBound = false;
        }
    }
}