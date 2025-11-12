package com.contare.chafon;

import com.contare.core.RfidDevice;
import com.contare.core.exceptions.RfidDeviceException;
import com.contare.core.mappers.TagMetadataMapper;
import com.contare.core.mappers.UHFInformationMapper;
import com.contare.core.objects.Options;
import com.contare.core.objects.TagMetadata;
import com.rfid.CReader;
import com.rfid.ReadTag;
import com.rfid.TagCallback;
import com.rfid.Utils;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class ChafonUHFDevice implements RfidDevice {

    private static final Logger logger = Logger.getLogger(ChafonUHFDevice.class);
    private static final int MIN_POWER_DBM = 0;
    private static final int MAX_POWER_DBM = 33;
    private static final int DEFAULT_POWER_DBM = 0;

    private Options opts;
    private CReader reader;
    private int antennas = 4;
    private final Set<String> buffer = new HashSet<>();

    public Set<String> getBuffer() {
        return Collections.unmodifiableSet(buffer);
    }

    @Override
    public boolean init(final Options opts) {
        this.opts = opts;
        this.antennas = opts.antennas;

        reader = new CReader(opts.ip, opts.port, opts.antennas, opts.verbose ? 1 : 0);

        this.SetCallBack((final ReadTag readTag) -> {
            if (readTag != null) {
                final TagMetadata tag = TagMetadataMapper.toDto(readTag);
                logger.infof("Tag Received: %s", tag);

                if (buffer.add(tag.getEpc())) {
                    logger.debugf("EPC %s added to buffer.", tag.getEpc());
                }
            } else {
                logger.warn("Tag Received is empty");
            }
        });

        return this.connect();
    }

    @Override
    public boolean connect() {
        try {
            final int result = reader.Connect();
            if (result != 0x00) {
                throw ChafonDeviceException.of(result);
            }
            return true;
        } catch (ChafonDeviceException e) {
            logger.error("Failed to connect device", e);
            return false;
        }
    }

    @Override
    public boolean start() throws RfidDeviceException {
        logger.debug("Starting device");

        // reset buffer
        buffer.clear();

        // final boolean fUpdated = this.SetFrequency(Frequency.BRAZIL);
        // if (fUpdated) {
        //     logger.debug("Device frequency has been updated");
        // }

        final boolean pUpdated = this.SetPower(30);
        if (pUpdated) {
            logger.debug("Device power has been updated");
        }

        final boolean bUpdated = this.SetBeep(true);
        if (bUpdated) {
            logger.debug("Device beep has been updated");
        }

        // final ReaderParameter params = reader.GetInventoryParameter();
        // params.SetScanTime(255);
        // reader.SetInventoryParameter(params);

        final int result = reader.StartRead();
        if (result != 0x00) {
            throw ChafonDeviceException.of(ChafonDeviceStatus.of(result));
        }

        return true;
    }

    @Override
    public boolean stop() {
        final long start = System.currentTimeMillis();
        logger.debug("Stopping device");
        if (reader != null) {
            reader.StopRead(); // this blocks until the reader thread finishes (per SDK behaviour)
        }
        final long elapsed = System.currentTimeMillis() - start;
        logger.debugf("Device stopped (%d ms)", elapsed);
        return true;
    }

    @Override
    public void close() throws IOException {
        final long start = System.currentTimeMillis();
        logger.debug("Closing device");
        this.stop();
        if (reader != null) {
            if (reader.isConnect) {
                reader.DisConnect();
            }
        }
        final long elapsed = System.currentTimeMillis() - start;
        logger.debugf("Device closed (%d ms)", elapsed);
    }

    // API
    public void SetCallBack(final Consumer<ReadTag> onRead) {
        reader.SetCallBack(new TagCallback() {
            @Override
            public void tagCallback(final ReadTag readTag) {
                onRead.accept(readTag);
            }

            @Override
            public void StopReadCallback() {
                logger.debugf("Callback stopped.");
            }
        });
    }

    public UHFInformation GetUHFInformation() {
        try {
            final byte[] version = new byte[2]; // bit 1 = version number, bit 2 = subversion number
            final byte[] power = new byte[1]; // output power (range 0 ~ 30 dbm)
            final byte[] band = new byte[1]; // spectrum band (1 - Chinese 1, 2 - US, 3 - Korean, 4 - EU, 8 - Chinese 2, 0 - All)
            final byte[] maxFrequency = new byte[1]; // current maximum frequency of the reader
            final byte[] minFrequency = new byte[1]; // current minimum frequency of the reader
            final byte[] beep = new byte[1]; // buzzer beeps information
            final int[] ant = new int[1]; // each bit represent an antenna number, such as 0x00009, the binary is 00000000 00001001, indicating antenna 1 to 4

            final int result = reader.GetUHFInformation(version, power, band, maxFrequency, minFrequency, beep, ant);
            if (result != 0x00) {
                throw ChafonDeviceException.of(result);
            }

            final int[] powerPerAntenna = this.GetRfPowerByAnt();
            final String serialNo = this.GetSerialNo();

            return UHFInformationMapper.parse(version, power, band, maxFrequency, minFrequency, beep, ant, powerPerAntenna, antennas, serialNo);
        } catch (ChafonDeviceException e) {
            logger.error("Error getting UHF information.", e);
            return null;
        }
    }

    public int GetAntennaMask() {
        final UHFInformation info = GetUHFInformation();
        return (info != null) ? info.getAntennaMask() : 255;
    }

    /**
     * Overload: SetRegion
     * Set the working frequency band of the reader.
     *
     * @param value - frequency object
     * @return true if successful, else false.
     */
    public boolean SetFrequency(final Frequency value) {
        final long start = System.currentTimeMillis();
        try {
            final int opt = 0; // save
            final int bandId = value.getBand();
            final int maxIndex = value.getMaxIndex();
            final int minIndex = value.getMinIndex();

            final int result = reader.ExtSetRegion(opt, bandId, maxIndex, minIndex);
            if (result != 0x00) {
                throw ChafonDeviceException.of(result);
            }

            // Log what we actually set
            final long elapsed = System.currentTimeMillis() - start;
            logger.infof("SetRegion: band = %d, indices = %d .. %d, frequency=%.3f ~ %.3f MHz (%d ms)", bandId, minIndex, maxIndex, value.getMinFrequency(), value.getMaxFrequency(), elapsed);

            return true;
        } catch (ChafonDeviceException e) {
            logger.errorf(e, "Error setting frequency. (%d ms)", System.currentTimeMillis() - start);
            return false;
        }
    }

    public boolean SetPower(final int value) {
        try {
            if (value < MIN_POWER_DBM || value > MAX_POWER_DBM) {
                throw ChafonDeviceException.of("Power must be between 0 and 33.");
            }
            final int result = reader.SetRfPower(value);
            if (result != 0x00) {
                throw ChafonDeviceException.of(result);
            }
            return true;
        } catch (ChafonDeviceException e) {
            logger.error("Error setting power.", e);
            return false;
        }
    }

    public int[] GetRfPowerByAnt() {
        final long start = System.currentTimeMillis();
        try {
            final byte[] _power = new byte[antennas];
            final int result = reader.GetRfPowerByAnt(_power);
            if (result != 0x00) {
                throw ChafonDeviceException.of(result);
            }

            final int[] power = new int[_power.length];
            for (int i = 0; i < power.length; i++) {
                power[i] = Byte.toUnsignedInt(_power[i]);
            }

            logger.debugf("Obtained antenna power. (%d ms)", System.currentTimeMillis() - start);

            return power;
        } catch (ChafonDeviceException e) {
            logger.errorf(e, "Error getting antenna power. (%d ms)", System.currentTimeMillis() - start);
            return null;
        }
    }

    public boolean SetRfPowerByAntenna(final int[] power) {
        final long start = System.currentTimeMillis();

        try {
            if (power == null) {
                throw new IllegalArgumentException("Antenna power must not be null.");
            }

            if (power.length > antennas) {
                throw new IllegalArgumentException("Antenna length must be <= number of antennas (" + antennas + ")");
            }

            // Create a array power sized to the device's antenna count.
            final byte[] array = new byte[antennas];

            for (int i = 0; i < antennas; i++) {
                final int val = (i < power.length) ? power[i] : DEFAULT_POWER_DBM;

                // Validate (or clamp). I recommend validating and throwing so callers know they passed bad values.
                if (val < MIN_POWER_DBM || val > MAX_POWER_DBM) {
                    throw new IllegalArgumentException(String.format("Power for antenna %d out of range: %d (allowed %d..%d)", i + 1, val, MIN_POWER_DBM, MAX_POWER_DBM));
                }

                // safe cast to byte — 0..30 fits into signed byte without wrap
                array[i] = (byte) val;
            }

            final int result = reader.SetRfPowerByAnt(array);

            if (result != 0x00) {
                throw ChafonDeviceException.of(result);
            }
            return true;
        } catch (IllegalArgumentException e) {
            logger.error(e.getMessage(), e);
        } catch (ChafonDeviceException e) {
            logger.errorf(e, "Error setting antenna power. (%d ms)", System.currentTimeMillis() - start);
        }
        return false;
    }

    public int GetWritePower() {
        try {
            final byte[] WritePower = new byte[1];
            final int result = reader.GetWritePower(WritePower);
            if (result != 0x00) {
                throw ChafonDeviceException.of(result);
            }

            return 0x00;
        } catch (ChafonDeviceException e) {
            logger.error("Error getting write power.", e);
            return 0;
        }
    }

    public boolean SetWritePower(final int value, final boolean enabled) {
        try {
            if (value < MIN_POWER_DBM || value > MAX_POWER_DBM) {
                throw new IllegalArgumentException("Power must be between " + MIN_POWER_DBM + " and " + MAX_POWER_DBM + " but received " + value);
            }
            // Bits 0..6 = power, bit7 = enabled flag
            final int packed = (value & 0x7F) | (enabled ? 0x80 : 0x00);
            final byte writePowerByte = (byte) (packed & 0xFF);
            final int result = reader.SetWritePower((byte) 0);
            if (result != 0x00) {
                throw ChafonDeviceException.of(result);
            }
            return true;
        } catch (IllegalArgumentException e) {
            logger.error(e.getMessage(), e);
        } catch (ChafonDeviceException e) {
            logger.errorf(e, "Error getting write power to %s.", value);
        }
        return false;
    }

    /**
     * Enable beep sound notification when the device read a tag.
     *
     * @param enabled - true to enable beep sound notification, false to disable beep sound notification.
     * @return true if beep sound notification changed.
     */
    public boolean SetBeep(final boolean enabled) {
        final long start = System.currentTimeMillis();
        try {
            final int result = reader.SetBeepNotification(enabled ? 0x01 : 0x00);
            if (result != 0x00) {
                throw ChafonDeviceException.of(result);
            }
            logger.debugf("Beep %s (%d ms)", (enabled ? "enabled" : "disabled"), System.currentTimeMillis() - start);
            return true;
        } catch (ChafonDeviceException e) {
            logger.errorf(e, "Error setting beep. (%d ms)", System.currentTimeMillis() - start);
            return false;
        }
    }

    public boolean SetCheckAnt(final boolean enabled) {
        try {
            final int value = enabled ? 1 : 0;
            final int result = reader.SetCheckAnt((byte) value);
            if (result != 0x00) {
                throw ChafonDeviceException.of(result);
            }
            return true;
        } catch (ChafonDeviceException e) {
            logger.error("Error getting check ant.", e);
            return false;
        }
    }

    /**
     * Enable or disable a single antenna.
     *
     * @param pos     - antenna position (1 - 4)
     * @param enabled - true = enable, false = disable
     * @param persist - true = save across power cycles, false = temporary
     * @return true on success (SDK returned 0x00), false on SDK error.
     */
    public boolean SetAntenna(final int pos, final boolean enabled, final boolean persist) {
        try {
            if (pos < 1 || pos > antennas) {
                throw new IllegalArgumentException("Antenna position must be between 1 and " + antennas + ", but received " + pos);
            }

            final int bit = 1 << (pos - 1);

            int antMask = GetAntennaMask();
            if (antMask == 255) {
                throw ChafonDeviceException.of("Unabled to obtain antenna mask");
            }
            logger.debugf("Antenna pos = %d, enabled = %b, bit = %d, mask = 0x%08X, binary = %s", pos, enabled, bit, antMask, Integer.toBinaryString(antMask));

            // Build AntCfg to pass to SDK: use full mask (up to 16 bits).
            // The SDK method will internally branch on antenna count (<=4 / >4) as needed.
            if (enabled) {
                antMask |= bit;
            } else {
                antMask &= ~bit;
            }

            logger.debugf("Antenna pos = %d, enabled = %b, bit = %d, mask = 0x%08X, binary = %s", pos, enabled, bit, antMask, Integer.toBinaryString(antMask));

            // SDK SetOnce semantics: 0 -> persist, 1 -> temporary
            final int setOnce = persist ? 0 : 1;

            final int result = reader.SetAntenna(setOnce, antMask);
            if (result != 0x00) {
                throw ChafonDeviceException.of(result);
            }
            return true;
        } catch (IllegalArgumentException e) {
            logger.error(e.getMessage(), e);
        } catch (ChafonDeviceException e) {
            logger.error("Error setting antenna.", e);
        }
        return false;
    }

    public boolean SetAntennas(final int index, final boolean enabled) {
        return this.SetAntenna(index, enabled, true);
    }

    public byte[] GetGPIOStatus() {
        try {
            byte[] output = new byte[1];
            int result = reader.GetGPIOStatus(output);
            if (result != 0x00) {
                throw ChafonDeviceException.of(result);
            }
            return output;
        } catch (ChafonDeviceException e) {
            logger.error("Error getting GPIO status.", e);
            return null;
        }
    }

    public String GetSerialNo() {
        return reader.GetSerialNo();
    }

    /**
     * Command used to measure the return loss of the antenna port.
     *
     * @param TestFreq   - 4 bytes, measure the frequency used, in KHz, with the high byte first. The frequency must be a multiple of 125KHz or 100KHz, with the high byte first
     * @param Ant        - Measure the antenna port, 0~15 represent antenna 1~antenna 16 respectively
     * @param ReturnLoss - 1 byte, the measurement result of the return loss value, in dB.
     * @return 0x00 if successful, else returns an error code.
     */
    private int MeasureReturnLoss(final byte[] TestFreq, final byte Ant, final byte[] ReturnLoss) {
        return reader.MeasureReturnLoss(TestFreq, Ant, ReturnLoss);
    }

    public void MeasureReturnLoss() {
        final long start = System.currentTimeMillis();
        try {
            final byte[] freq = new byte[4];
            final byte ant = 0x00;
            final byte[] loss = new byte[1];
            final int result = this.MeasureReturnLoss(freq, ant, loss);
            if (result != 0x00) {
                throw ChafonDeviceException.of(result);
            }
            logger.debugf("MeasureReturnLoss returned (%d ms)", System.currentTimeMillis() - start);
        } catch (ChafonDeviceException e) {
            logger.errorf(e, "Error getting MeasureReturnLoss. (%d ms)", System.currentTimeMillis() - start);
        }
    }

}
