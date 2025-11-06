package com.contare.chafon;

import lombok.Getter;
import lombok.ToString;

import java.util.Locale;
import java.util.Set;

@Getter
@ToString
public class Frequency {

    public static final Frequency CHINESE_2 = new Frequency(1, 920.125, 0.25, 0, 19);
    public static final Frequency US = new Frequency(2, 902.75, 0.5, 0, 49);
    public static final Frequency KOREAN = new Frequency(3, 917.1, 0.2, 0, 31);
    public static final Frequency EU = new Frequency(4, 865.1, 0.2, 0, 14);
    public static final Frequency CHINESE_1 = new Frequency(8, 840.125, 0.25, 0, 19);
    public static final Frequency ALL = new Frequency(0, 840, 2, 0, 60);

    public static final Frequency BRAZIL_A;
    public static final Frequency BRAZIL_B;

    private static final Set<Frequency> _set = Set.of(
        Frequency.US,
        Frequency.EU,
        Frequency.KOREAN,
        Frequency.CHINESE_1,
        Frequency.CHINESE_2
    );

    static {
        BRAZIL_A = Frequency.get(902, 907.5);
        BRAZIL_B = Frequency.get(915, 927.75);
    }

    private final int bandId;
    private final double fStartMHz;
    private final double stepMHz;
    private final int minIndex;
    private final int maxIndex;

    private Frequency(int bandId, double fStartMHz, double stepMHz, int minIndex, int maxIndex) {
        this.bandId = bandId;
        this.fStartMHz = fStartMHz;
        this.stepMHz = stepMHz;
        this.minIndex = minIndex;
        this.maxIndex = maxIndex;
    }

    public double getMinFrequency() {
        return frequencyForIndex(0);
    }

    public double getMaxFrequency() {
        return frequencyForIndex(maxIndex);
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
    public double frequencyForIndex(final int n) {
        return fStartMHz + n * stepMHz;
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

        return new Frequency(match.bandId, match.fStartMHz, match.stepMHz, minIndex, maxIndex);
    }

}
