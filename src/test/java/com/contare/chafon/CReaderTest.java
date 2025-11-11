package com.contare.chafon;

import com.rfid.CReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CReaderTest {

    private CReader reader;

    @BeforeEach
    public void setUp() {
        reader = new CReader("192.168.1.200", 2022, 4, 0);

        final int result = reader.Connect();
        assertEquals(0x00, result, "Unable to connect to device");
    }

    @ParameterizedTest
    @ArgumentsSource(AntennaMaskArgumentsProvider.class)
    public void SetAntenna(final int antMask, final int expected) {
        final int result = reader.SetAntenna(0x00, antMask);
        assertEquals(expected, result);
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

}
