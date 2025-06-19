package com.example.zavobd.obd;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

// This is the template for all OBD commands.
public abstract class AbstractObdCommand {

    protected String command = null;
    protected String name = "Unknown";
    protected String rawResponse = null;
    protected int value = 0;
    protected String unit = "";

    public AbstractObdCommand(String command, String name, String unit) {
        this.command = command;
        this.name = name;
        this.unit = unit;
    }

    // This is the main method that will be called from our communication thread.
    // It sends the command, reads the response, and performs the calculation.
    public void run(InputStream in, OutputStream out) throws IOException, InterruptedException {
        // Send the command
        out.write((command + "\r").getBytes());
        out.flush();

        // Read the response
        this.rawResponse = readRawData(in);

        // Perform the specific calculation for this command
        performCalculations();
    }

    // This method reads the raw data from the car's ECU.
    private String readRawData(InputStream in) throws IOException {
        StringBuilder responseBuffer = new StringBuilder();
        long startTime = System.currentTimeMillis();
        // Read character by character until we see the '>' prompt or time out.
        while ((System.currentTimeMillis() - startTime) < 2000) {
            if (in.available() > 0) {
                char c = (char) in.read();
                if (c == '>') {
                    break;
                }
                responseBuffer.append(c);
            }
        }
        // Clean up the response string
        return responseBuffer.toString().replaceAll("\\s", "").replaceAll("\r", "").replaceAll("\n", "");
    }

    // Abstract method that each specific command MUST implement.
    // This is where the parsing logic (e.g., "410C" -> 750) will go.
    protected abstract void performCalculations();

    // Getter for the final, formatted result (e.g., "750 RPM")
    public String getFormattedResult() {
        return value + " " + unit;
    }

    // Getter for the raw integer value.
    public int getResultValue() {
        return value;
    }
}