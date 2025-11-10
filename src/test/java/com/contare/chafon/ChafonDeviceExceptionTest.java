package com.contare.chafon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ChafonDeviceExceptionTest {

    @Test
    public void createExceptionFromInteger() {
        final ChafonDeviceException exception = ChafonDeviceException.of(0);
        assertEquals(0, exception.getCode());
        assertEquals("0x00", exception.getHex());
        assertEquals("API is called successfully.", exception.getMessage());
    }

    @Test
    public void createExceptionFromHexadecimal() {
        final ChafonDeviceException exception = ChafonDeviceException.of(0x30);
        assertEquals(48, exception.getCode());
        assertEquals("0x30", exception.getHex());
        assertEquals("Communication error.", exception.getMessage());
    }

    @Test
    public void createUnknownException() {
        final ChafonDeviceException exception = ChafonDeviceException.of(-1);
        assertEquals(-1, exception.getCode());
        assertEquals("0x30", exception.getHex());
        assertEquals("Unknown error code.", exception.getMessage());
    }

}
