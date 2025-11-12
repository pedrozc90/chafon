package com.contare;

import com.contare.chafon.ChafonRfidDevice;
import com.contare.chafon.ChafonUHFDevice;
import com.contare.chafon.Frequency;
import com.contare.chafon.UHFInformation;
import com.contare.config.Config;
import com.contare.config.ConfigLoader;
import com.contare.core.exceptions.RfidDeviceException;
import com.contare.core.objects.Options;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class Main2 {

    private static final Logger logger = Logger.getLogger(Main2.class);

    public static void main(final String[] args) {
        try {
            final Config.Device cfg = loadConfigurations(args);
            run(cfg);
        } catch (Exception e) {
            logger.errorf(e, "Failed to load configurations.");
            System.exit(1);
        }
    }

    private static Config.Device loadConfigurations(String[] args) throws Exception {
        final Config cfg = ConfigLoader.load(args);
        logger.info("Loaded configurations");
        return cfg.getDevice();
    }

    private static void run(final Config.Device cfg) {
        logger.infof("Verbose: %b", cfg.isVerbose());
        logger.infof("Device IP: %s", cfg.getIp());
        logger.infof("Device Port: %d", cfg.getPort());

        final int antennas = Math.max(cfg.getAntennas().getNum(), 4);
        logger.infof("Device Antennas: %d", antennas);


        final int interval = Math.max(cfg.getFrequency().getInterval(), 0);
        final List<Frequency> frequencies = Arrays.stream(cfg.getFrequency().getSpecs())
            .map(Config.FrequencySpec::toFrequency)
            .collect(Collectors.toList());

        final Map<Integer, Boolean> map = cfg.getAntennas().getMap();
        for (int ant = 1; ant <= antennas; ant++) {
            if (!map.containsKey(ant)) {
                map.put(ant, false);
            }
        }

        final Options opts = new Options(cfg.getAddress(), cfg.getIp(), cfg.getPort(), antennas, cfg.isVerbose(), frequencies, interval);

        try (final ChafonUHFDevice device = new ChafonUHFDevice()) {
            final boolean initialized = device.init(opts);
            if (initialized) {
                logger.debugf("Device connected opts: %s", opts);
            }

            final boolean fUpdated = device.SetFrequency(Frequency.BRAZIL);
            if (fUpdated) {
                logger.debug("Device frequency has been updated");
            }

            map.forEach((index, enabled) -> {
                final boolean updated = device.SetAntennas(index, enabled);
                if (updated) {
                    logger.infof("Antenna %d %s", index, enabled ? "enabled" : "disabled");
                }
            });

            final UHFInformation info = device.GetUHFInformation();
            logger.debugf("Device info: %s", info);

            boolean started = device.start();
            if (started) {
                logger.debugf("Device started");
            } else {
                logger.warnf("Device did not started");
            }

            /*
             * Wait for Ctrl+C (SIGINT) or other shutdown to stop the application.
             * We use a CompletableFuture that we complete from a shutdown hook.
             * When the future completes the main thread continues, the try-with-resources
             * will close the device, and the application exits cleanly.
             */
            final CompletableFuture<Void> stopFuture = new CompletableFuture<>();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutdown requested (Ctrl+C). Stopping...");

                final Set<String> buffer = device.getBuffer();
                for (String s : buffer) {
                    logger.infof("EPC: %s", s);
                }

                // complete the future to unblock the main thread
                stopFuture.complete(null);

                // Note: avoid long-running work in shutdown hooks. The try-with-resources
                // will close the device after stopFuture completes; if you need to signal
                // the device to stop immediately you can call device.stop() here, but
                // calling close in the main thread is preferred to keep shutdown-hook short.
            }, "shutdown-hook"));

            // Block here until stopFuture is completed by the shutdown hook.
            stopFuture.join();
        } catch (RfidDeviceException e) {
            logger.errorf(e, "Device error.");
        } catch (IOException e) {
            logger.errorf(e, "IO error.");
//        } catch (InterruptedException e) {
//            logger.errorf(e, "Interrupted while waiting for device.");
        }
    }

}
