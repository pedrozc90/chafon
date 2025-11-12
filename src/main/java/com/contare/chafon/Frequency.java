package com.contare.chafon;

import lombok.Getter;
import lombok.ToString;

import java.util.Locale;
import java.util.Set;

/**
 * Chafon frequency object
 * <p>
 * Fn = fStart + (N * fStep)
 */
@Getter
@ToString
public class Frequency {

    // DOCUMENTATION
    public static final Frequency ALL = new Frequency(0, 840, 2, 0, 60);                  // 840.00 ~ 960.00 MHz
    public static final Frequency CHINESE_2 = new Frequency(1, 920.125, 0.25, 0, 19);     // 920.125 ~ 924.875 MHz
    public static final Frequency US = new Frequency(2, 902.75, 0.50, 0, 49);             // 902.75 ~ 927.25 MHz
    public static final Frequency KOREAN = new Frequency(3, 917.10, 0.20, 0, 31);         // 917.10 ~ 923.30 MHz
    public static final Frequency EU_LOWER = new Frequency(4, 865.10, 0.20, 0, 14);       // 865.10 ~ 867.90 MHz
    public static final Frequency UKRAINE = new Frequency(6, 868.00, 0.10, 0, 6);         // 868.00 ~ 868.60 MHz
    public static final Frequency CHINESE_1 = new Frequency(8, 840.125, 0.25, 0, 19);     // 840.125 ~ 844.875 MHz
    public static final Frequency EU_3 = new Frequency(9, 865.70, 0.60, 0, 3);            // 865.70 ~ 867.50 MHz
    public static final Frequency US_3 = new Frequency(12, 902.00, 0.50, 0, 52);          // 902.00 ~ 928.00 MHz
    public static final Frequency HONG_KONG = new Frequency(16, 920.25, 0.50, 0, 9);      // 920.25 ~ 924.75 MHz
    public static final Frequency TAIWAN = new Frequency(17, 920.75, 0.50, 0, 13);        // 920.75 ~ 927.25 MHz
    public static final Frequency ETSI_UPPER = new Frequency(18, 916.30, 1.20, 0, 2);     // 916.30 ~ 918.70 MHz (ETSI = European Telecommunications Standards Institute)
    public static final Frequency MALAYSIA = new Frequency(19, 919.25, 0.50, 0, 7);       // 919.25 ~ 922.75 MHz
    public static final Frequency BRAZIL = new Frequency(21, 902.75, 0.50, 0, 34);        // 0 - 9 = 902.75 ~ 907.25 MHz; 10 - 34 910.25 ~ 927.25 MHz
    public static final Frequency THAILAND = new Frequency(22, 920.25, 0.50, 0, 9);       // 920.25 ~ 924.75 MHz
    public static final Frequency SINGAPORE = new Frequency(23, 920.25, 0.50, 0, 9);      // 920.25 ~ 924.75 MHz
    public static final Frequency AUSTRALIA = new Frequency(24, 920.25, 0.50, 0, 9);      // 920.25 ~ 924.75 MHz
    public static final Frequency INDIA = new Frequency(25, 865.10, 0.60, 0, 3);          // 865.10 ~ 866.90 MHz
    public static final Frequency URUGUAY = new Frequency(26, 916.25, 0.50, 0, 22);       // 916.25 ~ 927.25 MHz
    public static final Frequency VIETNAM = new Frequency(27, 918.75, 0.50, 0, 7);        // 918.75 ~ 922.25 MHz
    public static final Frequency ISRAEL = new Frequency(28, 916.25, 0.50, 0, 0);         // 916.25 ~ 916.25 MHz
    public static final Frequency INDONESIA = new Frequency(29, 917.25, 0.50, 0, 3);      // 0 = 917.25 ~ 917.25 MHz; 0 - 3 = 920.25 ~ 921.75 MHz
    public static final Frequency NEW_ZEALAND = new Frequency(30, 922.25, 0.50, 0, 9);    // 922.25 ~ 926.75 MHz
    public static final Frequency JAPAN_2 = new Frequency(31, 916.80, 1.20, 0, 3);        // 916.80 ~ 920.40 MHz
    public static final Frequency PERU = new Frequency(32, 916.25, 0.50, 0, 22);          // 916.25 ~ 927.25 MHz
    public static final Frequency RUSSIA = new Frequency(33, 916.20, 1.20, 0, 3);         // 916.20 ~ 919.80 MHz
    public static final Frequency SOUTH_AFRICA = new Frequency(34, 915.60, 0.20, 0, 16);  // 915.60 ~ 918.80 MHz
    public static final Frequency PHILIPPINES = new Frequency(35, 918.25, 0.50, 0, 3);    // 918.25 ~ 919.75 MHz

    // CUSTOM
    public static final Frequency BRAZIL_1 = new Frequency(2, 902.75, 0.5, 0, 9); // 902 ~ 907.5 MHz
    public static final Frequency BRAZIL_2 = new Frequency(2, 902.75, 0.5, 25, 49); // 915 ~ 927.75 MHz

    private static final Set<Frequency> _set = Set.of(
        CHINESE_2,
        US,
        KOREAN,
        EU_LOWER,
        UKRAINE,
        CHINESE_1,
        EU_3,
        US_3,
        HONG_KONG,
        TAIWAN,
        ETSI_UPPER,
        MALAYSIA,
        BRAZIL,
        THAILAND,
        SINGAPORE,
        AUSTRALIA,
        INDIA,
        URUGUAY,
        VIETNAM,
        ISRAEL,
        INDONESIA,
        NEW_ZEALAND,
        JAPAN_2,
        PERU,
        RUSSIA,
        SOUTH_AFRICA,
        PHILIPPINES
    );

    private final int band;             // frequency band (1 .. ?), each band represents a frequency function like Fn = fStart + (N * fStep) where N in [minIndex, maxIndex].
    private final double fStartMHz;     // frequency start (MHz)
    private final double stepMHz;       // frequency step (MHz)
    private final int minIndex;         // minimum index (inclusive)
    private final int maxIndex;         // maximum index (inclusive)

    public Frequency(int band, double fStartMHz, double stepMHz, int minIndex, int maxIndex) {
        this.band = band;
        this.fStartMHz = fStartMHz;
        this.stepMHz = stepMHz;
        this.minIndex = minIndex;
        this.maxIndex = maxIndex;
    }

    public double getMinFrequency() {
        return frequencyForIndex(fStartMHz, stepMHz, minIndex);
    }

    public double getMaxFrequency() {
        return frequencyForIndex(fStartMHz, stepMHz, maxIndex);
    }

    /**
     * Compute inclusive index range [nMin, nMax] for a requested frequency interval.
     * Returns null if there is no supported channel in this band that falls into the interval.
     */
    public int[] indicesForRange(double minMHz, double maxMHz) {
        // raw indices
        int nMin = (int) Math.ceil((minMHz - fStartMHz) / stepMHz);
        int nMax = (int) Math.floor((maxMHz - fStartMHz) / stepMHz);

        // clamp to allowed index range
        if (nMin < 0) nMin = 0;
        if (nMax > maxIndex) nMax = maxIndex;

        if (nMin > nMax) return null;
        return new int[]{ nMin, nMax };
    }

    /**
     * center frequency (MHz) for index n
     */
    public double frequencyForIndex(final double fStart, final double fStep, final int n) {
        return fStart + (n * fStep);
    }

    /**
     * Find the best single-band Selection that covers the requested frequency window [minMHz, maxMHz].
     * Heuristic: choose the band that yields the largest number of supported channels that overlap the requested range.
     * <p>
     * Throws IllegalArgumentException if no single band contains any channel inside the requested range.
     */
    public static Frequency get(double minMHz, double maxMHz) {
        if (minMHz > maxMHz) throw new IllegalArgumentException("minMHz > maxMHz");

        Frequency match = null;
        int bestCount = 0;
        int minIndex = 0, maxIndex = 0;

        for (Frequency row : _set) {
            int[] idx = row.indicesForRange(minMHz, maxMHz);
            if (idx == null) continue;
            int count = idx[1] - idx[0] + 1;
            if (count > bestCount) {
                bestCount = count;
                match = row;
                minIndex = idx[0];
                maxIndex = idx[1];
            }
        }

        if (match == null) {
            throw new IllegalArgumentException(String.format(Locale.ROOT, "No single band supports any channel inside %.3f..%.3f MHz", minMHz, maxMHz));
        }

        return new Frequency(match.band, match.fStartMHz, match.stepMHz, minIndex, maxIndex);
    }

    public static Frequency get(final int band, final int minIndex, final int maxIndex) {
        Frequency ref = null;
        for (Frequency row : _set) {
            if (row.band == band) {
                ref = row;
                if (minIndex < row.minIndex) {
                    throw new IllegalArgumentException(String.format(Locale.ROOT, "minIndex %d is outside the range of the band %d", minIndex, band));
                }
                if (maxIndex > row.maxIndex) {
                    throw new IllegalArgumentException(String.format(Locale.ROOT, "maxIndex %d is outside the range of the band %d", maxIndex, band));
                }
                if (minIndex > maxIndex) {
                    throw new IllegalArgumentException(String.format(Locale.ROOT, "minIndex %d is greater than maxIndex %d", minIndex, maxIndex));
                }
                break;
            }
        }
        if (ref == null) {
            throw new IllegalArgumentException(String.format(Locale.ROOT, "No single band supports any channel inside band %d", band));
        }
        return new Frequency(band, ref.fStartMHz, ref.stepMHz, minIndex, maxIndex);
    }

}
