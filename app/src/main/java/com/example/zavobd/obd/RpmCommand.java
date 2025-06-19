package com.example.zavobd.obd;

public class RpmCommand extends AbstractObdCommand {

    public RpmCommand() {
        super("010C", "Engine RPM", "RPM");
    }

    @Override
    protected void performCalculations() {
        // A response for RPM (010C) is "410CXXXX" where XXXX is two bytes (A and B)
        if (rawResponse != null && rawResponse.startsWith("410C")) {
            try {
                // Get the two-byte hex value
                String hexValue = rawResponse.substring(4, 8);
                int a = Integer.parseInt(hexValue.substring(0, 2), 16);
                int b = Integer.parseInt(hexValue.substring(2, 4), 16);
                // The formula is ((A * 256) + B) / 4
                this.value = ((a * 256) + b) / 4;
            } catch (Exception e) {
                this.value = 0;
            }
        } else {
            this.value = 0;
        }
    }
}