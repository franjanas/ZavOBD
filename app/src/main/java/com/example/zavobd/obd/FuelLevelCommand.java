package com.example.zavobd.obd;

public class FuelLevelCommand extends AbstractObdCommand {

    public FuelLevelCommand() {
        super("012F", "Fuel Level", "%");
    }

    @Override
    protected void performCalculations() {
        // The response for Fuel Level (012F) is "412FXX" where XX is one byte
        if (rawResponse != null && rawResponse.startsWith("412F")) {
            try {
                // The formula is: (100 * A) / 255
                // Where A is the decimal value of the hex byte.
                String hexValue = rawResponse.substring(4, 6);
                int a = Integer.parseInt(hexValue, 16);
                this.value = (100 * a) / 255;
            } catch (Exception e) {
                this.value = 0;
            }
        } else {
            this.value = 0;
        }
    }
}