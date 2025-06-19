package com.example.zavobd.obd;

public class SpeedCommand extends AbstractObdCommand {

    public SpeedCommand() {
        // Call the constructor of the parent class with the PID, name, and unit
        super("010D", "Vehicle Speed", "km/h");
    }

    @Override
    protected void performCalculations() {
        // The response for speed (010D) is "410DXX" where XX is one byte
        if (rawResponse != null && rawResponse.startsWith("410D")) {
            try {
                // Get the hex part of the response
                String hexValue = rawResponse.substring(4, 6);
                // Convert hex to decimal, which is the speed in km/h
                this.value = Integer.parseInt(hexValue, 16);
            } catch (Exception e) {
                // Handle potential errors during parsing
                this.value = 0;
            }
        } else {
            this.value = 0;
        }
    }
}