package com.example.zavobd.obd;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

// This is the template for all OBD commands.
public abstract class AbstractObdCommand {

    private static final String TAG = AbstractObdCommand.class.getSimpleName();

    protected String command = null;
    protected String name = "Unknown";
    protected String rawResponse = null;
    protected int value = 0;
    protected String unit = "";
    // private String cmd; // This field was previously used incorrectly and is unused now. Consider removing it.

    public AbstractObdCommand(String command, String name, String unit) {
        this.command = command;
        this.name = name;
        this.unit = unit;
    }

    // This is the main method that will be called from our communication thread.
    // It sends the command, reads the response, and performs the calculation.
    public void run(InputStream in, OutputStream out) throws IOException, InterruptedException {
        synchronized (AbstractObdCommand.class) { // Ensure only one command runs at a time.
            Log.d(TAG, "Command [" + this.command + "] run: Sending command.");
            sendCommand(out);
            Log.d(TAG, "Command [" + this.command + "] run: Reading result.");
            readResult(in);
            // After readResult, rawResponse is populated (or should be)
            // Now call the abstract method for calculations
            performCalculations(); // Make sure this is called before logging the finished state
            Log.d(TAG, "Command [" + this.command + "] run: Finished. Raw response: '" + rawResponse + "', Calculated value: " + value);
        }
    }

    protected void sendCommand(OutputStream out) throws IOException, InterruptedException {
        // Add the carriage return char
        String commandString = this.command + "\r";
        Log.d(TAG, "Sending command string: '" + commandString.replace("\r", "<CR>") + "'");
        out.write(commandString.getBytes());
        out.flush();
        // Pause between sending and trying to read - ELM327 is slow
        // Adjust this delay based on your adapter's responsiveness
        Thread.sleep(100); // Consider making this configurable or dynamic
        Log.d(TAG, "Command sent and flushed.");
    }

    protected void readResult(InputStream in) throws IOException {
        Log.d(TAG, "readResult: Starting to read from InputStream...");
        StringBuilder res = new StringBuilder();
        char c;
        // End of response is marked by '>' character.
        // Make sure to handle potential timeouts if '>' is never received.
        long startTime = System.currentTimeMillis();
        long timeout = 5000; // 5 seconds timeout for reading response

        try {
            while ((System.currentTimeMillis() - startTime) < timeout) {
                Log.v(TAG, "readResult: Loop iteration. Time remaining: " + (timeout - (System.currentTimeMillis() - startTime)) + "ms. Checking available bytes...");
                int availableBytes = in.available(); // Check available bytes once per loop iteration
                Log.v(TAG, "readResult: in.available() = " + availableBytes);

                if (availableBytes > 0) {
                    c = (char) in.read();
                    Log.v(TAG, "readResult: Read char: '" + (c == '\r' ? "<CR>" : c) + "' (int: " + (int) c + ")");
                    if (c == '>') {
                        Log.d(TAG, "readResult: End character '>' found. Breaking read loop.");
                        break;
                    }
                    res.append(c);
                } else {
                    Log.v(TAG, "readResult: No data available. Sleeping for 20ms.");
                    // No data available, yield a bit to prevent busy-waiting
                    try {
                        Thread.sleep(20); // Short sleep
                    } catch (InterruptedException e) {
                        Log.w(TAG, "readResult: Sleep interrupted.", e);
                        Thread.currentThread().interrupt(); // Restore interrupt status
                        break; // Exit loop on interrupt
                    }
                }
            }

            if ((System.currentTimeMillis() - startTime) >= timeout && res.indexOf(">") == -1) {
                Log.w(TAG, "readResult: Timeout occurred before '>' was found. Partial response: '" + res.toString().replace("\r", "<CR>") + "'");
                // Set rawResponse to a known error or empty state if timeout occurs
                // This ensures performCalculations() doesn't operate on potentially partial/corrupt data
                // or data from a previous successful read.
                rawResponse = "TIMEOUT"; // Or some other indicator
                return; // Early exit if timeout with no end character
            }

        } catch (IOException e) {
            Log.e(TAG, "readResult: IOException during read.", e);
            // Similar to timeout, set rawResponse to an error state
            rawResponse = "IO_ERROR";
            throw e; // Re-throw the exception, or handle it by returning
        }

        // Clean up the response: remove echoes, prompts, and whitespace
        // Corrected: use replaceAll to remove all whitespace characters
        rawResponse = res.toString().replace("SEARCHING...", "").replaceAll("\\s", "").replace("\r", "").trim();
        
        // Further cleaning specific to some adapters might be needed, e.g., removing the command echo if present.
        // For example, if '03' is sent, the response might be '03\r\n4300\r\n>'. We want to remove '03'.
        if (this.command != null && !rawResponse.equals("TIMEOUT") && !rawResponse.equals("IO_ERROR") && rawResponse.startsWith(this.command)) { // Added null check for safety and ensure not TIMEOUT/IO_ERROR
            Log.d(TAG, "readResult: Raw response before stripping command echo: '" + rawResponse + "'");
            rawResponse = rawResponse.substring(this.command.length()).trim();
            Log.d(TAG, "readResult: Raw response after stripping command echo: '" + rawResponse + "'");
        }

        Log.i(TAG, "readResult: Final processed rawResponse: '" + rawResponse + "'");
    }



    // This method reads the raw data from the car's ECU.
    // This method seems unused given the current readResult implementation. Consider removing if confirmed.
    private String readRawData(InputStream in) throws IOException {
        StringBuilder responseBuffer = new StringBuilder();
        long startTime = System.currentTimeMillis();
        // Read character by character until we see the '>' prompt or time out.
        while ((System.currentTimeMillis() - startTime) < 2000) { // Note: This uses a fixed 2s timeout
            if (in.available() > 0) {
                char c = (char) in.read();
                if (c == '>') {
                    break;
                }
                responseBuffer.append(c);
            }
        }
        // Clean up the response string
        // Corrected: use replaceAll to remove all whitespace characters if this method were to be used
        return responseBuffer.toString().replaceAll("\\s", "").replace("\r", "").replace("\n", "").trim();
    }

    // Abstract method that each specific command MUST implement.
    // This is where the parsing logic (e.g., "410C" -> 750) will go.
    protected abstract void performCalculations();

    // Getter for the final, formatted result (e.g., "750 RPM")
    public String getFormattedResult() {
        // It's good practice to re-calculate or ensure calculations are fresh if state can change
        // However, with the current run() flow, performCalculations() is called before this usually.
        // If performCalculations sets value to 0 on error, this will reflect that.
        return value + " " + unit;
    }

    // Getter for the raw integer value.
    public int getResultValue() {
        return value;
    }
}
