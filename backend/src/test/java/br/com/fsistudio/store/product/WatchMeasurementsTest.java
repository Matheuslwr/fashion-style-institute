package br.com.fsistudio.store.product;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class WatchMeasurementsTest {
    @Test
    void requiresAllFourPositiveMeasurements() {
        assertTrue(new WatchMeasurements(new BigDecimal("4.4"), new BigDecimal("5.5"),
            new BigDecimal("0.9"), new BigDecimal("3.5")).isComplete());
        assertFalse(new WatchMeasurements(new BigDecimal("4.4"), null,
            new BigDecimal("0.9"), new BigDecimal("3.5")).isComplete());
        assertFalse(new WatchMeasurements(new BigDecimal("4.4"), new BigDecimal("5.5"),
            BigDecimal.ZERO, new BigDecimal("3.5")).isComplete());
    }
}
