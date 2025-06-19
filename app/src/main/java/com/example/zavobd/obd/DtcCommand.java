package com.example.zavobd.obd;

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
        // Clear any previous codes
        troubleCodes.clear();

        // The response for DTCs (03) is complex. Example: "4301330171"
        // "43" is the header. The rest are pairs of bytes representing codes.
        // "0133" and "0171" are two separate codes.
        if (rawResponse != null && rawResponse.startsWith("43")) {
            String codes = rawResponse.substring(2); // Remove the "43" header
            // Loop through the response string, taking 4 characters (2 bytes) at a time.
            for (int i = 0; i < codes.length(); i += 4) {
                if (i + 4 <= codes.length()) {
                    String hexCode = codes.substring(i, i + 4);
                    // Ignore the "0000" padding that some cars send
                    if (!hexCode.equals("0000")) {
                        troubleCodes.add(formatDtc(hexCode));
                    }
                }
            }
        }
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
        if (troubleCodes.isEmpty()) {
            return "No trouble codes found.";
        }
        return String.join(", ", troubleCodes);
    }
}