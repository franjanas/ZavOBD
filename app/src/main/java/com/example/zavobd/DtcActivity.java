package com.example.zavobd;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.zavobd.obd.DtcCommand;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class DtcActivity extends AppCompatActivity {

    private static final String TAG = "ZavOBD_DTC";

    // Handler Message Codes
    private static final int MSG_UPDATE_STATUS = 1;
    private static final int MSG_SCAN_COMPLETE = 2;
    private static final int MSG_CONNECTION_FAILED = 3;
    private static final int MSG_CLEAR_SUCCESS = 4;

    private TextView tvStatus;
    private ListView lvDtc;
    private Button btnClearCodes;

    private ArrayAdapter<String> dtcListAdapter;
    private ArrayList<String> dtcList = new ArrayList<>();

    private final Handler uiHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_UPDATE_STATUS:
                    tvStatus.setText((String) msg.obj);
                    break;
                case MSG_SCAN_COMPLETE:
                    List<String> codes = (List<String>) msg.obj;
                    dtcList.clear();
                    if (codes.isEmpty()) {
                        tvStatus.setText("No stored trouble codes found.");
                    } else {
                        tvStatus.setText(codes.size() + " code(s) found:");
                        dtcList.addAll(codes);
                    }
                    dtcListAdapter.notifyDataSetChanged();
                    break;
                case MSG_CONNECTION_FAILED:
                    Toast.makeText(DtcActivity.this, "Error during communication.", Toast.LENGTH_LONG).show();
                    tvStatus.setText("Error during scan.");
                    break;
                case MSG_CLEAR_SUCCESS:
                    Toast.makeText(DtcActivity.this, "Codes cleared successfully.", Toast.LENGTH_LONG).show();
                    // The thread will automatically re-scan after a successful clear
                    break;
            }
        }
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

        BluetoothSocket socket = BluetoothConnectionManager.getInstance().getSocket();
        if (socket != null && socket.isConnected()) {
            new CommunicationThread(socket, uiHandler, "SCAN").start();
        } else {
            Toast.makeText(this, "Connection not established.", Toast.LENGTH_SHORT).show();
            finish();
        }

        btnClearCodes.setOnClickListener(v -> {
            showClearCodesConfirmationDialog();
        });
    }

    private void showClearCodesConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Clear Trouble Codes?")
                .setMessage("WARNING: This will turn off the Check Engine Light and erase important diagnostic data. This action cannot be undone.")
                .setPositiveButton("Clear Codes", (dialog, which) -> {
                    BluetoothSocket socket = BluetoothConnectionManager.getInstance().getSocket();
                    if (socket != null && socket.isConnected()) {
                        new CommunicationThread(socket, uiHandler, "CLEAR").start();
                    }
                })
                .setNegativeButton("Cancel", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private static class CommunicationThread extends Thread {
        private final BluetoothSocket socket;
        private final InputStream inputStream;
        private final OutputStream outputStream;
        private final Handler handler;
        private final String task;

        public CommunicationThread(BluetoothSocket socket, Handler handler, String task) {
            this.socket = socket;
            this.handler = handler;
            this.task = task;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { Log.e(TAG, "Error obtaining streams", e); }
            inputStream = tmpIn;
            outputStream = tmpOut;
        }

        @Override
        public void run() {
            try {
                if ("SCAN".equals(task)) {
                    handler.sendMessage(Message.obtain(handler, MSG_UPDATE_STATUS, "Scanning..."));
                    DtcCommand dtcCmd = new DtcCommand();
                    dtcCmd.run(inputStream, outputStream);
                    handler.sendMessage(Message.obtain(handler, MSG_SCAN_COMPLETE, dtcCmd.getFormattedCodes()));
                } else if ("CLEAR".equals(task)) {
                    handler.sendMessage(Message.obtain(handler, MSG_UPDATE_STATUS, "Clearing codes..."));
                    outputStream.write("04\r".getBytes());
                    outputStream.flush();
                    Thread.sleep(1000);
                    handler.sendEmptyMessage(MSG_CLEAR_SUCCESS);

                    // Automatically re-scan after clearing
                    handler.sendMessage(Message.obtain(handler, MSG_UPDATE_STATUS, "Re-scanning for codes..."));
                    DtcCommand dtcCmd = new DtcCommand();
                    dtcCmd.run(inputStream, outputStream);
                    handler.sendMessage(Message.obtain(handler, MSG_SCAN_COMPLETE, dtcCmd.getFormattedCodes()));
                }
            } catch (IOException | InterruptedException e) {
                Log.e(TAG, "Communication task failed", e);
                handler.sendEmptyMessage(MSG_CONNECTION_FAILED);
            }
        }
    }
}