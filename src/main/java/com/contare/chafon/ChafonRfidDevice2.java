package com.contare.chafon;

import com.contare.core.RfidDevice;
import com.contare.core.mappers.TagMetadataMapper;
import com.contare.core.objects.Options;
import com.contare.core.objects.TagMetadata;
import com.rfid.ReadTag;
import com.rfid.ReaderParameter;
import com.rfid.TagCallback;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.IOException;
import java.time.Instant;

public class ChafonRfidDevice2 implements RfidDevice {

    private static final int MIN_POWER_DBM = 0;
    private static final int MAX_POWER_DBM = 33;
    private static final int DEFAULT_POWER_DBM = 0;

    private ChafonReader reader;

    @Override
    public boolean init(final Options opts) {

        reader = new ChafonReader(opts.ip, opts.port, opts.antennas, opts.verbose);

        reader.SetCallBack(new TagCallback() {
            @Override
            public void tagCallback(final ReadTag readTag) {
                try {
                    final TagMetadata tag = TagMetadataMapper.toDto(readTag);
                    System.out.printf("[%s] Tag Received: %s%n", Instant.ofEpochMilli(System.currentTimeMillis()), tag);
                } catch (Exception e) {
                    System.err.println("Error parsing tag: " + e.getMessage());
                }
            }

            @Override
            public void StopReadCallback() {
                System.out.println("Stop Read Callback");
            }
        });

        return true;
    }

    @Override
    public boolean start() {
        System.out.println("Starting device");
        try {
            // TODO: alternar frequencia durante leitura.
            final boolean fUpdated = this.setFrequency(Frequency.BRAZIL_A);
            if (fUpdated) {
                System.out.println("Device frequency has been updated");
            }

            final boolean pUpdated = this.setPower(30);
            if (pUpdated) {
                System.out.println("Device power has been updated");
            }

            final boolean bUpdated = this.setBeep(true);
            if (bUpdated) {
                System.out.println("Device beep has been updated");
            }

            final ReaderParameter params = reader.GetInventoryParameter();
            params.SetScanTime(30);
            reader.SetInventoryParameter(params);

            final boolean started = this.startRead();
            if (started) {
                System.out.println("Device start to read tags.");
            }

            return started;
        } catch (final Exception e) {
            final String stackTrace = ExceptionUtils.getStackTrace(e);
            System.err.println("Error starting device. Cause: " + stackTrace);
            return false;
        }
    }

    @Override
    public boolean stop() {
        final long start = System.currentTimeMillis();
        System.out.println("Stopping device");
        if (reader != null) {
            reader.StopRead(); // this blocks until the reader thread finishes (per SDK behaviour)
        }
        final long elapsed = System.currentTimeMillis() - start;
        System.out.printf("Device stopped in %d ms%n", elapsed);
        return true;
    }

    @Override
    public void close() throws IOException {
        final long start = System.currentTimeMillis();
        System.out.println("Closing device");
        this.stop();
        if (reader != null) {
            if (reader.isConnect) {
                reader.DisConnect();
            }
        }
        final long elapsed = System.currentTimeMillis() - start;
        System.out.printf("Device closed in %d ms%n", elapsed);
    }

    // API
    public boolean connect() throws ChafonDeviceException {
        final int res = reader.Connect();
        final ChafonDeviceStatus status = ChafonDeviceStatus.of(res);
        if (!status.isSuccess()) {
            throw ChafonDeviceException.of(status);
        }
        return true;
    }

    private boolean startRead() throws ChafonDeviceException {
        final int res = reader.StartRead();
        final ChafonDeviceStatus status = ChafonDeviceStatus.of(res);
        if (!status.isSuccess()) {
            throw ChafonDeviceException.of(status);
        }
        return true;
    }

    public Metadata getInformation() throws ChafonDeviceException {
        final byte[] _version = new byte[2]; // bit 1 = version number, bit 2 = subversion number
        final byte[] _power = new byte[1]; // output power (range 0 ~ 30 dbm)
        final byte[] _band = new byte[1]; // spectrum band (1 - Chinese 1, 2 - US, 3 - Korean, 4 - EU, 8 - Chinese 2, 0 - All)
        final byte[] _maxFrequency = new byte[1]; // current maximum frequency of the reader
        final byte[] _minFrequency = new byte[1]; // current minimum frequency of the reader
        final byte[] _beep = new byte[1]; // buzzer beeps information
        final int[] _ant = new int[1]; // each bit represent an antenna number, such as 0x00009, the binary is 00000000 00001001, indicating antenna 1 to 4

        int res = reader.GetUHFInformation(_version, _power, _band, _maxFrequency, _minFrequency, _beep, _ant);
        final ChafonDeviceStatus status = ChafonDeviceStatus.of(res);
        if (!status.isSuccess()) {
            throw ChafonDeviceException.of(status);
        }

        final int major = Byte.toUnsignedInt(_version[0]);
        final int minor = Byte.toUnsignedInt(_version[1]);
        final String version = String.format("%d.%d", major, minor);

        final int powerByte = Byte.toUnsignedInt(_power[0]); // could be 0..33 or 255 if called after 'SetRfPowerByAnt'
        System.out.printf("power mask = 0x%08X, binary=%s%n", _power[0], Integer.toBinaryString(powerByte));
        final boolean powerPerAntennaMode = (powerByte == 0xFF);
        final int power = powerPerAntennaMode ? -1 : powerByte;
        final int[] powerPerAntenna = getAntennaPower();

        final int band = Byte.toUnsignedInt(_band[0]);
        final int maxIndex = Byte.toUnsignedInt(_maxFrequency[0]);
        final int minIndex = Byte.toUnsignedInt(_minFrequency[0]);

        final int beep = Byte.toUnsignedInt(_beep[0]);
        System.out.printf("beep mask = 0x%08X, binary=%s%n", _beep[0], Integer.toBinaryString(beep));

        final int mask = _ant[0];
        System.out.printf("ant mask = 0x%08X, binary=%s%n", mask, Integer.toBinaryString(mask));

        final int antennas = reader.GetAntennas();
        final int[] enabled = new int[antennas];
        final int limit = Math.max(0, Math.min(antennas, Integer.SIZE));
        for (int i = 0; i < limit; i++) {
            if ((mask & (1 << i)) != 0) {
                enabled[i] = i + 1; // antenna numbering from 1
            }
        }

        final String serialNo = reader.GetSerialNo();

        return new Metadata(version, power, powerPerAntenna, band, maxIndex, minIndex, (beep == 1), mask, enabled, serialNo);
    }

    /**
     * Set frequency by preset name (e.g. "US", "BRAZIL_A", "BRAZIL_B", "EU", "CHINESE_2").
     */
    public boolean setFrequency(final Frequency freq) throws ChafonDeviceException {
        final long start = System.currentTimeMillis();

        // SDK SetRegion order: (band, maxfre, minfre)
        int bandId = freq.getBand();
        int maxIndex = freq.getMaxIndex();
        int minIndex = freq.getMinIndex();

        int res = reader.SetRegion(bandId, maxIndex, minIndex);
        final ChafonDeviceStatus status = ChafonDeviceStatus.of(res);
        if (!status.isSuccess()) {
            throw ChafonDeviceException.of(status);
        }

        // Log what we actually set
        final long elapsed = System.currentTimeMillis() - start;
        System.out.printf("SetRegion: band=%d indices=%d..%d frequency=%.3f..%.3f MHz (%d ms)%n", bandId, minIndex, maxIndex, freq.getMinFrequency(), freq.getMaxFrequency(), elapsed);

        return true;
    }

    /**
     * Set frequency by numeric range (MHz).
     * This will pick the best single band that covers the requested range (or the largest overlap).
     * If no single band contains any channel in the requested window, an IllegalArgumentException is thrown.
     */
    public boolean setFrequency(double minMHz, double maxMHz) throws ChafonDeviceException {
        final Frequency value = Frequency.get(minMHz, maxMHz);
        return setFrequency(value);
    }

    /**
     *
     * @param value - The output power of the reader. The range is 0 to 30 in
     *              dBm. The highest bit 7 is 1, which means that the
     *              power adjustment is not saved; the bit 0 means that the
     *              power is saved and saved.
     * @return
     * @throws ChafonDeviceException
     */
    public boolean setPower(final int value) throws ChafonDeviceException {
        if (value < MIN_POWER_DBM || value > MAX_POWER_DBM) {
            throw new IllegalArgumentException("Power must be between 0 and 33.");
        }

        final int res = reader.SetRfPower(value);
        final ChafonDeviceStatus status = ChafonDeviceStatus.of(res);
        if (!status.isSuccess()) {
            throw ChafonDeviceException.of(status);
        }

        return true;
    }

    public boolean setAntenna(final int antenna, final boolean enabled, final boolean persist) throws ChafonDeviceException {
        final int antennas = reader.GetAntennas();
        if (antenna < 1 || antenna > antennas) {
            throw new IllegalArgumentException(String.format("Antenna must be between 1 and %d.", antennas));
        }
        final int setOnce = persist ? 0 : 1; // 0 = save across power-off, 1 = do NOT save

        // Read current mask from device (replace with your SDK call)
        final Metadata info = getInformation();
        final int currentMask = info.getAntennaMask();

        final int bit = 1 << (antenna - 1);
        final int newMask = enabled ? (currentMask | bit) : (currentMask & ~bit);

        final int res = reader.SetAntenna(setOnce, newMask);
        final ChafonDeviceStatus status = ChafonDeviceStatus.of(res);
        if (!status.isSuccess()) {
            throw ChafonDeviceException.of(status);
        }
        return true;
    }

    public boolean setBeep(final boolean enabled) throws ChafonDeviceException {
        final int arg1 = enabled ? 0x01 : 0x00;
        final int res = reader.SetBeepNotification(arg1);
        final ChafonDeviceStatus status = ChafonDeviceStatus.of(res);
        if (!status.isSuccess()) {
            throw ChafonDeviceException.of(status);
        }

        return true;
    }

    public int[] getAntennaPower() throws ChafonDeviceException {
        final int antennas = reader.GetAntennas();
        final byte[] _power = new byte[antennas];
        final int res = reader.GetRfPowerByAnt(_power);
        final ChafonDeviceStatus status = ChafonDeviceStatus.of(res);
        if (!status.isSuccess()) {
            throw ChafonDeviceException.of(status);
        }

        final int[] power = new int[_power.length];
        for (int i = 0; i < power.length; i++) {
            power[i] = Byte.toUnsignedInt(_power[i]);
        }

        return power;
    }

    public boolean setAntennaPower(final int[] power) throws ChafonDeviceException {
        if (power == null) {
            throw new IllegalArgumentException("Antenna power must not be null.");
        }

        final int antennas = reader.GetAntennas();
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

        final int res = reader.SetRfPowerByAnt(array);
        final ChafonDeviceStatus status = ChafonDeviceStatus.of(res);
        if (!status.isSuccess()) {
            throw ChafonDeviceException.of(status);
        }

        return true;
    }

    @Data
    @AllArgsConstructor
    @ToString
    public static class Metadata {

        private final String version;
        private final int power;
        private final int[] powerPerAntenna;
        private final int band;
        private final int maxIndex;
        private final int minIndex;
        private final boolean beep;
        private final int antennaMask;
        private final int[] antennas;
        private final String serial;

    }

}
