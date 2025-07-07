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
    private Button btnClearCodes;

    private ArrayAdapter<String> dtcListAdapter;
    private ArrayList<String> dtcList = new ArrayList<>();

    private ObdService obdService;
    private boolean isServiceBound = false;

    private final Handler uiHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case CommunicationThread.MSG_UPDATE_DTC_RESULT:
                    Bundle bundle = (Bundle) msg.obj;
                    List<String> codes = bundle.getStringArrayList("dtcCodes");
                    dtcList.clear();
                    if (codes == null || codes.isEmpty()) {
                        tvStatus.setText("No stored trouble codes found.");
                    } else {
                        tvStatus.setText(codes.size() + " code(s) found:");
                        dtcList.addAll(codes);
                    }
                    dtcListAdapter.notifyDataSetChanged();
                    break;
                case CommunicationThread.MSG_CONNECTION_LOST:
                    showErrorDialog("Connection Lost", "Communication with the device has been lost.");
                    break;
                case CommunicationThread.MSG_INVALID_DEVICE:
                    showErrorDialog("Invalid Device", "This is not a valid OBD-II adapter.");
                    break;
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
            // Once connected, immediately request a DTC scan
            tvStatus.setText("Scanning for codes...");
            obdService.setCommunicationMode(CommunicationThread.MODE_DTC_SCAN);
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
        dtcListAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, dtcList);
        lvDtc.setAdapter(dtcListAdapter);

        // Note: The clear codes logic will need to be implemented in a future step
        btnClearCodes.setOnClickListener(v -> {
            Toast.makeText(this, "Clear codes feature not implemented yet.", Toast.LENGTH_SHORT).show();
        });
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
            if (obdService != null) {
                // Set the service back to idle mode so it's not wasting resources
                obdService.setCommunicationMode(CommunicationThread.MODE_IDLE);
                obdService.unregisterClient();
            }
            unbindService(serviceConnection);
            isServiceBound = false;
        }
    }

    private void showErrorDialog(String title, String message) {
        if (!isFinishing()) {
            new AlertDialog.Builder(this)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton("Go to Connection Screen", (dialog, which) -> {
                        dialog.dismiss();
                        Intent intent = new Intent(DtcActivity.this, MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    })
                    .setCancelable(false)
                    .show();
        }
    }
}