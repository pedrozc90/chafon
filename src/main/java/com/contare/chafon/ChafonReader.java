package com.contare.chafon;

import com.contare.core.mappers.UHFInformationMapper;
import com.rfid.BaseReader;
import com.rfid.ReaderParameter;
import com.rfid.TagCallback;
import com.rfid.Utils;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

public class ChafonReader {

    private static final int MIN_POWER_DBM = 0;
    private static final int MAX_POWER_DBM = 33;
    private static final int DEFAULT_POWER_DBM = 0;

    Thread mainThread = Thread.currentThread();
    private volatile Thread mThread = null;
    private volatile boolean mWorking = true;
    private byte[] pOUcharIDList = new byte[25600];
    private volatile int NoCardCount = 0;

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

    public void DisConnect() {
        if (isConnect) {
            mWorking = false;
            reader.DisConnect();
            isConnect = false;
        }
    }

    public void SetInventoryParameter(final ReaderParameter param) {
        this.param = param;
    }

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
        byte[] ComAddr = new byte[1];
        ComAddr[0] = -1;
        byte[] AntCfg0 = new byte[1];
        byte[] AntCfg1 = new byte[1];
        int result = this.reader.GetReaderInformation(ComAddr, Version, ReaderType, TrType, band, MaxFre, MinFre, Power, ScanTime, AntCfg0, BeepEn, AntCfg1, CheckAnt);
        if (result == 0) {
            Ant[0] = ((AntCfg1[0] & 255) << 8) + (AntCfg0[0] & 255);
            this.param.SetAddress(ComAddr[0]);
            this.param.SetAntenna(Ant[0] & 255);
            byte[] Pro = new byte[1];
            Pro[0] = 0;
        }

        return result;
    }

    public UHFInformation GetUHFInformation() {
        final byte[] version = new byte[2]; // bit 1 = version number, bit 2 = subversion number
        final byte[] power = new byte[1]; // output power (range 0 ~ 30 dbm)
        final byte[] band = new byte[1]; // spectrum band (1 - Chinese 1, 2 - US, 3 - Korean, 4 - EU, 8 - Chinese 2, 0 - All)
        final byte[] maxFrequency = new byte[1]; // current maximum frequency of the reader
        final byte[] minFrequency = new byte[1]; // current minimum frequency of the reader
        final byte[] beep = new byte[1]; // buzzer beeps information
        final int[] ant = new int[1]; // each bit represent an antenna number, such as 0x00009, the binary is 00000000 00001001, indicating antenna 1 to 4

        final int result = this.GetUHFInformation(version, power, band, maxFrequency, minFrequency, beep, ant);
        if (result != 0x00) {
            final ChafonDeviceStatus status = ChafonDeviceStatus.of(result);
            System.err.println("GetUHFInformation: " + status);
            return null;
        }

        final int[] powerPerAntenna = GetRfPowerByAnt();
        final String serialNo = GetSerialNo();

        return UHFInformationMapper.parse(version, power, band, maxFrequency, minFrequency, beep, ant, powerPerAntenna, antennas, serialNo);
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
        if (value < MIN_POWER_DBM || value > MAX_POWER_DBM) {
            throw new IllegalArgumentException("Power must be between 0 and 33.");
        }
        final int result = this.SetRfPower(port);
        if (verbose) {
            final ChafonDeviceStatus status = ChafonDeviceStatus.of(result);
            System.out.println("SetPower: " + status);
        }
        return (result == 0x00);
    }

    /**
     * Chinese band 2:  Fs = 920.125 + N * 0.25 (MHz) where N ∈ [0, 19].
     * US band:         Fs = 902.75 + N * 0.5 (MHz) where N ∈ [0,49].
     * Korean band:     Fs = 917.1 + N * 0.2 (MHz) where N ∈ [0, 31].
     * EU band:         Fs = 865.1 + N*0.2(MHz) where N ∈ [0, 14].
     * Ukraine band:    Fs = 868.0 + N*0.1(MHz) where N ∈ [0, 6].
     * Peru band:       Fs = 916.2 + N*0.9(MHz) where N ∈ [0, 11].
     * Chinese band 1:  Fs = 840.125 + N * 0.25 (MHz) where N ∈ [0, 19].
     * EU3 band:        Fs = 865.7 + N * 0.6(MHz) where N ∈ [0, 3].
     * US band 3:       Fs = 902 + N * 0.5 (MHz) where N ∈ [0,52].
     * Taiwan band:     Fs = 922.25 + N * 0.5 (MHz) where N ∈ [0, 11].
     *
     * @param band
     * @param maxfre
     * @param minfre
     * @return 0x00 if successful, else return error code.
     */
    private int SetRegion(final int band, final int maxfre, final int minfre) {
        return reader.SetRegion(param.GetAddress(), band, maxfre, minfre);
    }

    /**
     * Set frequency by preset name (e.g. "US", "BRAZIL_A", "BRAZIL_B", "EU", "CHINESE_2").
     */
    public boolean SetFrequency(final Frequency value) {
        final long start = System.currentTimeMillis();

        final int bandId = value.getBand();
        final int maxIndex = value.getMaxIndex();
        final int minIndex = value.getMinIndex();

        final int result = this.SetRegion(bandId, maxIndex, minIndex);

        // Log what we actually set
        final long elapsed = System.currentTimeMillis() - start;
        System.out.printf("SetRegion: band=%d indices=%d..%d frequency=%.3f..%.3f MHz (%d ms)%n", bandId, minIndex, maxIndex, value.getMinFrequency(), value.getMaxFrequency(), elapsed);

        return (result == 0x00);
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
    private int SetAntenna(final int SetOnce, int AntCfg) {
        int result = 0;
        if (this.antennas > 4) {
            byte AntCfg1 = (byte) (AntCfg >> 8);
            byte AntCfg2 = (byte) (AntCfg & 255);
            result = this.reader.SetAntennaMultiplexing(this.param.GetAddress(), (byte) SetOnce, AntCfg1, AntCfg2);
            if (result == 0) {
                this.param.SetAntenna(AntCfg);
            }
        } else {
            if (SetOnce == 1) {
                AntCfg |= 128;
            }

            result = this.reader.SetAntennaMultiplexing(this.param.GetAddress(), (byte) AntCfg);
            if (result == 0) {
                this.param.SetAntenna(AntCfg);
            }
        }

        return result;
    }

    public boolean SetAntenna(final int antenna, final boolean enabled, final boolean persist) throws ChafonDeviceException {
        if (antenna < 1 || antenna > antennas) {
            throw new IllegalArgumentException(String.format("Antenna must be between 1 and %d.", antennas));
        }
        final int setOnce = persist ? 0 : 1; // 0 = save across power-off, 1 = do NOT save

        // Read current mask from device (replace with your SDK call)
        final UHFInformation info = GetUHFInformation();
        final int currentMask = info.getAntennaMask();

        final int bit = 1 << (antenna - 1);
        final int newMask = enabled ? (currentMask | bit) : (currentMask & ~bit);

        final int result = this.SetAntenna(setOnce, newMask);
        return (result == 0x00);
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
     * Enable beep sound notification when device read a tag.
     *
     * @param enable - true to enable beep sound notification, false to disable beep sound notification.
     * @return true if beep sound notification changed.
     */
    public boolean SetBeep(final boolean enable) {
        final int result = SetBeepNotification(enable ? 0x01 : 0x00);
        if (verbose) {
            final ChafonDeviceStatus status = ChafonDeviceStatus.of(result);
            System.out.println("SetBeep: " + status);
        }
        return (result == 0x00);
    }

    /**
     * Set the power according to the antenna port
     *
     * @param Power - the power of each antenna port, the number of parameter bytes must be consistent with the number of antenna ports, the low byte represents the power of antenna port 1, and so on
     * @return 0x00 if success, else return error code.
     */
    public int SetRfPowerByAnt(final byte[] Power) {
        return (Power.length != antennas) ? 255 : reader.SetRfPowerByAnt(param.GetAddress(), Power);
    }

    public boolean SetRfPowerByAntenna(final int[] power) {
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

        return (result == 0x00);
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
        final byte[] _power = new byte[antennas];
        final int result = this.GetRfPowerByAnt(_power);
        if (result != 0x00) {
            return null;
        }

        final int[] power = new int[_power.length];
        for (int i = 0; i < power.length; i++) {
            power[i] = Byte.toUnsignedInt(_power[i]);
        }

        return power;
    }

    public int ConfigDRM(final byte[] DRM) {
        return reader.ConfigDRM(param.GetAddress(), DRM);
    }

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
        byte[] output = new byte[1];
        int result = this.GetGPIOStatus(output);
        if (result != 0x00) {
            return null;
        }
        return output;
    }

    public String GetSerialNo() {
        byte[] btArr = new byte[4];
        int result = reader.GetSerialNo(param.GetAddress(), btArr);
        if (result == 0x00) {
            return Utils.bytesToHexString(btArr, 0, btArr.length);
        } else {
            return null;
        }
    }

    public int MeasureReturnLoss(final byte[] TestFreq, final byte Ant, final byte[] ReturnLoss) {
        return reader.MeasureReturnLoss(param.GetAddress(), TestFreq, Ant, ReturnLoss);
    }

    public int SetWritePower(final byte WritePower) {
        return reader.SetWritePower(param.GetAddress(), WritePower);
    }

    public int GetWritePower(final byte[] WritePower) {
        return reader.GetWritePower(param.GetAddress(), WritePower);
    }

    public int SetCheckAnt(final byte CheckAnt) {
        return reader.SetCheckAnt(param.GetAddress(), CheckAnt);
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
                    byte MaskFlag = 1;
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
                    byte MaskFlag = 1;
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

    public void SetCallBack(final TagCallback callback) {
        this.callback = callback;
        this.reader.SetCallBack(callback);
    }

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

                    int result = 48;
                    if (param.GetReadType() == 0) {
                        byte TIDlen = 0;
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
                            if (NoCardCount > reTryTime) {
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

                                System.out.printf("[%s] Toggled frequency index=%d freq=%s (updated=%b) took=%d ms now=%d last=%d elapsed=%d%n",
                                    Instant.ofEpochMilli(System.currentTimeMillis()), nextIndex, nextFreq, updated, (callEnd - callStart), now, lastToggleTime, elapsed);

                                // only advance the timer after finishing setFrequency
                                lastToggleTime = System.currentTimeMillis();
                            } catch (Exception e) {
                                System.err.println("Failed to toggle SetRegion: " + e.getMessage());
                                lastToggleTime = System.currentTimeMillis(); // avoid tight retry loops
                            } finally {
                                settingFrequency.set(false);
                            }
                        } else {
                            System.out.println("Skipping toggle because setFrequency already in progress");
                        }
                    }
                }
            }

            mThread = null;
            LockSupport.unpark(mainThread);
            if (callback != null) {
                callback.StopReadCallback();
            }

        });
        this.mThread.start();
        return 0x00;
    }

    public void StopRead() {
        if (mThread != null) {
            reader.StopImmediately(param.GetAddress());
            mWorking = false;
            LockSupport.park();
        }
    }

    public int Inventory_G2(final byte QValue, final byte Session, final byte AdrTID, final byte LenTID, final byte Target, final byte Ant, final byte Scantime, final byte[] pOUcharIDList, final int[] pOUcharTagNum, final int[] pListLen) {
        return reader.Inventory_G2(param.GetAddress(), QValue, Session, AdrTID, LenTID, Target, Ant, Scantime, pOUcharIDList, pOUcharTagNum, pListLen);
    }

    public int Inventory_Mix(final byte QValue, final byte Session, final byte MaskMem, final byte[] MaskAdr, final byte MaskLen, final byte[] MaskData, final byte MaskFlag, final byte ReadMem, final byte[] ReadAdr, final byte ReadLen, final byte[] Pwd, final byte Target, final byte Ant, final byte Scantime, final byte[] pOUcharIDList, final int[] pOUcharTagNum, final int[] pListLen) {
        return reader.Inventory_Mix(param.GetAddress(), QValue, Session, MaskMem, MaskAdr, MaskLen, MaskData, MaskFlag, ReadMem, ReadAdr, ReadLen, Pwd, Target, Ant, Scantime, pOUcharIDList, pOUcharTagNum, pListLen);
    }

}
