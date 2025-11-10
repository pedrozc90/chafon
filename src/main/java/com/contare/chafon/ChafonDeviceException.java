package com.contare.chafon;

import com.contare.core.exceptions.RfidDeviceException;

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
public final class ChafonDeviceException extends RfidDeviceException {

    private final ChafonDeviceStatus status;

    private ChafonDeviceException(final String message) {
        super(message);
        this.status = ChafonDeviceStatus.of(-1);
    }

    /**
     * Create an exception carrying the given status. The exception message is the status full message.
     */
    private ChafonDeviceException(final ChafonDeviceStatus status) {
        super(String.format("%s (%s)", Objects.requireNonNull(status, "status").getMessage(), status.getHex()));
        this.status = status;
    }

    public static ChafonDeviceException of(final String fmt, final Object... args) {
        final String message = String.format(fmt, args);
        return new ChafonDeviceException(message);
    }

    public static ChafonDeviceException of(final int code) {
        final ChafonDeviceStatus status = ChafonDeviceStatus.of(code);
        return of(status);
    }

    public static ChafonDeviceException of(final ChafonDeviceStatus status) {
        return new ChafonDeviceException(status);
    }

    public ChafonDeviceStatus getStatus() {
        return status;
    }

    public int getCode() {
        return status.getCode();
    }

    public String getHex() {
        return status.getHex();
    }

    /**
     * Override getMessage to return the short description (not the full message with hex).
     * The Throwable#getMessage() will still return the formatted message set in super() if needed.
     */
    @Override
    public String getMessage() {
        return status.getMessage();
    }

    /**
     * The combined description + hex, useful for logging.
     */
    public String getFullMessage() {
        return status.getFullMessage();
    }

}
