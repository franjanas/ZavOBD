package com.example.zavobd.obd;

public class CoolantCommand extends AbstractObdCommand {

    public CoolantCommand() {
        super("0105", "Engine Coolant Temperature", "°C");
    }

    @Override
    protected void performCalculations() {
        // A response for temp (0105) is "4105XX" where XX is one byte
        if (rawResponse != null && rawResponse.startsWith("4105")) {
            try {
                String hexValue = rawResponse.substring(4, 6);
                // The formula is: Hex to Decimal, then subtract 40
                this.value = Integer.parseInt(hexValue, 16) - 40;
            } catch (Exception e) {
                this.value = 0;
            }
        } else {
            this.value = 0;
        }
    }
}