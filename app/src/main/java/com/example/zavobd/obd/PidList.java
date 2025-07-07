package com.example.zavobd.obd;

import com.example.zavobd.PID;
import java.util.ArrayList;

public class PidList {

    public static ArrayList<PID> getSupportedPids() {
        ArrayList<PID> pids = new ArrayList<>();

        // --- Core Engine Data ---
        pids.add(new PID("0104", "Engine Load"));
        pids.add(new PID("0105", "Engine Coolant Temperature"));
        pids.add(new PID("010C", "Engine RPM"));
        pids.add(new PID("010D", "Vehicle Speed"));
        pids.add(new PID("0111", "Throttle Position"));
        pids.add(new PID("011F", "Run time since engine start"));
        pids.add(new PID("0146", "Ambient Air Temperature"));
        pids.add(new PID("015C", "Engine Oil Temperature"));
        pids.add(new PID("0167", "Engine Coolant Temperature at thermostat")); // May not be widely supported
        pids.add(new PID("01A6", "Odometer")); // May not be widely supported

        // --- Fuel System ---
        pids.add(new PID("010A", "Fuel Pressure"));
        pids.add(new PID("012F", "Fuel Level Input"));
        pids.add(new PID("015E", "Engine Fuel Rate"));
        pids.add(new PID("0123", "Fuel Rail Pressure"));
        pids.add(new PID("0159", "Fuel Rail Absolute Pressure"));

        // --- Air Intake / Exhaust ---
        pids.add(new PID("010F", "Intake Air Temperature"));
        pids.add(new PID("0110", "MAF Air Flow Rate"));
        pids.add(new PID("010B", "Intake Manifold Absolute Pressure"));
        pids.add(new PID("0133", "Barometric Pressure"));
        pids.add(new PID("0142", "Catalyst Temperature (Bank 1, Sensor 1)"));
        pids.add(new PID("0143", "Catalyst Temperature (Bank 2, Sensor 1)"));

        // --- Oxygen Sensors (crucial for diagnostics) ---
        pids.add(new PID("0114", "O2 Sensor 1, Bank 1 (Voltage)"));
        pids.add(new PID("0115", "O2 Sensor 2, Bank 1 (Voltage)"));
        pids.add(new PID("0116", "O2 Sensor 3, Bank 1 (Voltage)"));
        pids.add(new PID("0117", "O2 Sensor 4, Bank 1 (Voltage)"));
        pids.add(new PID("0118", "O2 Sensor 5, Bank 2 (Voltage)"));
        pids.add(new PID("0119", "O2 Sensor 6, Bank 2 (Voltage)"));
        pids.add(new PID("011A", "O2 Sensor 7, Bank 2 (Voltage)"));
        pids.add(new PID("011B", "O2 Sensor 8, Bank 2 (Voltage)"));

        // --- Emissions & EVAP System ---
        pids.add(new PID("012C", "Commanded EGR"));
        pids.add(new PID("012D", "EGR Error"));
        pids.add(new PID("012E", "Commanded Evaporative Purge"));
        pids.add(new PID("0131", "Distance traveled with MIL on"));

        // --- Electrical ---
        pids.add(new PID("0121", "Distance traveled since codes cleared"));
        pids.add(new PID("014D", "Control Module Voltage"));
        pids.add(new PID("015B", "Hybrid/EV Battery Remaining Life"));

        // --- Timing & Status ---
        pids.add(new PID("0103", "Fuel System Status"));
        pids.add(new PID("0106", "Short Term Fuel Trim—Bank 1"));
        pids.add(new PID("0107", "Long Term Fuel Trim—Bank 1"));
        pids.add(new PID("0108", "Short Term Fuel Trim—Bank 2"));
        pids.add(new PID("0109", "Long Term Fuel Trim—Bank 2"));
        pids.add(new PID("011C", "OBD standards this vehicle conforms to"));
        pids.add(new PID("011D", "Oxygen sensors present"));

        return pids;
    }
}