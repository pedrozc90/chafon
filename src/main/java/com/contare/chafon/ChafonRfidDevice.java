package com.contare.chafon;

import com.contare.core.RfidDevice;
import com.contare.core.exceptions.RfidDeviceException;
import com.contare.core.mappers.TagMetadataMapper;
import com.contare.core.objects.Options;
import com.contare.core.objects.TagMetadata;
import com.rfid.ReadTag;
import com.rfid.ReaderParameter;
import com.rfid.TagCallback;

import java.io.IOException;
import java.util.List;

public class ChafonRfidDevice implements RfidDevice {

    private Options opts;
    private ChafonReader reader;

    @Override
    public boolean init(final Options opts) {
        this.opts = opts;

        reader = new ChafonReader(opts.ip, opts.port, opts.antennas, opts.verbose);

        reader.SetCallBack(new TagCallback() {
            @Override
            public void tagCallback(final ReadTag readTag) {
                final TagMetadata tag = TagMetadataMapper.toDto(readTag);
                System.out.println("Tag Received: " + tag);
            }

            @Override
            public void StopReadCallback() {
                System.out.println("Stop Read Callback");
            }
        });

        final int result = reader.Connect();

        return (result == 0x00);
    }

    @Override
    public boolean start() throws RfidDeviceException {
        System.out.println("Starting device");
        final boolean fUpdated = reader.SetFrequency(Frequency.BRAZIL_A);
        if (fUpdated) {
            System.out.println("Device frequency has been updated");
        }

        final boolean pUpdated = reader.SetPower(30);
        if (pUpdated) {
            System.out.println("Device power has been updated");
        }

        final boolean bUpdated = reader.SetBeep(true);
        if (bUpdated) {
            System.out.println("Device beep has been updated");
        }

        // final ReaderParameter params = reader.GetInventoryParameter();
        // params.SetScanTime(255);
        // reader.SetInventoryParameter(params);
        final List<Frequency> frequencies = List.of(Frequency.BRAZIL_A, Frequency.BRAZIL_B);

        final int result = reader.StartRead(frequencies, 1_000);
        if (result != 0x00) {
            throw ChafonDeviceException.of(ChafonDeviceStatus.of(result));
        }

        return true;
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

    // API
    public UHFInformation GetUHFInformation() {
        return reader.GetUHFInformation();
    }

}
