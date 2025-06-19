package com.example.zavobd.obd;

public class MafCommand extends AbstractObdCommand {

    public MafCommand() {
        super("0110", "Mass Air Flow", "g/s");
    }

    @Override
    protected void performCalculations() {
        // The response for MAF (0110) is "4110XXXX" where XXXX is two bytes (A and B)
        // Formula is ((A * 256) + B) / 100
        if (rawResponse != null && rawResponse.startsWith("4110")) {
            try {
                String hexValue = rawResponse.substring(4, 8);
                int a = Integer.parseInt(hexValue.substring(0, 2), 16);
                int b = Integer.parseInt(hexValue.substring(2, 4), 16);
                // We are storing this as a double, so we need to handle it.
                // For now, we'll cast to int, but this is a good place for future improvement.
                this.value = (int) ((((a * 256) + b) / 100.0) * 100); // Store as int * 100 for precision
            } catch (Exception e) {
                this.value = 0;
            }
        } else {
            this.value = 0;
        }
    }

    // Custom getter to return the value as a double
    public double getMaf() {
        return value / 100.0;
    }
}