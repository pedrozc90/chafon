package com.contare.chafon;

import com.contare.core.RfidDevice;
import com.contare.core.exceptions.RfidDeviceException;
import com.contare.core.mappers.TagMetadataMapper;
import com.contare.core.objects.Options;
import com.contare.core.objects.TagMetadata;
import com.rfid.ReadTag;
import com.rfid.TagCallback;
import org.jboss.logging.Logger;

import java.io.IOException;

public class ChafonRfidDevice implements RfidDevice {

    private static final Logger logger = Logger.getLogger(ChafonRfidDevice.class);

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
                logger.infof("Tag Received: %s", tag);
            }

            @Override
            public void StopReadCallback() {
                logger.info("Stop Read Callback");
            }
        });

        final int result = reader.Connect();

        return (result == 0x00);
    }

    @Override
    public boolean start() throws RfidDeviceException {
        logger.debug("Starting device");

        final boolean fUpdated = reader.SetFrequency(Frequency.BRAZIL_A);
        if (fUpdated) {
            logger.debug("Device frequency has been updated");
        }

        final boolean pUpdated = reader.SetPower(30);
        if (pUpdated) {
            logger.debug("Device power has been updated");
        }

        final boolean bUpdated = reader.SetBeep(true);
        if (bUpdated) {
            logger.debug("Device beep has been updated");
        }

        // final ReaderParameter params = reader.GetInventoryParameter();
        // params.SetScanTime(255);
        // reader.SetInventoryParameter(params);

        final int result = reader.StartRead(opts.frequencies, opts.interval);
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
                reader.Disconnect();
            }
        }
        final long elapsed = System.currentTimeMillis() - start;
        logger.debugf("Device closed (%d ms)", elapsed);
    }

    // API
    public UHFInformation GetUHFInformation() {
        return reader.GetUHFInformation();
    }

}
