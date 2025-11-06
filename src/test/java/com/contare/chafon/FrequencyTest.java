package com.contare.chafon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class FrequencyTest {

    @Test
    public void test() {
        final Frequency frequency = Frequency.get(902, 907.5);
        assertNotNull(frequency);
        assertEquals(2, frequency.getBandId());
        assertEquals(902.75, frequency.getFStartMHz());
        assertEquals(0.5, frequency.getStepMHz());
        assertEquals(0, frequency.getMinIndex());
        assertEquals(9, frequency.getMaxIndex());
        assertEquals(902.75, frequency.getMinFrequency());
        assertEquals(907.25, frequency.getMaxFrequency());

        assertEquals(Frequency.BRAZIL_A, frequency);
    }

}
