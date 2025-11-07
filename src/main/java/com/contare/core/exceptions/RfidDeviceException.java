package com.contare.core.exceptions;

public class RfidDeviceException extends Exception {

    public RfidDeviceException(final String message) {
        super(message);
    }

    public RfidDeviceException(final String fmt, final Object... args) {
        this(String.format(fmt, args));
    }

    public RfidDeviceException(final Throwable cause) {
        super(cause);
    }

    public RfidDeviceException(final Throwable cause, final String message) {
        super(message, cause);
    }

    public RfidDeviceException(final Throwable cause, final String fmt, final Object... args) {
        this(cause, String.format(fmt, args));
    }

}
