package com.contare.chafon;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

@Data
@AllArgsConstructor
@ToString
public class UHFInformation {

    private final String version;
    private final int power;
    private final int[] powerPerAntenna;
    private final int band;
    private final String bandMask;
    private final int maxIndex;
    private final int minIndex;
    private final boolean beep;
    private final int antennaMask;
    private final int[] antennas;
    private final String serial;

}
