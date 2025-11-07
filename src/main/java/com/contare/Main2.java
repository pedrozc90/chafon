package com.contare;

import com.contare.chafon.ChafonRfidDevice;
import com.contare.chafon.ChafonRfidDevice2;
import com.contare.chafon.ToggleFrequencyController;
import com.contare.core.exceptions.RfidDeviceException;
import com.contare.core.objects.Options;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main2 {

    public static void main(final String[] args) {
        final CountDownLatch latch = new CountDownLatch(1);

        final Options opts = new Options("2C-AC-44-04-97-01", "192.168.1.200", 2022, 4, false);

        try (final ChafonRfidDevice2 device = new ChafonRfidDevice2()) {
            final boolean initialized = device.init(opts);
            if (initialized) {
                System.out.println("Device initialized opts: " + opts);
            }

            final boolean connected = device.connect();
            if (connected) {
                System.out.println("Device connected");
            }

            final ChafonRfidDevice2.Metadata info = device.getInformation();
            System.out.println("Device info: " + info);

            device.setAntenna(1, true, true);
            // device.setAntenna(4, false, true);

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