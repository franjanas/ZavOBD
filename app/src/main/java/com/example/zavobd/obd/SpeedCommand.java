package com.example.zavobd.obd;

// It's good practice to import Log if you're using it, e.g.:
// import android.util.Log;

import android.util.Log;

public class SpeedCommand extends AbstractObdCommand {

    public SpeedCommand() {
        // Call the constructor of the parent class with the PID, name, and unit
        super("010D", "Vehicle Speed", "km/h");
    }

    @Override
    protected void performCalculations() {
        // The raw response might be something like "7E803410DXX" or "410DXX"
        // where XX is one byte for speed.
        if (rawResponse != null) {
            String cleanedResponse = rawResponse.replaceAll("\\s", ""); // Remove spaces
            int indexOf410D = cleanedResponse.indexOf("410D");

            if (indexOf410D != -1 && cleanedResponse.length() >= indexOf410D + 6) {
                try {
                    // Get the hex part of the response (the two characters after "410D")
                    String hexValue = cleanedResponse.substring(indexOf410D + 4, indexOf410D + 6);
                    // Convert hex to decimal, which is the speed in km/h
                    this.value = Integer.parseInt(hexValue, 16);
                } catch (Exception e) {
                    // Handle potential errors during parsing
                    this.value = 0;
                    // It's good practice to log the error
                    Log.e("SpeedCommand", "Error parsing speed: " + cleanedResponse, e);
                }
            } else {
                this.value = 0;
                // Log if the expected pattern isn't found
                Log.w("SpeedCommand", "Unexpected response format: " + cleanedResponse);
            }
        } else {
            this.value = 0;
        }
    }
}