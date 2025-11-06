
package com.contare.chafon;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Exception representing a device/tag return code from the Cafon reader API.
 * <p>
 * Contains:
 * - int code   : numeric value treated as unsigned byte (0..255)
 * - String hex : textual hex representation like "0x05"
 * - String message : human readable description for the code
 * <p>
 * Static helpers:
 * - ChafonDeviceException.ofCode(int code) : build exception object for the code (uses "Unknown error code" for unmapped codes)
 * - ChafonDeviceException.throwIfError(int code) : throws a ChafonDeviceException when code != 0x00
 * <p>
 * Notes:
 * - Input ints are treated as unsigned bytes via code & 0xFF.
 * - 0x00 is considered success and will NOT cause throwIfError(...) to throw.
 */
public final class ChafonDeviceStatus {

    private final int code;       // 0..255
    private final String hex;     // "0x05"
    private final String message; // human readable description for the code
    private final boolean success;

    private ChafonDeviceStatus(int code, String hex, String message, boolean success) {
        this.code = code & 0xFF;
        this.hex = Objects.requireNonNull(hex, "hex");
        this.message = Objects.requireNonNull(message, "message");
        this.success = success;
    }

    /**
     * Returns the canonical status instance for known codes, or a small new instance for unknown codes.
     * 0x00 will always be returned as the success instance.
     */
    public static ChafonDeviceStatus of(int code) {
        final int c = code & 0xFF;

        final ChafonDeviceStatus known = map.get(c);
        if (known != null) {
            return known;
        }

        final String hex = String.format("0x%02X", c);
        final boolean success = (c == 0x00);
        final String desc = success ? "API is called successfully." : "Unknown error code";
        // Unknown codes are rare; create a small instance carrying the code/hex/desc
        return new ChafonDeviceStatus(c, hex, desc, success);
    }

    public int getCode() {
        return code;
    }

    public String getHex() {
        return hex;
    }

    public String getMessage() {
        return message;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getFullMessage() {
        return String.format("%s (%s)", message, hex);
    }

    // --- internal canonical mapping for known codes ---
    private static final Map<Integer, ChafonDeviceStatus> map;

    static {
        final Map<Integer, ChafonDeviceStatus> m = new HashMap<>();
        m.put(0x00, new ChafonDeviceStatus(0x00, "0x00", "API is called successfully.", true));
        m.put(0x01, new ChafonDeviceStatus(0x01, "0x01", "No find tag", false));
        m.put(0x05, new ChafonDeviceStatus(0x05, "0x05", "Access password error.", false));
        m.put(0x09, new ChafonDeviceStatus(0x09, "0x09", "Kill password error.", false));
        m.put(0x0A, new ChafonDeviceStatus(0x0A, "0x0A", "All-zero tag killing password is invalid.", false));
        m.put(0x0B, new ChafonDeviceStatus(0x0B, "0x0B", "Command is not support by the tag", false));
        m.put(0x0C, new ChafonDeviceStatus(0x0C, "0x0C", "All-zero tag access password is invalid for such command.", false));
        m.put(0x0D, new ChafonDeviceStatus(0x0D, "0x0D", "Fail to setup read protection for a protection enabled tag.", false));
        m.put(0x0E, new ChafonDeviceStatus(0x0E, "0x0E", "Fail to unlock a protection disabled tag.", false));
        m.put(0x10, new ChafonDeviceStatus(0x10, "0x10", "Some bytes stored in the tag are locked.", false));
        m.put(0x11, new ChafonDeviceStatus(0x11, "0x11", "Lock operation failed.", false));
        m.put(0x12, new ChafonDeviceStatus(0x12, "0x12", "Already locked, lock operation failed.", false));
        m.put(0x13, new ChafonDeviceStatus(0x13, "0x13", "Fail to store the value of some preserved parameters. Configuration will still valid before reader shut down.", false));
        m.put(0x14, new ChafonDeviceStatus(0x14, "0x14", "Modification failed.", false));
        m.put(0x30, new ChafonDeviceStatus(0x30, "0x30", "Communication error.", false));
        m.put(0xF8, new ChafonDeviceStatus(0xF8, "0xF8", "Error detected in antenna check.", false));
        m.put(0xF9, new ChafonDeviceStatus(0xF9, "0xF9", "Operation failed.", false));
        m.put(0xFA, new ChafonDeviceStatus(0xFA, "0xFA", "Tag is detected, but fails to complete operation due to poor communication.", false));
        m.put(0xFB, new ChafonDeviceStatus(0xFB, "0xFB", "No tag is detected.", false));
        m.put(0xFC, new ChafonDeviceStatus(0xFC, "0xFC", "Error code returned from tags.", false));
        m.put(0xFD, new ChafonDeviceStatus(0xFD, "0xFD", "Command length error.", false));
        m.put(0xFE, new ChafonDeviceStatus(0xFE, "0xFE", "Illegal command.", false));
        m.put(0xFF, new ChafonDeviceStatus(0xFF, "0xFF", "Parameter error.", false));

        map = Collections.unmodifiableMap(m);
    }

}
