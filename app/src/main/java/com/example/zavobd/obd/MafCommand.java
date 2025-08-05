package com.example.zavobd.obd;

import android.util.Log;

public class MafCommand extends AbstractObdCommand {

    private static final String TAG = "MafCommand";

    public MafCommand() {
        super("0110", "Mass Air Flow", "g/s");
    }

    @Override
    protected void performCalculations() {
        // The response for MAF (0110) might be "7E8...4110XXYY..." or "4110XXYY"
        // where XXYY are two bytes (A and B)
        // Formula is ((A * 256) + B) / 100
        if (rawResponse != null) {
            String cleanedResponse = rawResponse.replaceAll("\\s", ""); // Should have been done in Abstract, but good practice
            int indexOf4110 = cleanedResponse.indexOf("4110");

            if (indexOf4110 != -1 && cleanedResponse.length() >= indexOf4110 + 8) { // Ensure there are 4 hex chars (2 bytes) after "4110"
                try {
                    // Get the hex value (the four characters after "4110")
                    String hexValue = cleanedResponse.substring(indexOf4110 + 4, indexOf4110 + 8);
                    Log.d(TAG, "MAF hex A: " + hexValue.substring(0, 2) + ", hex B: " + hexValue.substring(2, 4));
                    int a = Integer.parseInt(hexValue.substring(0, 2), 16);
                    int b = Integer.parseInt(hexValue.substring(2, 4), 16);
                    
                    // Calculate MAF in g/s
                    double mafInGramsPerSecond = ((a * 256.0) + b) / 100.0;
                    
                    // Store as int, scaled by 100 for two decimal places of precision
                    this.value = (int) (mafInGramsPerSecond * 100.0);
                    
                    Log.d(TAG, "Calculated MAF: " + mafInGramsPerSecond + " g/s, Stored value: " + this.value);

                } catch (NumberFormatException e) {
                    this.value = 0;
                    Log.e(TAG, "Error parsing MAF from hex: '" + cleanedResponse.substring(indexOf4110 + 4, indexOf4110 + 8) + "' in response: " + cleanedResponse, e);
                } catch (Exception e) {
                    this.value = 0;
                    Log.e(TAG, "Generic error parsing MAF: " + cleanedResponse, e);
                }
            } else {
                this.value = 0;
                if (indexOf4110 == -1) {
                    // Log.w(TAG, "Expected '4110' not found in response: " + cleanedResponse);
                } else {
                    // Log.w(TAG, "Response too short after '4110': " + cleanedResponse);
                }
            }
        } else {
            this.value = 0;
            // Log.w(TAG, "Raw response was null for MAF command.");
        }
    }

    // Custom getter to return the value as a double in g/s
    public double getMaf() {
        return this.value / 100.0;
    }
}
