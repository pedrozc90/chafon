package com.contare;

import com.contare.chafon.ChafonUHFDevice;
import com.contare.chafon.Frequency;
import com.contare.chafon.UHFInformation;
import com.contare.config.Config;
import com.contare.config.ConfigLoader;
import com.contare.core.exceptions.RfidDeviceException;
import com.contare.core.objects.Options;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class Main {

    private static final Logger logger = Logger.getLogger(Main.class);

    public static void main(final String[] args) {
        try {
            final Config cfg = ConfigLoader.load(args);
            logger.info("Loaded configurations");

            final Config.Device params = cfg.getDevice();
            logger.infof("Verbose: %b", params.isVerbose());
            logger.infof("Device IP: %s", params.getIp());
            logger.infof("Device Port: %d", params.getPort());

            final int antennas = Math.max(params.getAntennas().getNum(), 4);
            logger.infof("Device Antennas: %d", antennas);

            final Frequency frequency = Optional.ofNullable(params.getFrequency().toFrequency()).orElse(Frequency.BRAZIL);

            final Map<Integer, Boolean> map = params.getAntennas().getMap();
            for (int ant = 1; ant <= antennas; ant++) {
                if (!map.containsKey(ant)) {
                    map.put(ant, false);
                }
            }

            final Options opts = new Options(params.getAddress(), params.getIp(), params.getPort(), antennas, params.isVerbose());

            try (final ChafonUHFDevice device = new ChafonUHFDevice()) {
                final boolean initialized = device.init(opts);
                if (initialized) {
                    logger.debugf("Device connected opts: %s", opts);
                }

                final boolean fUpdated = device.SetFrequency(frequency);
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
                    logger.infof("Device started");
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
            }

        } catch (Exception e) {
            logger.errorf(e, "Failed to load configurations.");
            System.exit(1);
        }
    }

}
