package com.example.zavobd.obd;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class TroubleCodesCommand extends AbstractObdCommand {

    private static final String TAG = TroubleCodesCommand.class.getSimpleName();
    private List<String> troubleCodes = new ArrayList<>();
    private int numberOfCodes = 0;

    public TroubleCodesCommand() {
        // Mode 03: Request Stored Diagnostic Trouble Codes
        super("03", "Diagnostic Trouble Codes", "");
    }

    @Override
    protected void performCalculations() {
        if (rawResponse == null || rawResponse.isEmpty()) {
            Log.e(TAG, "No raw response to parse.");
            return;
        }

        // Response for mode 03 is complex. It can span multiple lines
        // and needs careful parsing.
        // Example response: "43 01 04 01 30 00 00" (first '43' is response to '03')
        // Each DTC is 2 bytes.
        // The first part of the rawResponse might be the command echo if not stripped (e.g., "03")
        // or just "43" if the echo was stripped.

        String workingResponse = rawResponse.replaceAll("\\s", ""). // Remove all whitespace
                replaceAll(">", ""); // Remove prompt character if present

        // If the response starts with the command itself or "43" (response for 03), strip it.
        if (workingResponse.startsWith("03")) {
            workingResponse = workingResponse.substring(2);
        }
        if (workingResponse.startsWith("43")) {
            workingResponse = workingResponse.substring(2);
        }

        Log.d(TAG, "Cleaned workingResponse for DTC parsing: " + workingResponse);

        // Each DTC is represented by 4 hex characters (2 bytes)
        // For example, P0104 is represented by 0104.
        // The response will be a sequence of these 2-byte codes.
        // Example: 010401300000 -> P0104, P0130, P0000 (P0000 might indicate no more codes or an actual P0000 code)

        int begin = 0;
        while (begin < workingResponse.length() - 3) { // Need at least 4 chars for a DTC
            String dtc = "";
            String byte1Str = workingResponse.substring(begin, begin + 2);
            String byte2Str = workingResponse.substring(begin + 2, begin + 4);

            try {
                int b1 = Integer.parseInt(byte1Str, 16);

                // First character of DTC is determined by the first two bits of the first byte
                // 00xx = P (Powertrain)
                // 01xx = C (Chassis)
                // 10xx = B (Body)
                // 11xx = U (Network)
                int firstCharIdentifier = (b1 & 0xC0) >> 6; // Get the first two bits

                switch (firstCharIdentifier) {
                    case 0: // 00xx
                        dtc += "P";
                        break;
                    case 1: // 01xx
                        dtc += "C";
                        break;
                    case 2: // 10xx
                        dtc += "B";
                        break;
                    case 3: // 11xx
                        dtc += "U";
                        break;
                    default:
                        Log.w(TAG, "Unknown DTC category identifier: " + firstCharIdentifier);
                        dtc += "?"; // Should not happen
                        break;
                }

                // The next character is derived from the next two bits of the first byte
                // (bits 5 and 4 of b1)
                int secondCharIdentifier = (b1 & 0x30) >> 4;
                dtc += Integer.toHexString(secondCharIdentifier).toUpperCase();

                // The last three characters of the DTC code are:
                // last 4 bits of the first byte (b1 & 0x0F)
                // and the entire second byte (byte2Str)
                dtc += Integer.toHexString(b1 & 0x0F).toUpperCase();
                dtc += byte2Str.toUpperCase();

                // "P0000" or similar might indicate "no codes" or padding,
                // but we will list all codes returned.
                // Actual "no codes" is often an empty response after "43" or just "43".
                if (!dtc.endsWith("0000") || troubleCodes.isEmpty()) { // Add P0000 only if it's the first or only code, can be a valid code
                    troubleCodes.add(dtc);
                } else if (dtc.endsWith("0000") && workingResponse.length() == 4) { // Only one "P0000" received
                     troubleCodes.add(dtc);
                }


            } catch (NumberFormatException e) {
                Log.e(TAG, "Error parsing DTC bytes: " + byte1Str + byte2Str, e);
            }
            begin += 4; // Move to the next 2-byte pair
        }
        numberOfCodes = troubleCodes.size();
        Log.i(TAG, "Found " + numberOfCodes + " trouble codes: " + troubleCodes.toString());
    }

    /**
     * @return The list of DTCs found. Example: ["P0104", "C0130"]
     */
    public List<String> getTroubleCodesList() {
        return troubleCodes;
    }

    /**
     * @return Number of trouble codes found.
     */
    public int getNumberOfCodes() {
        return numberOfCodes;
    }

    @Override
    public String getFormattedResult() {
        if (rawResponse == null || rawResponse.isEmpty()){
            return "No data";
        }
        if (troubleCodes.isEmpty() && (rawResponse.trim().equals("43") || rawResponse.trim().isEmpty())) {
            return "No trouble codes found.";
        }
        if (troubleCodes.isEmpty()) {
            return "No trouble codes found. Raw: " + rawResponse;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Found ").append(numberOfCodes).append(" DTC(s):\n");
        for (int i = 0; i < troubleCodes.size(); i++) {
            sb.append(troubleCodes.get(i));
            if (i < troubleCodes.size() - 1) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }
}
