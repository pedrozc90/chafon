package com.contare.core.mappers;

import com.contare.chafon.ChafonDeviceException;
import com.contare.chafon.ChafonDeviceStatus;
import com.contare.chafon.UHFInformation;

public class UHFInformationMapper {

    /**
     *
     * @param _version (2 bytes)
     * @param _power (1 bytes)
     * @param _band (1 bytes)
     * @param _maxFrequency (1 bytes)
     * @param _minFrequency (1 bytes)
     * @param _beep (1 bytes)
     * @param _ant (1 int)
     * @return
     */
    public static UHFInformation parse(
        final byte[] _version,
        final byte[] _power,
        final byte[] _band,
        final byte[] _maxFrequency,
        final byte[] _minFrequency,
        final byte[] _beep,
        final int[] _ant,
        final int[] powerPerAntenna,
        final int antennas,
        final String serialNo
    ) {
        final int major = Byte.toUnsignedInt(_version[0]);
        final int minor = Byte.toUnsignedInt(_version[1]);
        final String version = String.format("%d.%d", major, minor);

        final int powerByte = Byte.toUnsignedInt(_power[0]); // could be 0..33 or 255 if called after 'SetRfPowerByAnt'
        System.out.printf("power mask = 0x%08X, binary=%s%n", _power[0], Integer.toBinaryString(powerByte));
        final boolean powerPerAntennaMode = (powerByte == 0xFF);
        final int power = powerPerAntennaMode ? -1 : powerByte;
        // final int[] powerPerAntenna = getAntennaPower();

        final int band = Byte.toUnsignedInt(_band[0]);
        final int maxIndex = Byte.toUnsignedInt(_maxFrequency[0]);
        final int minIndex = Byte.toUnsignedInt(_minFrequency[0]);

        final int beep = Byte.toUnsignedInt(_beep[0]);
        final String bandMask = Integer.toBinaryString(beep);
        System.out.printf("beep mask = 0x%08X, binary=%s%n", _beep[0], bandMask);

        final int mask = _ant[0];
        System.out.printf("ant mask = 0x%08X, binary=%s%n", mask, Integer.toBinaryString(mask));

        // final int antennas = reader.GetAntennas();
        final int[] enabled = new int[antennas];
        final int limit = Math.max(Math.min(antennas, Integer.SIZE), 0);
        for (int i = 0; i < limit; i++) {
            if ((mask & (1 << i)) != 0) {
                enabled[i] = i + 1; // antenna numbering from 1
            }
        }

        // final String serialNo = reader.GetSerialNo();

        return new UHFInformation(version, power, powerPerAntenna, band, bandMask, maxIndex, minIndex, (beep == 1), mask, enabled, serialNo);
    }

}
