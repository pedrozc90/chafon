package com.contare.chafon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class FrequencyTest {

    @Test
    public void test_Brazil_A() {
        final Frequency frequency = Frequency.get(902, 907.5);
        assertNotNull(frequency);
        assertEquals(2, frequency.getBand());
        assertEquals(902.75, frequency.getFStartMHz());
        assertEquals(0.5, frequency.getStepMHz());
        assertEquals(0, frequency.getMinIndex());
        assertEquals(9, frequency.getMaxIndex());
        assertEquals(902.75, frequency.getMinFrequency());
        assertEquals(907.25, frequency.getMaxFrequency());

        assertEquals(Frequency.BRAZIL_1, frequency);
    }

    @Test
    public void test_Brazil_B() {
        final Frequency frequency = Frequency.US;

        final int[] indexes = frequency.indicesForRange(915, 927.75);
        assertNotNull(indexes);
        assertEquals(2, indexes[0]);
        assertEquals(19, indexes[1]);
    }

}
