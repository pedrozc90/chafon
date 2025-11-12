package com.contare.chafon;

import com.contare.core.mappers.UHFInformationMapper;
import com.rfid.*;
import lombok.extern.slf4j.Slf4j;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

@Slf4j
public class ChafonReader {

    private static final Logger logger = Logger.getLogger(ChafonReader.class);

    private static final int MIN_POWER_DBM = 0;
    private static final int MAX_POWER_DBM = 33;
    private static final int DEFAULT_POWER_DBM = 0;

    Thread mainThread = Thread.currentThread();
    private volatile Thread mThread = null;
    private volatile boolean mWorking = true;
    private byte[] pOUcharIDList = new byte[25600];
    private volatile int NoCardCount = 0;
    private String sdkVersion = "1.0.0.1";

    private BaseReader reader = null;
    public boolean isConnect = false;
    private ReaderParameter param = new ReaderParameter();
    private TagCallback callback;

    private String ip = "192.168.0.250"; // device ip address, default is 192.168.0.250 or 192.168.1.200
    private int port = 27011; // device port number, default is 27011 or 2022
    private int antennas = 4; // device maximum number of antennas, default is 4, max is 40.
    private boolean verbose = false;

    /**
     *
     * @param ip       - device ip address, default is 192.168.0.250
     * @param port     - device port number, default is 27011
     * @param antennas - max antenna number, default is 4, max is 40.
     * @param verbose  - verbose mode, default is false.
     */
    public ChafonReader(final String ip, final int port, final int antennas, final boolean verbose) {
        Objects.requireNonNull(ip, "ip must not be null.");

        this.ip = ip;
        this.port = port;
        this.antennas = antennas;
        this.verbose = verbose;

        this.param.SetAddress((byte) -1);
        this.param.SetScanTime(10);
        this.param.SetSession(1);
        this.param.SetQValue(4);
        this.param.SetTidPtr(0);
        this.param.SetTidLen(6);
        this.param.SetAntenna(1);
        this.param.SetReadType(0);
        this.param.SetReadMem(3);
        this.param.SetReadPtr(0);
        this.param.SetReadLength(6);
        this.param.SetPassword("00000000");

        this.reader = new BaseReader(ip, antennas);

        this.isConnect = false;
    }

    public String GetSdkVersion() {
        return this.sdkVersion;
    }

    public int Connect() {
        if (isConnect) {
            return 0x30; // 0x30 = 48
        }

        final int logswitch = verbose ? 1 : 0; // 0 - close, 1 = open
        int result = reader.Connect(ip, port, logswitch);
        if (result == 0) {
            isConnect = true;
        }

        return result;
    }

    public void Disconnect() {
        if (isConnect) {
            mWorking = false;
            reader.DisConnect();
            isConnect = false;
        }
    }

    /**
     * Set the query parameter used when inventory is enabled.
     *
     * @param param - query parameter
     */
    public void SetInventoryParameter(final ReaderParameter param) {
        this.param = param;
    }

    /**
     * Get the query parameter used in inventory.
     *
     * @return ReaderParameter object
     */
    public ReaderParameter GetInventoryParameter() {
        return this.param;
    }

    /**
     * Get basic information of the UFH module.
     *
     * @param Version - Output 2 bytes, reader version information. The first byte is the version number, and the second byte is the handle of the subversion number.
     * @param Power   - Output 1 byte, the output power of the reader. The range is 0 to 30 in dBm
     * @param band    - Output 1 byte, spectrum band. 1 = Chinese band-2, 2 = US band, 3 = Korean band, 4 = EU band, 8 - Chinese band-1
     * @param MaxFre  - Output 1 byte, indicating the current maximum frequency of the reader
     * @param MinFre  - Output 1 byte, indicating the current minimum frequency of the reader.
     * @param BeepEn  - Output 1 byte, buzzer beeps information.
     * @param Ant     - Output 1 word, antenna configuration information. Each bit represents an antenna number, such as 0x0009, the binary is 00000000 00001001, indicating antenna 1 and
     *                antenna 4.
     * @return 0x00 if successful, else return error code.
     */
    public int GetUHFInformation(final byte[] Version, final byte[] Power, final byte[] band, final byte[] MaxFre, final byte[] MinFre, final byte[] BeepEn, final int[] Ant) {
        byte[] ReaderType = new byte[1];
        byte[] TrType = new byte[1];
        byte[] ScanTime = new byte[1];
        byte[] OutputRep = new byte[1];
        byte[] CheckAnt = new byte[1];
        byte[] ComAddr = new byte[]{-1};
        byte[] AntCfg0 = new byte[1];
        byte[] AntCfg1 = new byte[1];
        int result = reader.GetReaderInformation(ComAddr, Version, ReaderType, TrType, band, MaxFre, MinFre, Power, ScanTime, AntCfg0, BeepEn, AntCfg1, CheckAnt);
        if (result == 0x00) {
            Ant[0] = ((AntCfg1[0] & 255) << 8) + (AntCfg0[0] & 255);
            param.SetAddress(ComAddr[0]);
            param.SetAntenna(Ant[0] & 255);
            byte[] var17 = new byte[1];
        }

        return result;
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

            final int result = this.GetUHFInformation(version, power, band, maxFrequency, minFrequency, beep, ant);
            if (result != 0x00) {
                throw ChafonDeviceException.of(result);
            }

            final int[] powerPerAntenna = GetRfPowerByAnt();
            final String serialNo = GetSerialNo();

            return UHFInformationMapper.parse(version, power, band, maxFrequency, minFrequency, beep, ant, powerPerAntenna, antennas, serialNo);
        } catch (ChafonDeviceException e) {
            logger.error("Error getting UHF information.", e);
            return null;
        }
    }

    /**
     * Set the reader power. Set all antennas to the same power.
     *
     * @param power - The output power of the reader. The range is 0 to 30 in dBm.
     *              The highest bit 7 is 1, which means that the power adjustment is not saved;
     *              the bit 0 means that the power is saved and saved.
     * @return 0x00 if success, else return error code.
     */
    private int SetRfPower(final int power) {
        return reader.SetRfPower(param.GetAddress(), (byte) power);
    }

    public boolean SetPower(final int value) {
        try {
            if (value < MIN_POWER_DBM || value > MAX_POWER_DBM) {
                throw ChafonDeviceException.of("Power must be between 0 and 33.");
            }
            final int result = this.SetRfPower(value);
            if (result != 0x00) {
                throw ChafonDeviceException.of(result);
            }
            return true;
        } catch (ChafonDeviceException e) {
            logger.error("Error setting power.", e);
            return false;
        }
    }

    /**
     * Set the working frequency band of the reader.
     *
     * @param band   - 1 byte, spectrum band.
     *               1 = Chinese band 2
     *               2 = US band
     *               3 = Korean band
     *               4 = EU band
     *               8 = Chinese band 1
     * @param maxfre - indicates the current maximum frequency of the reader's operation.
     * @param minfre - indicates the current minimum frequency at which the current reader works.
     * @return 0x00 if successful, else return error code.
     */
    public int SetRegion(final int band, final int maxfre, final int minfre) {
        return reader.SetRegion(param.GetAddress(), band, maxfre, minfre);
    }

    /**
     * Set the working frequency band of the reader.
     *
     * @param opt    - byte, 0 = save, 1 = not save
     * @param band   - byte, frequency band
     * @param maxfre - byte, maximum frequency point
     * @param minfre - byte, minimum frequency point
     * @return 0x00 if successful, else return error code.
     */
    public int ExtSetRegion(int opt, int band, int maxfre, int minfre) {
        return this.reader.ExtSetRegion(this.param.GetAddress(), opt, band, maxfre, minfre);
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

            final int result = this.ExtSetRegion(opt, bandId, maxIndex, minIndex);
            if (result != 0x00) {
                throw ChafonDeviceException.of(result);
            }

            // Log what we actually set
            final long elapsed = System.currentTimeMillis() - start;
            logger.debugf("SetRegion: band = %d, indices = %d .. %d, frequency=%.3f ~ %.3f MHz (%d ms)", bandId, minIndex, maxIndex, value.getMinFrequency(), value.getMaxFrequency(), elapsed);

            return true;
        } catch (ChafonDeviceException e) {
            logger.errorf(e, "Error setting frequency. (%d ms)", System.currentTimeMillis() - start);
            return false;
        }
    }

    /**
     * Set the effective working antenna of the reader.
     *
     * @param SetOnce - 1: Indicates that it will not be saved when power off;
     *                0: Indicates power-off save
     * @param AntCfg  - Valid antenna number, each bit represents an antenna number,
     *                such as 0x0009, binary is 00000000 00001001, indicating antenna 1 and antenna 4.
     * @return 0x00 if successful, else return error code.
     */
    public int SetAntenna(final int SetOnce, int AntCfg) {
        int result = 0;
        if (antennas > 4) {
            byte AntCfg1 = (byte) (AntCfg >> 8);
            byte AntCfg2 = (byte) (AntCfg & 255);
            result = reader.SetAntennaMultiplexing(param.GetAddress(), (byte) SetOnce, AntCfg1, AntCfg2);
            if (result == 0) {
                param.SetAntenna(AntCfg);
            }
        } else {
            if (SetOnce == 1) {
                AntCfg |= 128;
            }

            result = reader.SetAntennaMultiplexing(param.GetAddress(), (byte) AntCfg);
            if (result == 0) {
                param.SetAntenna(AntCfg);
            }
        }

        return result;
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

            final int result = this.SetAntenna(setOnce, antMask);
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

    public boolean SetAntenna(final int pos, final boolean enabled) {
        return SetAntenna(pos, enabled, true);
    }

    public int GetAntennaMask() {
        final UHFInformation info = GetUHFInformation();
        return (info != null) ? info.getAntennaMask() : 255;
    }

    /**
     * Set the buzzer switch.
     *
     * @param BeepEn - 0x00 = disable beep; 0x01 - enable beep
     * @return 0x00 if success, else return error code.
     */
    private int SetBeepNotification(final int BeepEn) {
        return this.reader.SetBeepNotification(this.param.GetAddress(), (byte) BeepEn);
    }

    /**
     * Enable beep sound notification when the device read a tag.
     *
     * @param enable - true to enable beep sound notification, false to disable beep sound notification.
     * @return true if beep sound notification changed.
     */
    public boolean SetBeep(final boolean enable) {
        final long start = System.currentTimeMillis();
        try {
            final int result = SetBeepNotification(enable ? 0x01 : 0x00);
            if (result != 0x00) {
                throw ChafonDeviceException.of(result);
            }
            logger.debugf("Beep %s (%d ms)", (enable ? "enabled" : "disabled"), System.currentTimeMillis() - start);
            return true;
        } catch (ChafonDeviceException e) {
            logger.errorf(e, "Error setting beep. (%d ms)", System.currentTimeMillis() - start);
            return false;
        }
    }

    /**
     * Set the power according to the antenna port
     *
     * @param Power - the power of each antenna port, the number of parameter bytes must be consistent with the number of antenna ports, the low byte represents the power of antenna port 1, and so on
     * @return 0x00 if success, else return error code.
     */
    public int SetRfPowerByAnt(final byte[] Power) {
        return (Power.length != antennas) ? 0xFF : reader.SetRfPowerByAnt(param.GetAddress(), Power);
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

            final int result = this.SetRfPowerByAnt(array);

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

    /**
     * Get the power according to the antenna port.
     *
     * @param Power - the power of each antenna port, the number of parameter bytes must be consistent with the number of antenna ports, the low byte represents the power of antenna port 1, and so on
     * @return
     */
    private int GetRfPowerByAnt(final byte[] Power) {
        return (Power.length != antennas) ? 255 : reader.GetRfPowerByAnt(param.GetAddress(), Power);
    }

    public int[] GetRfPowerByAnt() {
        final long start = System.currentTimeMillis();
        try {
            final byte[] _power = new byte[antennas];
            final int result = this.GetRfPowerByAnt(_power);
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

    public int ConfigDRM(final byte[] DRM) {
        return reader.ConfigDRM(param.GetAddress(), DRM);
    }

    /**
     * Used to set the pick-up time of the relay.
     *
     * @param RelayTime - Relay pick-up time, range 1 ~ 255. Unit 50 ms.
     * @return 0x00 if
     */
    public int SetRelay(final int RelayTime) {
        return reader.SetRelay(param.GetAddress(), (byte) RelayTime);
    }

    /**
     * Set the state of the GPIO port of the reader.
     *
     * @param value - GPIO port (Out1-Out2 pin) output status.
     *              Bit0-Bit1 control the Out1-Out2 pins respectively, and Bit2-Bit7 are reserved.
     * @return 0x00 if successful, else return error code.
     */
    public int SetGPIO(final int value) {
        return reader.SetGPIO(param.GetAddress(), (byte) value);
    }

    /**
     * Read the GPIO port status of the reader.
     *
     * @param OutputPin - 1 byte, GPIO port output status.
     *                  Bit0 represents the pin status of IN1, Bit4-Bit5 represent the status of Out1-Out2 respectively,
     *                  and other bits are reserved.
     * @return 0x00 if successful, else return error code.
     */
    private int GetGPIOStatus(final byte[] OutputPin) {
        return reader.GetGPIOStatus(param.GetAddress(), OutputPin);
    }

    public byte[] GetGPIOStatus() {
        try {
            byte[] output = new byte[1];
            int result = this.GetGPIOStatus(output);
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
        try {
            byte[] btArr = new byte[4];
            int result = reader.GetSerialNo(param.GetAddress(), btArr);
            if (result != 0x00) {
                throw ChafonDeviceException.of(result);
            }
            return Utils.bytesToHexString(btArr, 0, btArr.length);
        } catch (ChafonDeviceException e) {
            logger.error("Error getting serial number.", e);
            return null;
        }
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
        return reader.MeasureReturnLoss(param.GetAddress(), TestFreq, Ant, ReturnLoss);
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

    /**
     * Command is used to set the power used when writing tags.
     *
     * @param WritePower - Write power parameter, the highest bit 7 enable flag, 0-not enabled; 1-enabled.
     *                   Bit0~bit6 represent the write power value, the range is 0~33, and the unit is dbm.
     * @return 0x00 if successful, else returns an error code.
     */
    public int SetWritePower(final byte WritePower) {
        return reader.SetWritePower(param.GetAddress(), WritePower);
    }

    public boolean SetWritePower(final int value, final boolean enabled) {
        try {
            if (value < MIN_POWER_DBM || value > MAX_POWER_DBM) {
                throw new IllegalArgumentException("Power must be between " + MIN_POWER_DBM + " and " + MAX_POWER_DBM + " but received " + value);
            }
            // Bits 0..6 = power, bit7 = enabled flag
            final int packed = (value & 0x7F) | (enabled ? 0x80 : 0x00);
            final byte writePowerByte = (byte) (packed & 0xFF);
            final int result = this.SetWritePower((byte) 0);
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
     * Command used to read the power used when writing tags.
     *
     * @param WritePower - 1 byte, write power parameter, the highest bit 7 enable flag, 0-not enabled; 1-enabled.
     *                   Bit0~bit6 represent the write power value, the range is 0~33, and the unit is dbm.
     * @return 0x00 if success, else return an error code.
     */
    public int GetWritePower(final byte[] WritePower) {
        return reader.GetWritePower(param.GetAddress(), WritePower);
    }

    public int GetWritePower() {
        try {
            final byte[] WritePower = new byte[1];
            final int result = this.GetWritePower(WritePower);
            if (result != 0x00) {
                throw ChafonDeviceException.of(result);
            }

            return 0x00;
        } catch (ChafonDeviceException e) {
            logger.error("Error getting write power.", e);
            return 0;
        }
    }

    /**
     * This command is used to set whether to enable the antenna detection function.
     *
     * @param CheckAnt - antenna detection switch, 0 = off, 1 = on
     * @return 0x00 if successful, else return error code.
     */
    public int SetCheckAnt(final byte CheckAnt) {
        return reader.SetCheckAnt(param.GetAddress(), CheckAnt);
    }

    public boolean SetCheckAnt(final boolean enable) {
        try {
            final int value = enable ? 1 : 0;
            final int result = this.SetCheckAnt((byte) value);
            if (result != 0x00) {
                throw ChafonDeviceException.of(result);
            }
            return true;
        } catch (ChafonDeviceException e) {
            logger.error("Error getting check ant.", e);
            return false;
        }
    }

    public int SetCfgParameter(final byte opt, final byte cfgNum, final byte[] data, final int len) {
        return reader.SetCfgParameter(param.GetAddress(), opt, cfgNum, data, len);
    }

    public int GetCfgParameter(final byte cfgNo, final byte[] cfgData, final int[] len) {
        return reader.GetCfgParameter(param.GetAddress(), cfgNo, cfgData, len);
    }

    public int SelectCmdWithCarrier(final byte Antenna, final byte Session, final byte SelAction, final byte MaskMem, final byte[] MaskAdr, final byte MaskLen, final byte[] MaskData, final byte Truncate, final byte CarrierTime) {
        return reader.SelectCmdWithCarrier(param.GetAddress(), Antenna, Session, SelAction, MaskMem, MaskAdr, MaskLen, MaskData, Truncate, CarrierTime);
    }

    public String ReadDataByEPC(final String EPCStr, final byte Mem, final byte WordPtr, final byte Num, final String PasswordStr) {
        if (EPCStr != null && EPCStr.length() % 4 != 0) {
            return null;
        } else if (PasswordStr != null && PasswordStr.length() == 8) {
            byte[] Password = Utils.hexStringToBytes(PasswordStr);
            byte ENum = 0;
            if (EPCStr != null) {
                ENum = (byte) (EPCStr.length() / 4);
            }

            byte[] EPC = Utils.hexStringToBytes(EPCStr);
            byte MaskMem = 0;
            byte[] MaskAdr = new byte[2];
            byte MaskLen = 0;
            byte[] MaskData = new byte[12];
            byte MaskFlag = 0;
            byte[] Data = new byte[Num * 2];
            byte[] Errorcode = new byte[1];
            int result = this.reader.ReadData_G2(this.param.GetAddress(), ENum, EPC, Mem, WordPtr, Num, Password, MaskMem, MaskAdr, MaskLen, MaskData, Data, Errorcode);
            return result == 0 ? Utils.bytesToHexString(Data, 0, Data.length) : null;
        } else {
            return null;
        }
    }

    public String ReadDataByTID(final String TIDStr, final byte Mem, final byte WordPtr, final byte Num, final String PasswordStr) {
        if (TIDStr != null && TIDStr.length() % 4 == 0) {
            if (PasswordStr != null && PasswordStr.length() == 8) {
                byte[] Password = Utils.hexStringToBytes(PasswordStr);
                byte ENum = -1;
                byte[] EPC = new byte[12];
                byte[] TID = Utils.hexStringToBytes(TIDStr);
                byte MaskMem = 2;
                byte[] MaskAdr = new byte[2];
                MaskAdr[0] = MaskAdr[1] = 0;
                byte MaskLen = (byte) (TIDStr.length() * 4);
                byte[] MaskData = new byte[TIDStr.length()];
                System.arraycopy(TID, 0, MaskData, 0, TID.length);
                byte[] Data = new byte[Num * 2];
                byte[] Errorcode = new byte[1];
                int result = this.reader.ReadData_G2(this.param.GetAddress(), ENum, EPC, Mem, WordPtr, Num, Password, MaskMem, MaskAdr, MaskLen, MaskData, Data, Errorcode);
                return result == 0 ? Utils.bytesToHexString(Data, 0, Data.length) : null;
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    public int WriteDataByEPC(final String EPCStr, final byte Mem, final byte WordPtr, final String PasswordStr, final String wdata) {
        if (EPCStr != null && EPCStr.length() % 4 != 0) {
            return 255;
        } else if (wdata != null && wdata.length() % 4 == 0) {
            if (PasswordStr != null && PasswordStr.length() == 8) {
                byte[] Password = Utils.hexStringToBytes(PasswordStr);
                byte ENum = 0;
                if (EPCStr != null) {
                    ENum = (byte) (EPCStr.length() / 4);
                }

                byte WNum = (byte) (wdata.length() / 4);
                byte[] EPC = Utils.hexStringToBytes(EPCStr);
                byte[] data = Utils.hexStringToBytes(wdata);
                byte MaskMem = 0;
                byte[] MaskAdr = new byte[2];
                byte MaskLen = 0;
                byte[] MaskData = new byte[12];
                byte[] Errorcode = new byte[1];
                return this.reader.WriteData_G2(this.param.GetAddress(), WNum, ENum, EPC, Mem, WordPtr, data, Password, MaskMem, MaskAdr, MaskLen, MaskData, Errorcode);
            } else {
                return 255;
            }
        } else {
            return 255;
        }
    }

    /**
     * Write data to each memory area through the TID mask.
     *
     * @param TIDStr      - the hexadecimal TID number of the tag.
     * @param Mem         - memory area to be write. 0 - password area, the first 2 words are the destruction password, the last 2 words are the access password. 1 - EPC area, 2 - TID area, 3 - User area.
     * @param WordPtr     - write start word address.
     * @param PasswordStr - tag hexadecimal access password (4 bytes)
     * @param wdata       - the hexadecimal string of the data to be written, the length must be an integer multiple of 4.
     * @return 0x00 if successful, error code.
     */
    public int WriteDataByTID(final String TIDStr, final byte Mem, final byte WordPtr, final String PasswordStr, final String wdata) {
        if (TIDStr != null && TIDStr.length() % 4 == 0) {
            if (wdata != null && wdata.length() % 4 == 0) {
                if (PasswordStr != null && PasswordStr.length() == 8) {
                    byte[] Password = Utils.hexStringToBytes(PasswordStr);
                    byte ENum = -1;
                    byte WNum = (byte) (wdata.length() / 4);
                    byte[] EPC = new byte[12];
                    byte[] data = Utils.hexStringToBytes(wdata);
                    byte[] TID = Utils.hexStringToBytes(TIDStr);
                    byte MaskMem = 2;
                    byte[] MaskAdr = new byte[2];
                    MaskAdr[0] = MaskAdr[1] = 0;
                    byte MaskLen = (byte) (TIDStr.length() * 4);
                    byte[] MaskData = new byte[TIDStr.length()];
                    System.arraycopy(TID, 0, MaskData, 0, TID.length);
                    byte MaskFlag = 0;
                    byte[] ErrorCode = new byte[1];
                    return reader.WriteData_G2(param.GetAddress(), WNum, ENum, EPC, Mem, WordPtr, data, Password, MaskMem, MaskAdr, MaskLen, MaskData, ErrorCode);
                } else {
                    return 0xFF;
                }
            } else {
                return 0xFF;
            }
        } else {
            return 0xFF;
        }
    }

    /**
     *
     * @param epc      - the hexadecimal EPC number of the tag.
     * @param password - tag hexadecimal access password (4 bytes)
     * @return 0x00 if successful, 0xFF if an error occurred.
     */
    public int WriteEPC(final String epc, final String password) {
        if (epc != null && epc.length() % 4 == 0) {
            if (password != null && password.length() == 8) {
                final byte[] passwordBytes = Utils.hexStringToBytes(password);
                final byte WNum = (byte) (epc.length() / 4);
                final byte[] errorCodeBytes = new byte[1];
                final byte[] dataBytes = Utils.hexStringToBytes(epc);
                return reader.WriteEPC_G2(param.GetAddress(), WNum, passwordBytes, dataBytes, errorCodeBytes);
            } else {
                return 0xFF;
            }
        } else {
            return 0xFF;
        }
    }

    public int WriteEPCByTID(final String TIDStr, final String EPCStr, final String PasswordStr) {
        if (TIDStr != null && TIDStr.length() % 4 == 0) {
            if (EPCStr != null && EPCStr.length() % 4 == 0) {
                if (PasswordStr != null && PasswordStr.length() == 8) {
                    byte[] Password = Utils.hexStringToBytes(PasswordStr);
                    byte ENum = -1;
                    byte WNum = (byte) (EPCStr.length() / 4);
                    byte[] EPC = new byte[12];
                    String PCStr = "";
                    switch (WNum) {
                        case 1:
                            PCStr = "0800";
                            break;
                        case 2:
                            PCStr = "1000";
                            break;
                        case 3:
                            PCStr = "1800";
                            break;
                        case 4:
                            PCStr = "2000";
                            break;
                        case 5:
                            PCStr = "2800";
                            break;
                        case 6:
                            PCStr = "3000";
                            break;
                        case 7:
                            PCStr = "3800";
                            break;
                        case 8:
                            PCStr = "4000";
                            break;
                        case 9:
                            PCStr = "4800";
                            break;
                        case 10:
                            PCStr = "5000";
                            break;
                        case 11:
                            PCStr = "5800";
                            break;
                        case 12:
                            PCStr = "6000";
                            break;
                        case 13:
                            PCStr = "6800";
                            break;
                        case 14:
                            PCStr = "7000";
                            break;
                        case 15:
                            PCStr = "7800";
                            break;
                        case 16:
                            PCStr = "8000";
                    }

                    String wdata = PCStr + EPCStr;
                    ++WNum;
                    byte[] data = Utils.hexStringToBytes(wdata);
                    byte[] TID = Utils.hexStringToBytes(TIDStr);
                    byte MaskMem = 2;
                    byte[] MaskAdr = new byte[2];
                    MaskAdr[0] = MaskAdr[1] = 0;
                    byte MaskLen = (byte) (TIDStr.length() * 4);
                    byte[] MaskData = new byte[TIDStr.length()];
                    System.arraycopy(TID, 0, MaskData, 0, TID.length);
                    byte MaskFlag = 0;
                    byte[] Errorcode = new byte[1];
                    byte Mem = 1;
                    byte WordPtr = 1;
                    return this.reader.WriteData_G2(this.param.GetAddress(), WNum, ENum, EPC, Mem, WordPtr, data, Password, MaskMem, MaskAdr, MaskLen, MaskData, Errorcode);
                } else {
                    return 0xFF;
                }
            } else {
                return 0xFF;
            }
        } else {
            return 0xFF;
        }
    }

    /**
     * Set the protection status of each area of the label.
     *
     * @param epc        - the hexadecimal EPC number of the tag.
     * @param select
     * @param setprotect
     * @param password   - tag hexadecimal access password (4 bytes)
     * @return
     */
    public int Lock(final String epc, final byte select, final byte setprotect, final String password) {
        if (epc != null && epc.length() % 4 != 0) {
            return 0xFF;
        } else if (password != null && password.length() == 8) {
            byte ENum = 0;
            if (epc != null) {
                ENum = (byte) (epc.length() / 4);
            }

            final byte[] epcBytes = Utils.hexStringToBytes(epc);
            final byte[] passwordBytes = Utils.hexStringToBytes(password);
            final byte[] errorBytes = new byte[1];
            return reader.Lock_G2(param.GetAddress(), ENum, epcBytes, select, setprotect, passwordBytes, errorBytes);
        } else {
            return 0xFF;
        }
    }

    /**
     * Command used to destroy the label. Once the tag is destroyed, the reader's commands are never processed again.
     *
     * @param epc      - the hexadecimal EPC number of the tag.
     * @param password - tag hexadecimal access password (4 bytes)
     * @return
     */
    public int Kill(final String epc, final String password) {
        if (epc != null && epc.length() % 4 != 0) {
            return 0xFF;
        } else if (password != null && password.length() == 8) {
            byte ENum = 0;
            if (epc != null) {
                ENum = (byte) (epc.length() / 4);
            }

            final byte[] pecBytes = Utils.hexStringToBytes(epc);
            final byte[] passwordBytes = Utils.hexStringToBytes(password);
            final byte[] errorBytes = new byte[1];
            return reader.Kill_G2(param.GetAddress(), ENum, pecBytes, passwordBytes, errorBytes);
        } else {
            return 0xFF;
        }
    }

    public int ReadData_G2(final byte ENum, final byte[] EPC, final byte Mem, final byte WordPtr, final byte Num, final byte[] Password, final byte MaskMem, final byte[] MaskAdr, final byte MaskLen, final byte[] MaskData, final byte[] Data, final byte[] ErrorCode) {
        return reader.ReadData_G2(param.GetAddress(), ENum, EPC, Mem, WordPtr, Num, Password, MaskMem, MaskAdr, MaskLen, MaskData, Data, ErrorCode);
    }

    public int WriteData_G2(final byte WNum, final byte ENum, final byte[] EPC, final byte Mem, final byte WordPtr, final byte[] WriteData, final byte[] Password, final byte MaskMem, final byte[] MaskAdr, final byte MaskLen, final byte[] MaskData, final byte[] ErrorCode) {
        return reader.WriteData_G2(param.GetAddress(), WNum, ENum, EPC, Mem, WordPtr, WriteData, Password, MaskMem, MaskAdr, MaskLen, MaskData, ErrorCode);
    }

    /**
     * Set the callback interface after the inventory is started, and the label data is returned through the callback interface.
     *
     * @param callback - tag's callback interface
     */
    public void SetCallBack(final TagCallback callback) {
        this.callback = callback;
        this.reader.SetCallBack(callback);
    }

    public void SetCallBack(final Consumer<ReadTag> onRead) {
        this.SetCallBack(new TagCallback() {
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

    /**
     * Start to read tags.
     *
     * @param frequencies - list of frequencies to swap during process.
     * @param intervalMs  - time between frequency swaps.
     * @return 0x00 if started, else 0xFF if already running.
     */
    public int StartRead(final List<Frequency> frequencies, final int intervalMs) {
        if (mThread != null) {
            return 0xFF;
        }

        mWorking = true;

        final boolean toggleEnabled = frequencies != null && frequencies.size() > 1;
        final AtomicInteger freqIndex = new AtomicInteger(0);
        final AtomicBoolean settingFrequency = new AtomicBoolean(false);

        mThread = new Thread(() -> {
            long lastToggleTime = System.currentTimeMillis();
            byte Target = 0;
            int index = 0;

            while (mWorking) {
                int antenna = 1 << index;
                if ((param.GetAntenna() & antenna) == antenna) {
                    byte Ant = (byte) (index | 128);
                    int[] pOUcharTagNum = new int[1];
                    int[] pListLen = new int[1];
                    pOUcharTagNum[0] = pListLen[0] = 0;
                    if (param.GetSession() == 0 || param.GetSession() == 1) {
                        Target = 0;
                        NoCardCount = 0;
                    }

                    int result = 0;
                    if (param.GetReadType() == 0) {
                        byte TIDlen = 0;
                        byte MaskMem = 0;
                        reader.Inventory_G2(param.GetAddress(), (byte) param.GetQValue(), (byte) param.GetSession(), (byte) param.GetTidPtr(), TIDlen, Target, Ant, (byte) param.GetScanTime(), pOUcharIDList, pOUcharTagNum, pListLen);
                    } else if (param.GetReadType() == 1) {
                        byte TIDlen = (byte) param.GetTidLen();
                        if (TIDlen == 0) {
                            TIDlen = 6;
                        }

                        reader.Inventory_G2(param.GetAddress(), (byte) param.GetQValue(), (byte) param.GetSession(), (byte) param.GetTidPtr(), TIDlen, Target, Ant, (byte) param.GetScanTime(), pOUcharIDList, pOUcharTagNum, pListLen);
                    } else if (param.GetReadType() == 2) {
                        byte MaskMem = 0;
                        byte[] MaskAdr = new byte[2];
                        byte MaskLen = 0;
                        byte[] MaskData = new byte[96];
                        byte MaskFlag = 0;
                        byte[] ReadAddr = new byte[]{ (byte) (param.GetReadPtr() >> 8), (byte) (param.GetReadPtr() & 255) };
                        byte[] Password = Utils.hexStringToBytes(param.GetPassword());
                        reader.Inventory_Mix(param.GetAddress(), (byte) param.GetQValue(), (byte) param.GetSession(), MaskMem, MaskAdr, MaskLen, MaskData, MaskFlag, (byte) param.GetReadMem(), ReadAddr, (byte) param.GetReadLength(), Password, Target, Ant, (byte) param.GetScanTime(), pOUcharIDList, pOUcharTagNum, pListLen);
                    }

                    if (pOUcharTagNum[0] == 0) {
                        if (param.GetSession() > 1) {
                            ChafonReader var10000 = ChafonReader.this;
                            var10000.NoCardCount = var10000.NoCardCount + 1;
                            int reTryTime = antennas;
                            if (NoCardCount > reTryTime && param.GetTarget() == 2) {
                                Target = (byte) (1 - Target);
                                NoCardCount = 0;
                            }
                        }
                    } else {
                        NoCardCount = 0;
                    }

                    try {
                        Thread.sleep(5L);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                ++index;
                if (antennas != 0) {
                    index %= antennas;
                } else {
                    index = 0;
                }

                // --- Toggle region if requested interval elapsed
                if (toggleEnabled) {
                    long now = System.currentTimeMillis();
                    long elapsed = now - lastToggleTime;
                    if (elapsed >= intervalMs) {
                        if (settingFrequency.compareAndSet(false, true)) {
                            try {
                                // pre-increment index: first loop will move 0 -> 1 as requested
                                int nextIndex = freqIndex.updateAndGet(i -> {
                                    int ni = i + 1;
                                    return (ni >= frequencies.size()) ? 0 : ni;
                                });

                                Frequency nextFreq = frequencies.get(nextIndex);

                                long callStart = System.currentTimeMillis();
                                boolean updated = this.SetFrequency(nextFreq);
                                long callEnd = System.currentTimeMillis();

                                logger.infof("Frequency changed (%b) to band = %d, indices = %d .. %d, frequency = %.3f ~ %.3f MHz (%d ms)",
                                    updated,
                                    nextFreq.getBand(),
                                    nextFreq.getMinIndex(),
                                    nextFreq.getMaxIndex(),
                                    nextFreq.getMinFrequency(),
                                    nextFreq.getMaxFrequency(),
                                    callEnd - callStart);

                                // only advance the timer after finishing setFrequency
                                lastToggleTime = System.currentTimeMillis();
                            } catch (Exception e) {
                                logger.error("Failed to change device frequency.", e);
                                lastToggleTime = System.currentTimeMillis(); // avoid tight retry loops
                            } finally {
                                settingFrequency.set(false);
                            }
                        } else {
                            logger.debug("Skipping toggle because setFrequency already in progress");
                        }
                    }
                }
            }

            if (callback != null) {
                callback.StopReadCallback();
            }

            mThread = null;
        });
        this.mThread.start();
        return 0x00;
    }

    /**
     * Stop to read tags.
     */
    public void StopRead() {
        if (mThread != null) {
            reader.StopImmediately(param.GetAddress());
            mWorking = false;
        }
    }

    /**
     * Check whether there is an electronic label that conforms to the agreement within the valid range.
     *
     * @param QValue        - 1 byte, 0~15: The initial Q value used when querying the EPC label,
     * @param Session       - 1 byte, the session value used when querying the EPC tag.
     *                      0x00: Session uses S0;
     *                      0x01: Session uses S1;
     *                      0x02: Session uses S2;
     *                      0x03: Session uses S3.
     *                      0xff: The reader automatically configures the session (only valid for EPC query)
     *                      It is recommended to select
     * @param AdrTID        - 1 byte, query the starting word address of the TID area.
     * @param LenTID        - 1 byte, query the number of data words in the TID area, the range is 1~15, 0 means EPC query
     * @param Target        - 1 byte, the Target value of the query tag. 0 – Target A; 1 – Target B.
     * @param Ant           - 1 byte, the antenna number to be queried this time.
     *                      0x80 – Antenna 1; 0x81 – Antenna 2;
     *                      0x82 – Antenna 3; 0x83 – Antenna 4;
     *                      0x84 – Antenna 5; 0x85 – Antenna 6;
     *                      0x86 – Antenna 7; 0x87 – Antenna 8;
     *                      0x88 – Antenna 9; 0x89 – Antenna 10;
     *                      0x8A – Antenna 11; 0x8B – Antenna 12;
     *                      0x8C – Antenna 13; 0x8D – Antenna 14;
     *                      0x8A – Antenna 15; 0x8B – Antenna 16;
     *                      The single-port reader is fixed at 0x80.
     * @param Scantime      - 1 byte, the maximum operation time for inventory.
     *                      The valid range of Scantime is 0 ~ 255, corresponding to (0 ~ 255)*100ms.
     *                      For Scantime = 0, operation time is not limited.
     * @param pOUcharIDList - The inquired tag data, the data block follows the format stated below:
     *                      EPC/TID length + EPC/TID No. + RSSI.
     *                      Data of multiple tags is formed by several identical data blocks in sequence
     * @param pOUcharTagNum - The total amount of tag inquired during the current inventory.
     * @param pListLen      - The total length of the received data stored in pEPCList.
     * @return
     */
    public int Inventory_G2(final byte QValue, final byte Session, final byte AdrTID, final byte LenTID, final byte Target, final byte Ant, final byte Scantime, final byte[] pOUcharIDList, final int[] pOUcharTagNum, final int[] pListLen) {
        return reader.Inventory_G2(param.GetAddress(), QValue, Session, AdrTID, LenTID, Target, Ant, Scantime, pOUcharIDList, pOUcharTagNum, pListLen);
    }

    public int Inventory_Mix(final byte QValue, final byte Session, final byte MaskMem, final byte[] MaskAdr, final byte MaskLen, final byte[] MaskData, final byte MaskFlag, final byte ReadMem, final byte[] ReadAdr, final byte ReadLen, final byte[] Pwd, final byte Target, final byte Ant, final byte Scantime, final byte[] pOUcharIDList, final int[] pOUcharTagNum, final int[] pListLen) {
        return reader.Inventory_Mix(param.GetAddress(), QValue, Session, MaskMem, MaskAdr, MaskLen, MaskData, MaskFlag, ReadMem, ReadAdr, ReadLen, Pwd, Target, Ant, Scantime, pOUcharIDList, pOUcharTagNum, pListLen);
    }

    public int SetExtProfile(final byte Opt, final int[] Profile) {
        return reader.SetExtProfile(param.GetAddress(), Opt, Profile);
    }

}
