package com.example.zavobd;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.Toast;
import com.example.zavobd.obd.PidList;
import java.util.ArrayList;

public class PidSelectionActivity extends AppCompatActivity {

    private ListView pidListView;
    private CheckBox selectAllCheckbox;
    private Button runScanButton;

    private ArrayList<PID> supportedPids;
    private PidAdapter pidAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pid_selection);

        pidListView = findViewById(R.id.list_view_pids);
        selectAllCheckbox = findViewById(R.id.checkbox_select_all);
        runScanButton = findViewById(R.id.btn_run_scan);

        supportedPids = PidList.getSupportedPids();
        pidAdapter = new PidAdapter(this, supportedPids);
        pidListView.setAdapter(pidAdapter);

        pidListView.setOnItemClickListener((parent, view, position, id) -> {
            PID selectedPid = supportedPids.get(position);
            selectedPid.setSelected(!selectedPid.isSelected());
            pidAdapter.notifyDataSetChanged();
        });

        selectAllCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            for (PID pid : supportedPids) {
                pid.setSelected(isChecked);
            }
            pidAdapter.notifyDataSetChanged();
        });

        runScanButton.setOnClickListener(v -> {
            ArrayList<PID> selectedPids = new ArrayList<>();
            for (PID pid : supportedPids) {
                if (pid.isSelected()) {
                    selectedPids.add(pid);
                }
            }

            if (selectedPids.isEmpty()) {
                Toast.makeText(this, "Please select at least one parameter to scan.", Toast.LENGTH_SHORT).show();
            } else {
                Intent intent = new Intent(this, PidResultsActivity.class);
                intent.putExtra("SELECTED_PIDS", selectedPids);
                startActivity(intent);
            }
        });
    }
}