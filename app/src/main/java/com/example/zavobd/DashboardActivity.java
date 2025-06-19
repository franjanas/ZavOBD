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
import android.widget.TextView;
import android.widget.Toast;

import com.github.anastr.speedviewlib.AwesomeSpeedometer;
import com.github.anastr.speedviewlib.SpeedView;
import com.example.zavobd.obd.RpmCommand;
import com.example.zavobd.obd.SpeedCommand;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class DashboardActivity extends AppCompatActivity {
    private static final String TAG = "ZavOBD_Dashboard";

    // Handler Message Codes
    private static final int MSG_UPDATE_GAUGES = 1;
    private static final int MSG_CONNECTION_LOST = 2;
    private static final int MSG_INVALID_DEVICE = 3;
    private static final int MSG_SET_STATUS_TEXT = 4;

    private TextView tvConnectionStatus;
    private SpeedView speedView;
    private AwesomeSpeedometer rpmGauge;
    private CommunicationThread communicationThread;

    private final Handler uiHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_UPDATE_GAUGES:
                    Bundle bundle = (Bundle) msg.obj;
                    speedView.speedTo(bundle.getInt("speed"));
                    rpmGauge.speedTo(bundle.getInt("rpm") / 1000f);
                    break;
                case MSG_CONNECTION_LOST:
                    showErrorDialog("Connection Lost", "Communication with the device has been lost.");
                    break;
                case MSG_INVALID_DEVICE:
                    showErrorDialog("Invalid Device", "This does not appear to be a valid OBD-II adapter. Please connect to the correct device.");
                    break;
                case MSG_SET_STATUS_TEXT:
                    tvConnectionStatus.setText((String) msg.obj);
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        tvConnectionStatus = findViewById(R.id.tv_connection_status);
        speedView = findViewById(R.id.speed_view);
        rpmGauge = findViewById(R.id.rpm_gauge);

        BluetoothSocket socket = BluetoothConnectionManager.getInstance().getSocket();
        if (socket != null && socket.isConnected()) {
            communicationThread = new CommunicationThread(socket, uiHandler);
            communicationThread.start();
        } else {
            Toast.makeText(this, "Connection not established.", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void showErrorDialog(String title, String message) {
        if (!isFinishing()) {
            new AlertDialog.Builder(this)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton("Go to Connection Screen", (dialog, which) -> {
                        dialog.dismiss();
                        Intent intent = new Intent(DashboardActivity.this, MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    })
                    .setCancelable(false)
                    .show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (communicationThread != null) {
            communicationThread.cancel();
        }
    }

    private static class CommunicationThread extends Thread {
        private final BluetoothSocket socket;
        private final InputStream inputStream;
        private final OutputStream outputStream;
        private final Handler handler;

        public CommunicationThread(BluetoothSocket socket, Handler handler) {
            this.socket = socket;
            this.handler = handler;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { Log.e(TAG, "Error obtaining streams", e); }
            inputStream = tmpIn;
            outputStream = tmpOut;
        }

        private String executeSimpleCommand(String command) throws IOException {
            if(outputStream == null) return "";
            outputStream.write((command + "\r").getBytes());
            outputStream.flush();
            try { Thread.sleep(200); } catch (InterruptedException e) { /* ignore */ }
            if(inputStream == null) return "";
            StringBuilder response = new StringBuilder();
            while(inputStream.available() > 0) {
                response.append((char) inputStream.read());
            }
            return response.toString().replaceAll("\\s", "");
        }

        @Override
        public void run() {
            try {
                handler.sendMessage(Message.obtain(handler, MSG_SET_STATUS_TEXT, "Initializing..."));
                executeSimpleCommand("ATE0");
                executeSimpleCommand("ATSP6");
                String testResponse = executeSimpleCommand("0100");
                if (!testResponse.contains("4100")) {
                    Log.e(TAG, "Device failed sanity check. Not an OBD device.");
                    handler.sendEmptyMessage(MSG_INVALID_DEVICE);
                    return; // Stop the thread
                }

                handler.sendMessage(Message.obtain(handler, MSG_SET_STATUS_TEXT, "Status: Live Data"));

                final SpeedCommand speedCmd = new SpeedCommand();
                final RpmCommand rpmCmd = new RpmCommand();

                while (!Thread.currentThread().isInterrupted()) {
                    speedCmd.run(inputStream, outputStream);
                    rpmCmd.run(inputStream, outputStream);

                    Bundle bundle = new Bundle();
                    bundle.putInt("speed", speedCmd.getResultValue());
                    bundle.putInt("rpm", rpmCmd.getResultValue());

                    Message link = handler.obtainMessage(MSG_UPDATE_GAUGES, bundle);
                    handler.sendMessage(link);

                    Thread.sleep(500);
                }
            } catch (IOException | InterruptedException e) {
                Log.e(TAG, "Communication lost.", e);
                handler.sendEmptyMessage(MSG_CONNECTION_LOST);
            }
        }

        public void cancel() {
            try {
                interrupt();
                if (socket != null) {
                    socket.close();
                }
            } catch (IOException e) { Log.e(TAG, "Could not close the client socket", e); }
        }
    }
}