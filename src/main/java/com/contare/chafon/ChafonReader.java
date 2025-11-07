package com.contare.chafon;

import com.rfid.BaseReader;
import com.rfid.ReaderParameter;
import com.rfid.TagCallback;
import com.rfid.Utils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

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
    private BaseReader reader = null;
    private ReaderParameter params = new ReaderParameter();
    private volatile boolean mWorking = true;
    private volatile Thread mThread = null;
    private byte[] pOUcharIDList = new byte[25600];
    private volatile int NoCardCount = 0;
    private TagCallback callback;
    public boolean isConnect = false;

    private String ip = "192.168.0.250";
    private int port = 27011;
    private int antennas = 4;
    private int logswitch = 0;

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
        this.logswitch = verbose ? 1 : 0;
        this.params.SetAddress((byte) -1);
        this.params.SetScanTime(10);
        this.params.SetSession(1);
        this.params.SetQValue(4);
        this.params.SetTidPtr(0);
        this.params.SetTidLen(6);
        this.params.SetAntenna(1);
        this.params.SetReadType(0);
        this.params.SetReadMem(3);
        this.params.SetReadPtr(0);
        this.params.SetReadLength(6);
        this.params.SetPassword("00000000");
        this.reader = new BaseReader(ip, antennas);
        this.isConnect = false;
    }

    public int GetAntennas() {
        return this.antennas;
    }

    public int Connect() {
        if (this.isConnect) {
            return 48;
        } else {
            int result = this.reader.Connect(this.ip, this.port, this.logswitch);
            if (result == 0) {
                this.isConnect = true;
            }

            return result;
        }
    }

    public void DisConnect() {
        if (this.isConnect) {
            this.mWorking = false;
            this.reader.DisConnect();
            this.isConnect = false;
        }

    }

    public void SetInventoryParameter(final ReaderParameter param) {
        this.params = param;
    }

    public ReaderParameter GetInventoryParameter() {
        return this.params;
    }

    public int GetUHFInformation(byte[] Version, byte[] Power, byte[] band, byte[] MaxFre, byte[] MinFre, byte[] BeepEn, int[] Ant) {
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
            this.params.SetAddress(ComAddr[0]);
            this.params.SetAntenna(Ant[0] & 255);
            byte[] Pro = new byte[1];
            Pro[0] = 0;
        }

        return result;
    }

    public int SetRfPower(int Power) {
        return this.reader.SetRfPower(this.params.GetAddress(), (byte) Power);
    }

    public int SetRegion(int band, int maxfre, int minfre) {
        return this.reader.SetRegion(this.params.GetAddress(), band, maxfre, minfre);
    }

    public int SetAntenna(int SetOnce, int AntCfg) {
        int result = 0;
        if (this.antennas > 4) {
            byte AntCfg1 = (byte) (AntCfg >> 8);
            byte AntCfg2 = (byte) (AntCfg & 255);
            result = this.reader.SetAntennaMultiplexing(this.params.GetAddress(), (byte) SetOnce, AntCfg1, AntCfg2);
            if (result == 0) {
                this.params.SetAntenna(AntCfg);
            }
        } else {
            if (SetOnce == 1) {
                AntCfg |= 128;
            }

            result = this.reader.SetAntennaMultiplexing(this.params.GetAddress(), (byte) AntCfg);
            if (result == 0) {
                this.params.SetAntenna(AntCfg);
            }
        }

        return result;
    }

    public int SetBeepNotification(int BeepEn) {
        return this.reader.SetBeepNotification(this.params.GetAddress(), (byte) BeepEn);
    }

    public int SetRfPowerByAnt(byte[] Power) {
        return Power.length != this.antennas ? 255 : this.reader.SetRfPowerByAnt(this.params.GetAddress(), Power);
    }

    public int GetRfPowerByAnt(byte[] Power) {
        return Power.length != this.antennas ? 255 : this.reader.GetRfPowerByAnt(this.params.GetAddress(), Power);
    }

    public int ConfigDRM(byte[] DRM) {
        return this.reader.ConfigDRM(this.params.GetAddress(), DRM);
    }

    public int SetRelay(int RelayTime) {
        return this.reader.SetRelay(this.params.GetAddress(), (byte) RelayTime);
    }

    public int SetGPIO(int GPIO) {
        return this.reader.SetGPIO(this.params.GetAddress(), (byte) GPIO);
    }

    public int GetGPIOStatus(byte[] OutputPin) {
        return this.reader.GetGPIOStatus(this.params.GetAddress(), OutputPin);
    }

    public String GetSerialNo() {
        byte[] btArr = new byte[4];
        int result = this.reader.GetSerialNo(this.params.GetAddress(), btArr);
        if (result == 0) {
            String temp = Utils.bytesToHexString(btArr, 0, btArr.length);
            return temp;
        } else {
            return null;
        }
    }

    public int MeasureReturnLoss(byte[] TestFreq, byte Ant, byte[] ReturnLoss) {
        return this.reader.MeasureReturnLoss(this.params.GetAddress(), TestFreq, Ant, ReturnLoss);
    }

    public int SetWritePower(byte WritePower) {
        return this.reader.SetWritePower(this.params.GetAddress(), WritePower);
    }

    public int GetWritePower(byte[] WritePower) {
        return this.reader.GetWritePower(this.params.GetAddress(), WritePower);
    }

    public int SetCheckAnt(byte CheckAnt) {
        return this.reader.SetCheckAnt(this.params.GetAddress(), CheckAnt);
    }

    public int SetCfgParameter(byte opt, byte cfgNum, byte[] data, int len) {
        return this.reader.SetCfgParameter(this.params.GetAddress(), opt, cfgNum, data, len);
    }

    public int GetCfgParameter(byte cfgNo, byte[] cfgData, int[] len) {
        return this.reader.GetCfgParameter(this.params.GetAddress(), cfgNo, cfgData, len);
    }

    public int SelectCmdWithCarrier(byte Antenna, byte Session, byte SelAction, byte MaskMem, byte[] MaskAdr, byte MaskLen, byte[] MaskData, byte Truncate, byte CarrierTime) {
        return this.reader.SelectCmdWithCarrier(this.params.GetAddress(), Antenna, Session, SelAction, MaskMem, MaskAdr, MaskLen, MaskData, Truncate, CarrierTime);
    }

    public String ReadDataByEPC(String EPCStr, byte Mem, byte WordPtr, byte Num, String PasswordStr) {
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
            int result = this.reader.ReadData_G2(this.params.GetAddress(), ENum, EPC, Mem, WordPtr, Num, Password, MaskMem, MaskAdr, MaskLen, MaskData, Data, Errorcode);
            return result == 0 ? Utils.bytesToHexString(Data, 0, Data.length) : null;
        } else {
            return null;
        }
    }

    public String ReadDataByTID(String TIDStr, byte Mem, byte WordPtr, byte Num, String PasswordStr) {
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
                int result = this.reader.ReadData_G2(this.params.GetAddress(), ENum, EPC, Mem, WordPtr, Num, Password, MaskMem, MaskAdr, MaskLen, MaskData, Data, Errorcode);
                return result == 0 ? Utils.bytesToHexString(Data, 0, Data.length) : null;
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    public int WriteDataByEPC(String EPCStr, byte Mem, byte WordPtr, String PasswordStr, String wdata) {
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
                return this.reader.WriteData_G2(this.params.GetAddress(), WNum, ENum, EPC, Mem, WordPtr, data, Password, MaskMem, MaskAdr, MaskLen, MaskData, Errorcode);
            } else {
                return 255;
            }
        } else {
            return 255;
        }
    }

    public int WriteDataByTID(String TIDStr, byte Mem, byte WordPtr, String PasswordStr, String wdata) {
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
                    byte[] Errorcode = new byte[1];
                    return this.reader.WriteData_G2(this.params.GetAddress(), WNum, ENum, EPC, Mem, WordPtr, data, Password, MaskMem, MaskAdr, MaskLen, MaskData, Errorcode);
                } else {
                    return 255;
                }
            } else {
                return 255;
            }
        } else {
            return 255;
        }
    }

    public int WriteEPC(String EPCStr, String PasswordStr) {
        if (EPCStr != null && EPCStr.length() % 4 == 0) {
            if (PasswordStr != null && PasswordStr.length() == 8) {
                byte[] Password = Utils.hexStringToBytes(PasswordStr);
                byte WNum = (byte) (EPCStr.length() / 4);
                byte[] Errorcode = new byte[1];
                byte[] data = Utils.hexStringToBytes(EPCStr);
                return this.reader.WriteEPC_G2(this.params.GetAddress(), WNum, Password, data, Errorcode);
            } else {
                return 255;
            }
        } else {
            return 255;
        }
    }

    public int WriteEPCByTID(String TIDStr, String EPCStr, String PasswordStr) {
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
                    return this.reader.WriteData_G2(this.params.GetAddress(), WNum, ENum, EPC, Mem, WordPtr, data, Password, MaskMem, MaskAdr, MaskLen, MaskData, Errorcode);
                } else {
                    return 255;
                }
            } else {
                return 255;
            }
        } else {
            return 255;
        }
    }

    public int Lock(String EPCStr, byte select, byte setprotect, String PasswordStr) {
        if (EPCStr != null && EPCStr.length() % 4 != 0) {
            return 255;
        } else if (PasswordStr != null && PasswordStr.length() == 8) {
            byte ENum = 0;
            if (EPCStr != null) {
                ENum = (byte) (EPCStr.length() / 4);
            }

            byte[] EPC = Utils.hexStringToBytes(EPCStr);
            byte[] Password = Utils.hexStringToBytes(PasswordStr);
            byte[] Errorcode = new byte[1];
            return this.reader.Lock_G2(this.params.GetAddress(), ENum, EPC, select, setprotect, Password, Errorcode);
        } else {
            return 255;
        }
    }

    public int Kill(String EPCStr, String PasswordStr) {
        if (EPCStr != null && EPCStr.length() % 4 != 0) {
            return 255;
        } else if (PasswordStr != null && PasswordStr.length() == 8) {
            byte ENum = 0;
            if (EPCStr != null) {
                ENum = (byte) (EPCStr.length() / 4);
            }

            byte[] EPC = Utils.hexStringToBytes(EPCStr);
            byte[] Password = Utils.hexStringToBytes(PasswordStr);
            byte[] Errorcode = new byte[1];
            return this.reader.Kill_G2(this.params.GetAddress(), ENum, EPC, Password, Errorcode);
        } else {
            return 255;
        }
    }

    public int ReadData_G2(byte ENum, byte[] EPC, byte Mem, byte WordPtr, byte Num, byte[] Password, byte MaskMem, byte[] MaskAdr, byte MaskLen, byte[] MaskData, byte[] Data, byte[] Errorcode) {
        return this.reader.ReadData_G2(this.params.GetAddress(), ENum, EPC, Mem, WordPtr, Num, Password, MaskMem, MaskAdr, MaskLen, MaskData, Data, Errorcode);
    }

    public int WriteData_G2(byte WNum, byte ENum, byte[] EPC, byte Mem, byte WordPtr, byte[] Writedata, byte[] Password, byte MaskMem, byte[] MaskAdr, byte MaskLen, byte[] MaskData, byte[] Errorcode) {
        return this.reader.WriteData_G2(this.params.GetAddress(), WNum, ENum, EPC, Mem, WordPtr, Writedata, Password, MaskMem, MaskAdr, MaskLen, MaskData, Errorcode);
    }

    public void SetCallBack(TagCallback mycallback) {
        this.callback = mycallback;
        this.reader.SetCallBack(mycallback);
    }

    // Toggle interval in milliseconds (default 1s). Can be changed with setToggleIntervalMs(...)
    private long toggleIntervalMs = 1000;
    // state
    private boolean currentIsA = true;

    public int StartRead() {
        if (mThread == null) {

            mWorking = true;

            final List<Frequency> frequencies = List.of(Frequency.BRAZIL_A, Frequency.BRAZIL_B);
            final AtomicInteger freqIndex = new AtomicInteger(0);
            final AtomicBoolean settingFrequency = new AtomicBoolean(false);


            mThread = new Thread(() -> {
                long lastToggleTime = System.currentTimeMillis();
                byte Target = 0;
                int index = 0;

                while (mWorking) {
                    int antenna = 1 << index;

                    if ((params.GetAntenna() & antenna) == antenna) {
                        byte Ant = (byte) (index | 128);
                        int[] pOUcharTagNum = new int[1];
                        int[] pListLen = new int[1];
                        pOUcharTagNum[0] = pListLen[0] = 0;
                        if (params.GetSession() == 0 || params.GetSession() == 1) {
                            Target = 0;
                            NoCardCount = 0;
                        }

                        int result = 48;
                        if (params.GetReadType() == 0) {
                            byte TIDlen = 0;
                            reader.Inventory_G2(params.GetAddress(), (byte) params.GetQValue(), (byte) params.GetSession(), (byte) params.GetTidPtr(), TIDlen, Target, Ant, (byte) params.GetScanTime(), pOUcharIDList, pOUcharTagNum, pListLen);
                        } else if (params.GetReadType() == 1) {
                            byte TIDlen = (byte) params.GetTidLen();
                            if (TIDlen == 0) {
                                TIDlen = 6;
                            }

                            reader.Inventory_G2(params.GetAddress(), (byte) params.GetQValue(), (byte) params.GetSession(), (byte) params.GetTidPtr(), TIDlen, Target, Ant, (byte) params.GetScanTime(), pOUcharIDList, pOUcharTagNum, pListLen);
                        } else if (params.GetReadType() == 2) {
                            byte MaskMem = 0;
                            byte[] MaskAdr = new byte[2];
                            byte MaskLen = 0;
                            byte[] MaskData = new byte[96];
                            byte MaskFlag = 0;
                            byte[] ReadAddr = new byte[]{ (byte) (params.GetReadPtr() >> 8), (byte) (params.GetReadPtr() & 255) };
                            byte[] Password = Utils.hexStringToBytes(params.GetPassword());
                            reader.Inventory_Mix(params.GetAddress(), (byte) params.GetQValue(), (byte) params.GetSession(), MaskMem, MaskAdr, MaskLen, MaskData, MaskFlag, (byte) params.GetReadMem(), ReadAddr, (byte) params.GetReadLength(), Password, Target, Ant, (byte) params.GetScanTime(), pOUcharIDList, pOUcharTagNum, pListLen);
                        }

                        if (pOUcharTagNum[0] == 0) {
                            if (params.GetSession() > 1) {
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
                    long now = System.currentTimeMillis();
                    long elapsed = now - lastToggleTime;
                    if (elapsed >= toggleIntervalMs) {
                        if (settingFrequency.compareAndSet(false, true)) {
                            try {
                                // pre-increment index: first loop will move 0 -> 1 as requested
                                int nextIndex = freqIndex.updateAndGet(i -> {
                                    int ni = i + 1;
                                    return (ni >= frequencies.size()) ? 0 : ni;
                                });

                                Frequency nextFreq = frequencies.get(nextIndex);

                                long callStart = System.currentTimeMillis();
                                boolean updated = setFrequency(nextFreq);
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

                mThread = null;
                LockSupport.unpark(mainThread);
                if (callback != null) {
                    callback.StopReadCallback();
                }

            });
            this.mThread.start();
            return 0;
        } else {
            return 255;
        }
    }

    public void StopRead() {
        if (this.mThread != null) {
            this.reader.StopImmediately(this.params.GetAddress());
            this.mWorking = false;
            LockSupport.park();
        }

    }

    public int Inventory_G2(byte QValue, byte Session, byte AdrTID, byte LenTID, byte Target, byte Ant, byte Scantime, byte[] pOUcharIDList, int[] pOUcharTagNum, int[] pListLen) {
        return this.reader.Inventory_G2(this.params.GetAddress(), QValue, Session, AdrTID, LenTID, Target, Ant, Scantime, pOUcharIDList, pOUcharTagNum, pListLen);
    }

    public int Inventory_Mix(byte QValue, byte Session, byte MaskMem, byte[] MaskAdr, byte MaskLen, byte[] MaskData, byte MaskFlag, byte ReadMem, byte[] ReadAdr, byte ReadLen, byte[] Pwd, byte Target, byte Ant, byte Scantime, byte[] pOUcharIDList, int[] pOUcharTagNum, int[] pListLen) {
        return this.reader.Inventory_Mix(this.params.GetAddress(), QValue, Session, MaskMem, MaskAdr, MaskLen, MaskData, MaskFlag, ReadMem, ReadAdr, ReadLen, Pwd, Target, Ant, Scantime, pOUcharIDList, pOUcharTagNum, pListLen);
    }

    // API
    public ChafonRfidDevice.Metadata getInformation() throws ChafonDeviceException {
        final byte[] _version = new byte[2]; // bit 1 = version number, bit 2 = subversion number
        final byte[] _power = new byte[1]; // output power (range 0 ~ 30 dbm)
        final byte[] _band = new byte[1]; // spectrum band (1 - Chinese 1, 2 - US, 3 - Korean, 4 - EU, 8 - Chinese 2, 0 - All)
        final byte[] _maxFrequency = new byte[1]; // current maximum frequency of the reader
        final byte[] _minFrequency = new byte[1]; // current minimum frequency of the reader
        final byte[] _beep = new byte[1]; // buzzer beeps information
        final int[] _ant = new int[1]; // each bit represent an antenna number, such as 0x00009, the binary is 00000000 00001001, indicating antenna 1 to 4

        int res = this.GetUHFInformation(_version, _power, _band, _maxFrequency, _minFrequency, _beep, _ant);
        final ChafonDeviceStatus status = ChafonDeviceStatus.of(res);
        if (!status.isSuccess()) {
            throw ChafonDeviceException.of(status);
        }

        final int major = Byte.toUnsignedInt(_version[0]);
        final int minor = Byte.toUnsignedInt(_version[1]);
        final String version = String.format("%d.%d", major, minor);

        final int powerByte = Byte.toUnsignedInt(_power[0]); // could be 0..33 or 255 if called after 'SetRfPowerByAnt'
        // System.out.printf("power mask = 0x%08X, binary=%s%n", _power[0], Integer.toBinaryString(powerByte));
        final boolean powerPerAntennaMode = (powerByte == 0xFF);
        final int power = powerPerAntennaMode ? -1 : powerByte;
        final int[] powerPerAntenna = getAntennaPower();

        final int band = Byte.toUnsignedInt(_band[0]);
        final int maxIndex = Byte.toUnsignedInt(_maxFrequency[0]);
        final int minIndex = Byte.toUnsignedInt(_minFrequency[0]);

        final int beep = Byte.toUnsignedInt(_beep[0]);
        // System.out.printf("beep mask = 0x%08X, binary=%s%n", _beep[0], Integer.toBinaryString(beep));

        final int mask = _ant[0];
        // System.out.printf("ant mask = 0x%08X, binary=%s%n", mask, Integer.toBinaryString(mask));

        final int[] enabled = new int[antennas];
        final int limit = Math.max(0, Math.min(antennas, Integer.SIZE));
        for (int i = 0; i < limit; i++) {
            if ((mask & (1 << i)) != 0) {
                enabled[i] = i + 1; // antenna numbering from 1
            }
        }

        final String serialNo = this.GetSerialNo();

        return new ChafonRfidDevice.Metadata(version, power, powerPerAntenna, band, maxIndex, minIndex, (beep == 1), mask, enabled, serialNo);
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

        int res = this.SetRegion(bandId, maxIndex, minIndex);
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

        final int res = this.SetRfPower(value);
        final ChafonDeviceStatus status = ChafonDeviceStatus.of(res);
        if (!status.isSuccess()) {
            throw ChafonDeviceException.of(status);
        }

        return true;
    }

    public boolean setAntenna(final int antenna, final boolean enabled, final boolean persist) throws ChafonDeviceException {
        if (antenna < 1 || antenna > antennas) {
            throw new IllegalArgumentException(String.format("Antenna must be between 1 and %d.", antennas));
        }
        final int setOnce = persist ? 0 : 1; // 0 = save across power-off, 1 = do NOT save

        // Read current mask from device (replace with your SDK call)
        final ChafonRfidDevice.Metadata info = getInformation();
        final int currentMask = info.getAntennaMask();

        final int bit = 1 << (antenna - 1);
        final int newMask = enabled ? (currentMask | bit) : (currentMask & ~bit);

        final int res = this.SetAntenna(setOnce, newMask);
        final ChafonDeviceStatus status = ChafonDeviceStatus.of(res);
        if (!status.isSuccess()) {
            throw ChafonDeviceException.of(status);
        }
        return true;
    }

    public boolean setBeep(final boolean enabled) throws ChafonDeviceException {
        final int arg1 = enabled ? 0x01 : 0x00;
        final int res = this.SetBeepNotification(arg1);
        final ChafonDeviceStatus status = ChafonDeviceStatus.of(res);
        if (!status.isSuccess()) {
            throw ChafonDeviceException.of(status);
        }

        return true;
    }

    public int[] getAntennaPower() throws ChafonDeviceException {
        final byte[] _power = new byte[antennas];
        final int res = this.GetRfPowerByAnt(_power);
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

        final int res = this.SetRfPowerByAnt(array);
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
