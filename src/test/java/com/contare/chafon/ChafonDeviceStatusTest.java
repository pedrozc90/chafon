package com.contare.chafon;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ChafonDeviceStatusTest {

    @ParameterizedTest
    @CsvSource(value = {
        "48, 0x30, Communication error.",
        "255, 0xFF, Parameter error."
    })
    public void test(final int code, final String hex, final String message) {
        final ChafonDeviceStatus status = ChafonDeviceStatus.of(code);
        assertEquals(hex, status.getHex());
        assertEquals(message, status.getMessage());
    }

}
