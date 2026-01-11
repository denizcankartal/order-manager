package com.ordermanager.validator;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ValidationResult.
 *
 * Tests factory methods and state management.
 */
class ValidationResultTest {

    @Test
    void testSuccess_NoAdjustment() {
        BigDecimal value = new BigDecimal("50000.00");
        ValidationResult result = ValidationResult.success(value);

        assertTrue(result.isValid());
        assertEquals(value, result.getOriginalValue());
        assertEquals(value, result.getAdjustedValue());
        assertFalse(result.wasAdjusted());
        assertTrue(result.getWarnings().isEmpty());
    }

    @Test
    void testAdjusted_WithWarning() {
        BigDecimal original = new BigDecimal("50000.123");
        BigDecimal adjusted = new BigDecimal("50000.12");
        String warning = "Price adjusted: 50000.123 → 50000.12 (tickSize: 0.01)";

        ValidationResult result = ValidationResult.adjusted(original, adjusted, warning);

        assertTrue(result.isValid());
        assertEquals(original, result.getOriginalValue());
        assertEquals(adjusted, result.getAdjustedValue());
        assertTrue(result.wasAdjusted());
        assertEquals(1, result.getWarnings().size());
        assertEquals(warning, result.getWarnings().get(0));
    }

    @Test
    void testFailure_WithError() {
        BigDecimal value = new BigDecimal("0.001");
        String error = "Price 0.001 is below minimum 0.01";

        ValidationResult result = ValidationResult.failure(value, error);

        assertFalse(result.isValid());
        assertEquals(value, result.getOriginalValue());
        assertEquals(value, result.getAdjustedValue());
        assertFalse(result.wasAdjusted());
        assertEquals(1, result.getWarnings().size());
        assertEquals(error, result.getWarnings().get(0));
    }

    @Test
    void testWasAdjusted_SameValue_ReturnsFalse() {
        BigDecimal value = new BigDecimal("50000.00");
        ValidationResult result = ValidationResult.adjusted(value, value, "No change");

        assertTrue(result.isValid());
        assertFalse(result.wasAdjusted()); // Same value
    }

    @Test
    void testWasAdjusted_DifferentScale_ReturnsFalse() {
        // BigDecimal.compareTo ignores scale, so 50000.00 == 50000.0
        BigDecimal original = new BigDecimal("50000.00");
        BigDecimal adjusted = new BigDecimal("50000.0");
        ValidationResult result = ValidationResult.adjusted(original, adjusted, "Scale change");

        assertTrue(result.isValid());
        assertFalse(result.wasAdjusted()); // Numerically equal
    }

    @Test
    void testWasAdjusted_ActualChange_ReturnsTrue() {
        BigDecimal original = new BigDecimal("50000.123");
        BigDecimal adjusted = new BigDecimal("50000.12");
        ValidationResult result = ValidationResult.adjusted(original, adjusted, "Adjusted");

        assertTrue(result.isValid());
        assertTrue(result.wasAdjusted());
    }
}
