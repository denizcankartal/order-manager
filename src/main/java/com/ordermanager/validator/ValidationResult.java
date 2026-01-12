package com.ordermanager.validator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of order parameter validation.
 *
 * - Original value - before adjustment
 * - Adjusted value - after auto-correction to comply with filters)
 * - List of warnings/messages
 * - Validation success/failure status
 *
 * Used by individual filter validators (PRICE_FILTER, LOT_SIZE, MIN_NOTIONAL).
 */
public class ValidationResult {

    private final BigDecimal originalValue;
    private final BigDecimal adjustedValue;
    private final List<String> warnings;
    private final boolean valid;

    private ValidationResult(BigDecimal originalValue, BigDecimal adjustedValue,
            List<String> warnings, boolean valid) {
        this.originalValue = originalValue;
        this.adjustedValue = adjustedValue;
        this.warnings = new ArrayList<>(warnings);
        this.valid = valid;
    }

    /**
     * Create a successful validation result with no adjustments
     *
     * @param value The validated value
     * @return Success result
     */
    public static ValidationResult success(BigDecimal value) {
        return new ValidationResult(value, value, Collections.emptyList(), true);
    }

    /**
     * Create a successful validation result with auto-adjustment
     *
     * @param originalValue Value before adjustment
     * @param adjustedValue Value after adjustment
     * @param warning       Warning message explaining the adjustment
     * @return Success result with adjustment warning
     */
    public static ValidationResult adjusted(BigDecimal originalValue, BigDecimal adjustedValue, String warning) {
        List<String> warnings = new ArrayList<>();
        warnings.add(warning);
        return new ValidationResult(originalValue, adjustedValue, warnings, true);
    }

    /**
     * Create a failed validation result
     *
     * @param value The invalid value
     * @param error Error message explaining why validation failed
     * @return Failure result
     */
    public static ValidationResult failure(BigDecimal value, String error) {
        List<String> warnings = new ArrayList<>();
        warnings.add(error);
        return new ValidationResult(value, value, warnings, false);
    }

    public BigDecimal getOriginalValue() {
        return originalValue;
    }

    public BigDecimal getAdjustedValue() {
        return adjustedValue;
    }

    public List<String> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    public boolean isValid() {
        return valid;
    }

    /**
     * Check if value was adjusted during validation.
     *
     * @return true if adjustedValue != originalValue
     */
    public boolean wasAdjusted() {
        return originalValue != null && adjustedValue != null &&
                originalValue.compareTo(adjustedValue) != 0;
    }
}
