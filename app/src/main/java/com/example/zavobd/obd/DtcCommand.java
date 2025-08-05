package com.example.zavobd.obd;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class DtcCommand extends AbstractObdCommand {

    // We override the default value and unit, as this command returns a list.
    private final List<String> troubleCodes = new ArrayList<>();

    public DtcCommand() {
        super("03", "Diagnostic Trouble Codes", "");
    }

    @Override
    protected void performCalculations() {
        Log.i("DtcCommand", "performCalculations started. Raw response: '" + rawResponse + "'");
        // Clear any previous codes
        troubleCodes.clear();

        // The response for DTCs (03) is complex. Example: "4301330171"
        // "43" is the header. The rest are pairs of bytes representing codes.
        // "0133" and "0171" are two separate codes.
        if (rawResponse != null && rawResponse.startsWith("43")) {
            String codes = rawResponse.substring(2); // Remove the "43" header
            Log.i("DtcCommand", "Codes string after removing '43': '" + codes + "'");
            // Loop through the response string, taking 4 characters (2 bytes) at a time.
            for (int i = 0; i < codes.length(); i += 4) {
                if (i + 4 <= codes.length()) {
                    String hexCode = codes.substring(i, i + 4);
                    // Ignore the "0000" padding that some cars send
                    Log.d("DtcCommand", "Processing hexCode: '" + hexCode + "'");
                    if (!hexCode.equals("0000")) {
                        troubleCodes.add(formatDtc(hexCode));
                        Log.d("DtcCommand", "Added formatted DTC: '" + formatDtc(hexCode) + "'");
                    }
                }
            }
        } else {
            Log.w("DtcCommand", "rawResponse is null or does not start with '43'. rawResponse: '" + rawResponse + "'");
        }
        Log.i("DtcCommand", "performCalculations finished. Trouble codes list: " + troubleCodes.toString());
    }

    // This method formats a 4-char hex code (e.g., "0133") into a standard DTC (e.g., "P0133")
    private String formatDtc(String hexCode) {
        char firstChar = hexCode.charAt(0);
        char firstLetter;

        switch (firstChar) {
            case '0':
            case '1':
            case '2':
            case '3':
                firstLetter = 'P'; // Powertrain
                break;
            case '4':
            case '5':
            case '6':
            case '7':
                firstLetter = 'C'; // Chassis
                break;
            case '8':
            case '9':
            case 'A':
            case 'B':
                firstLetter = 'B'; // Body
                break;
            case 'C':
            case 'D':
            case 'E':
            case 'F':
                firstLetter = 'U'; // Network
                break;
            default:
                firstLetter = '?';
        }
        return firstLetter + hexCode;
    }

    // Custom getter to return the list of formatted codes
    public List<String> getFormattedCodes() {
        return troubleCodes;
    }

    // Override the default getFormattedResult to be more descriptive
    @Override
    public String getFormattedResult() {
        Log.i("DtcCommand", "getFormattedResult called. Current troubleCodes: " + troubleCodes.toString());
        if (troubleCodes.isEmpty()) {
            Log.i("DtcCommand", "Trouble codes list is empty. Returning 'No trouble codes found.'");
            return "No trouble codes found.";
        }
        String result = String.join(", ", troubleCodes);
        Log.i("DtcCommand", "Formatted result string: '" + result + "'"); // Assuming 'result' holds the String.join output
        return result;
    }
}