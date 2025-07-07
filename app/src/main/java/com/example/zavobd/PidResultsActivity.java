package com.example.zavobd;

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
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;

public class PidResultsActivity extends AppCompatActivity {

    private TextView tvStatus;
    private ListView lvResults;

    private ArrayList<PID> selectedPids;
    private ArrayList<PidResult> resultList;
    private PidResultAdapter resultAdapter;

    private ObdService obdService;
    private boolean isServiceBound = false;

    private final Handler uiHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == CommunicationThread.MSG_UPDATE_CUSTOM_RESULTS) {
                // When new data comes from the service, update our list
                ArrayList<String> values = msg.getData().getStringArrayList("customResults");
                if (values != null && values.size() == resultList.size()) {
                    for (int i = 0; i < values.size(); i++) {
                        resultList.get(i).setValue(values.get(i));
                    }
                    resultAdapter.notifyDataSetChanged();
                }
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

            // Now that we are bound, tell the service to run our custom scan
            tvStatus.setText("Live Data from " + selectedPids.size() + " parameters:");
            obdService.startCustomScan(selectedPids);
        }
        @Override
        public void onServiceDisconnected(ComponentName arg0) { isServiceBound = false; }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pid_results);

        tvStatus = findViewById(R.id.tv_results_status);
        lvResults = findViewById(R.id.list_view_pid_results);

        // Get the list of PIDs the user selected from the previous screen
        selectedPids = (ArrayList<PID>) getIntent().getSerializableExtra("SELECTED_PIDS");
        if (selectedPids == null || selectedPids.isEmpty()) {
            Toast.makeText(this, "No parameters selected.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize our results list and adapter
        resultList = new ArrayList<>();
        for (PID pid : selectedPids) {
            resultList.add(new PidResult(pid.getDescription(), "Reading..."));
        }
        resultAdapter = new PidResultAdapter(this, resultList);
        lvResults.setAdapter(resultAdapter);
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