package com.contare;

import com.contare.chafon.ChafonRfidDevice;
import com.contare.chafon.Frequency;
import com.contare.chafon.UHFInformation;
import com.contare.config.Config;
import com.contare.config.ConfigLoader;
import com.contare.core.exceptions.RfidDeviceException;
import com.contare.core.objects.Options;
import org.jboss.logging.Logger;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

public class Main {

    private static final Logger logger = Logger.getLogger(Main.class);

    public static void main(final String[] args) {
        try {
            // MUST run this before any logging occurs (including libraries).
            SLF4JBridgeHandler.removeHandlersForRootLogger();
            SLF4JBridgeHandler.install();
            final Config cfg = ConfigLoader.load(args);
            logger.infof("Loaded configurations:\n%s", ConfigLoader.toString(cfg));
            run(cfg.getDevice());
        } catch (Exception e) {
            logger.errorf(e, "Failed to load configurations.");
            System.exit(1);
        }
    }

    private static void run(final Config.Device cfg) {
        final int interval = Math.max(cfg.getFrequency().getInterval(), 0);
        final List<Frequency> frequencies = Arrays.stream(cfg.getFrequency().getSpecs())
            .map(Config.FrequencySpec::toFrequency)
            .collect(Collectors.toList());

        final CountDownLatch latch = new CountDownLatch(1);

        final Options opts = new Options(cfg.getAddress(), cfg.getIp(), cfg.getPort(), cfg.getAntennas(), cfg.isVerbose(), frequencies, interval);

        try (final ChafonRfidDevice device = new ChafonRfidDevice()) {
            final boolean initialized = device.init(opts);
            if (initialized) {
                logger.debugf("Device connected opts: %s", opts);
            }

            final UHFInformation info = device.GetUHFInformation();
            logger.debugf("Device info: %s", info);


            boolean started = device.start();
            if (started) {
                logger.debugf("Device started");
            } else {
                logger.warnf("Device did not started");
            }

            latch.await();
        } catch (RfidDeviceException e) {
            logger.errorf(e, "Device error.");
        } catch (IOException e) {
            logger.errorf(e, "IO error.");
        } catch (InterruptedException e) {
            logger.errorf(e, "Interrupted while waiting for device.");
        }
    }

}
