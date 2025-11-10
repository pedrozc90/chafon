package com.contare;

import com.contare.chafon.ChafonRfidDevice;
import com.contare.chafon.Frequency;
import com.contare.chafon.UHFInformation;
import com.contare.config.Config;
import com.contare.config.ConfigLoader;
import com.contare.core.exceptions.RfidDeviceException;
import com.contare.core.objects.Options;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

public class Main {

    public static void main(final String[] args) {
        try {
            final Config cfg = ConfigLoader.load(args);
            System.out.println("Loaded configurations:\n" + ConfigLoader.toString(cfg));

            run(cfg.getDevice());
        } catch (Exception e) {
            System.err.println("Failed to load configurations. Reason: " + e.getMessage());
            e.printStackTrace();
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
                System.out.println("Device connected opts: " + opts);
            }

            final UHFInformation info = device.GetUHFInformation();
            System.out.println("Device info: " + info);


            boolean started = device.start();
            System.out.println("Device started: " + started);

            latch.await();
        } catch (RfidDeviceException e) {
            final String stackTrace = ExceptionUtils.getStackTrace(e);
            System.err.println("Device error. Reason: " + stackTrace);
        } catch (IOException e) {
            final String stackTrace = ExceptionUtils.getStackTrace(e);
            System.err.println("IO error. Reason " + stackTrace);
        } catch (InterruptedException e) {
            final String stackTrace = ExceptionUtils.getStackTrace(e);
            System.err.println("Interrupted while waiting for device. Reason: " + stackTrace);
        }
    }

}
