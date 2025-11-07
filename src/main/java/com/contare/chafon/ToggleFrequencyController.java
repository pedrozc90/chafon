package com.contare.chafon;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ToggleFrequencyController {

    private final ChafonRfidDevice device;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean useA = new AtomicBoolean(true);
    private ScheduledFuture<?> scheduledTask;

    // interval in milliseconds
    private final long intervalMs;

    public ToggleFrequencyController(ChafonRfidDevice device, long intervalMs) {
        this.device = device;
        this.intervalMs = intervalMs;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "freq-toggle");
            t.setDaemon(true);
            return t;
        });
    }

    public synchronized void start() {
        if (scheduledTask != null && !scheduledTask.isDone()) return;

        Runnable toggle = () -> {
            // pick next frequency
            Frequency next = useA.getAndSet(!useA.get()) ? Frequency.BRAZIL_A : Frequency.BRAZIL_B;
            try {
                // synchronize on device to avoid concurrent SDK calls from other threads
                synchronized (device) {
                    device.setFrequency(next);
                }
            } catch (ChafonDeviceException e) {
                // Log and decide whether to stop toggling or retry.
                System.err.printf("Failed to set frequency %s: %s%n", next, e.getMessage());
            } catch (Exception e) {
                System.err.printf("Unexpected error while toggling frequency: %s%n", e.getMessage());
            }
        };

        // schedule at fixed rate to attempt a toggle every intervalMs
        scheduledTask = scheduler.scheduleAtFixedRate(toggle, 0, intervalMs, TimeUnit.MILLISECONDS);
    }

    public synchronized void stop() {
        if (scheduledTask != null) {
            scheduledTask.cancel(true);
            scheduledTask = null;
        }
        // optionally shut down scheduler if controller is no longer needed
        // scheduler.shutdownNow();
    }

    public void shutdown() {
        stop();
        scheduler.shutdownNow();
    }
}
