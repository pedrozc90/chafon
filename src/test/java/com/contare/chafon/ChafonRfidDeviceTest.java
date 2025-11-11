package com.contare.chafon;

import com.contare.core.objects.Options;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ChafonRfidDeviceTest {

    private ChafonRfidDevice device;

    @BeforeEach
    public void setUp() {
        final List<Frequency> frequencies = List.of(Frequency.US);
        final Options opts = new Options("2C-AC-44-04-97-01", "192.168.1.200", 2022, 4, false, frequencies, 1_000);
        device = new ChafonRfidDevice();
        device.init(opts);
    }

    // SET ANTENNAS
    @Test
    public void SetAntennas_EnableAll() {
        assertAll(
            () -> assertTrue(device.SetAntennas(1, true), "Failed to enable antenna 1"),
            () -> assertTrue(device.SetAntennas(2, true), "Failed to enable antenna 2"),
            () -> assertTrue(device.SetAntennas(3, true), "Failed to enable antenna 3"),
            () -> assertTrue(device.SetAntennas(4, true), "Failed to enable antenna 4")
        );
    }

    @Test
    public void SetAntennas_DisableAll() {
        assertAll(
            () -> assertTrue(device.SetAntennas(1, false), "Failed to disable antenna 1"),
            () -> assertTrue(device.SetAntennas(2, false), "Failed to disable antenna 2"),
            () -> assertTrue(device.SetAntennas(3, false), "Failed to disable antenna 3"),
            () -> assertTrue(device.SetAntennas(4, false), "Failed to disable antenna 4")
        );
    }

}
