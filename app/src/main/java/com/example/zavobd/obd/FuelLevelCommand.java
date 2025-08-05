package com.example.zavobd.obd;

// import android.util.Log;

import android.util.Log;

public class FuelLevelCommand extends AbstractObdCommand {

    public FuelLevelCommand() {
        super("012F", "Fuel Level", "%");
    }

    @Override
    protected void performCalculations() {
        // The response for fuel level (012F) might be "7E8...412FXX..." or "412FXX"
        // where XX is one byte (A)
        if (rawResponse != null) {
            String cleanedResponse = rawResponse.replaceAll("\\s", ""); // Remove spaces
            int indexOf412F = cleanedResponse.indexOf("412F");

            if (indexOf412F != -1 && cleanedResponse.length() >= indexOf412F + 6) { // Ensure there are 2 hex chars (1 byte) after "412F"
                try {
                    // Get the hex value (the two characters after "412F")
                    String hexValue = cleanedResponse.substring(indexOf412F + 4, indexOf412F + 6);
                    int a = Integer.parseInt(hexValue, 16);
                    // The formula is (A * 100) / 255
                    this.value = (a * 100) / 255;
                } catch (Exception e) {
                    this.value = 0;
                    Log.e("FuelLevelCommand", "Error parsing fuel level: " + cleanedResponse, e);
                }
            } else {
                this.value = 0;
                Log.w("FuelLevelCommand", "Unexpected response format: " + cleanedResponse);
            }
        } else {
            this.value = 0;
        }
    }
}