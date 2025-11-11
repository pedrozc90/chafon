package com.contare.chafon;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class ChafonReaderTest {

    private ChafonReader reader;

    @BeforeEach
    public void setUp() {
        reader = new ChafonReader("192.168.1.200", 2022, 4, false);
        final int connected = reader.Connect();
        assertEquals(0x00, connected, "Failed to connect to device");
    }

    @Test
    public void GetUHFInformation() {
        final UHFInformation result = reader.GetUHFInformation();
        assertNotNull(result);
    }

    @ParameterizedTest
    @ArgumentsSource(AntennaMaskArgumentsProvider.class)
    public void SetAntenna(final int antMask, final int expected) {
        final int result = reader.SetAntenna(0x00, antMask);
        assertEquals(expected, result);
    }

    @Test
    public void SetAntenna_1_3() {
        assertAll(
            () -> assertTrue(reader.SetAntenna(1, true, true)),
            () -> assertTrue(reader.SetAntenna(3, true, true))
        );
    }

    @Test
    public void GetGPIO() {
        final byte[] result = reader.GetGPIOStatus();
        assertNotNull(result);
    }

    private static class AntennaMaskArgumentsProvider implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(final ExtensionContext context) throws Exception {
            return Stream.of(
                Arguments.of(0x00, 0xFF), // disable all antennas
                Arguments.of(0x01, 0x00), // antenna 1
                Arguments.of(0x05, 0x00), // antenna 1 + 3
                Arguments.of(0x09, 249)  // antenna 1 + 4
            );
        }

    }

    @ParameterizedTest
    @CsvSource(value = {
        "1 , 920.125, 0.25, 0, 19", // Chinese 2
        "2 , 902.75 , 0.50, 0, 49", // US
        "3 , 917.10 , 0.20, 0, 31", // Korean
        "4 , 865.10 , 0.20, 0, 14", // EU
        "8 , 840.125, 0.25, 0, 19", // Chinese 1
        "12, 902.00 , 0.50, 0, 52", // US 3
        "16, 920.25 , 0.50, 0, 9", // HK
        "17, 920.75 , 0.50, 0, 13", // Taiwan
        "18, 916.30 , 1.20, 0, 2", // ETSI UPPER
        "19, 919.25 , 0.50, 0, 7", // Malaysia
        "21, 902.75 , 0.50, 0, 9", // Brazil
        "21, 910.25 , 0.50, 10, 34", // Brazil
        "22, 920.25 , 0.50, 0, 9", // Thailand
        "23, 920.25 , 0.50, 0, 9", // Singapore
        "24, 920.25 , 0.50, 0, 9", // Australia
        "25, 865.10 , 0.60, 0, 3", // India
        "26, 916.25 , 0.50, 0, 22", // Uruguay
        "27, 918.75 , 0.50, 0, 7", // Vietnam
        "28, 916.25 , 0.50, 0, 0", // Israel
        "29, 917.25 , 0.50, 0, 0", // Indonesia
        "29, 919.75 , 0.50, 1, 3", // Indonesia
        "30, 922.25 , 0.50, 0, 9", // New Zealand
        "31, 916.80 , 1.20, 0, 3", // Japan 2
        "32, 916.25 , 0.50, 0, 22", // Peru
        "33, 916.20 , 1.20, 0, 3", // Russia
        "34, 915.60 , 0.20, 0, 16", // South Arica
        "35, 918.25 , 0.50, 0, 3", // Philippines
        // "??, 868.0  , 0.1 , 0, 6", // Ukraine
        // "??, 916.2  , 0.9 , 0, 11", // Peru
        // "??, 865.7  , 0.6 , 0, 3", // EU 3
    })
    public void SetFrequency(final int band, final double fStart, final double fStep, final int minIndex, final int maxIndex) {
        for (int index = minIndex; index <= maxIndex; index++) {
            final Frequency frequency = new Frequency(band, fStart, fStep, minIndex, index, false);
            final boolean updated = reader.SetFrequency(frequency);
            assertTrue(updated);
        }
    }

}
