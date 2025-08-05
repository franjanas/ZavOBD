package com.example.zavobd.obd;

import android.util.Log;

public class RpmCommand extends AbstractObdCommand {

    public RpmCommand() {
        super("010C", "Engine RPM", "RPM");
    }

    @Override
    protected void performCalculations() {
        // A response for RPM (010C) might be "7E804410CXXXX" or "410CXXXX"
        // where XXXX is two bytes (A and B)
        if (rawResponse != null) {
            String cleanedResponse = rawResponse.replaceAll("\\s", ""); // Remove spaces
            int indexOf410C = cleanedResponse.indexOf("410C");

            if (indexOf410C != -1 && cleanedResponse.length() >= indexOf410C + 8) { // Ensure there are 4 hex chars (2 bytes) after "410C"
                try {
                    // Get the two-byte hex value (the four characters after "410C")
                    String hexValue = cleanedResponse.substring(indexOf410C + 4, indexOf410C + 8);
                    int a = Integer.parseInt(hexValue.substring(0, 2), 16);
                    int b = Integer.parseInt(hexValue.substring(2, 4), 16);
                    // The formula is ((A * 256) + B) / 4
                    this.value = ((a * 256) + b) / 4;
                } catch (Exception e) {
                    this.value = 0;
                    Log.e("RpmCommand", "Error parsing RPM: " + cleanedResponse, e);
                }
            } else {
                this.value = 0;
                Log.w("RpmCommand", "Unexpected response format: " + cleanedResponse);
            }
        } else {
            this.value = 0;
        }
    }
}