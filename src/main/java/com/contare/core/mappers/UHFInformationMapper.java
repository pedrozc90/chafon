package com.contare.core.mappers;

import com.contare.chafon.UHFInformation;
import org.jboss.logging.Logger;

public class UHFInformationMapper {

    private static final Logger logger = Logger.getLogger(UHFInformationMapper.class);

    /**
     *
     * @param version         (2 bytes)
     * @param power           (1 bytes)
     * @param band            (1 bytes)
     * @param maxFrequency    (1 bytes)
     * @param minFrequency    (1 bytes)
     * @param beep            (1 bytes)
     * @param ant             (1 int)
     * @param powerPerAntenna - array of integer
     * @return
     */
    public static UHFInformation parse(
        final byte[] version,
        final byte[] power,
        final byte[] band,
        final byte[] maxFrequency,
        final byte[] minFrequency,
        final byte[] beep,
        final int[] ant,
        final int[] powerPerAntenna,
        final int antennas,
        final String serialNo
    ) {
        final int major = Byte.toUnsignedInt(version[0]);
        final int minor = Byte.toUnsignedInt(version[1]);
        final String _version = String.format("%d.%d", major, minor);

        final int powerMask = Byte.toUnsignedInt(power[0]); // could be 0..33 or 255 if called after 'SetRfPowerByAnt'
        final boolean powerPerAntennaMode = (powerMask == 0xFF);
        final int powerValue = powerPerAntennaMode ? 255 : powerMask;
        logger.debugf("Power mask = 0x%08X, binary = %s, mode = %s", powerMask, Integer.toBinaryString(powerMask), powerPerAntennaMode ? "antenna" : "global");

        final int bandMask = Byte.toUnsignedInt(band[0]);
        final int maxIndex = Byte.toUnsignedInt(maxFrequency[0]);
        final int minIndex = Byte.toUnsignedInt(minFrequency[0]);
        logger.debugf("Frequency band = %d, index = %d .. %d", bandMask, minIndex, maxIndex);

        final int beepMask = Byte.toUnsignedInt(beep[0]);
        logger.debugf("Beep mask = 0x%08X, binary = %s", beepMask, Integer.toBinaryString(beepMask));

        final int antMask = ant[0];
        logger.debugf("Antenna mask = 0x%08X, binary = %s", antMask, Integer.toBinaryString(antMask));

        // final int antennas = reader.GetAntennas();
        final int[] enabled = new int[antennas];
        final int limit = Math.max(Math.min(antennas, Integer.SIZE), 0);
        for (int i = 0; i < limit; i++) {
            if ((antMask & (1 << i)) != 0) {
                enabled[i] = i + 1; // antenna numbering from 1
            }
        }

        return new UHFInformation(_version, powerValue, powerPerAntenna, bandMask, minIndex, maxIndex, beepMask, (beepMask == 1), antMask, enabled, serialNo);
    }

}
