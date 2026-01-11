package com.ordermanager.validator;

import com.ordermanager.model.SymbolInfo;
import com.ordermanager.model.filter.LotSizeFilter;
import com.ordermanager.model.filter.MinNotionalFilter;
import com.ordermanager.model.filter.PriceFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Orchestrates order validation across binance symbol filters:
 * 
 * - PRICE_FILTER: Auto-adjusts price to valid tick size
 * - LOT_SIZE: Auto-adjusts quantity to valid step size
 * - MIN_NOTIONAL: Fails if order value too small (no auto-adjust)
 *
 * https://developers.binance.com/docs/binance-spot-api-docs/filters
 */
public class OrderValidator {

    private static final Logger logger = LoggerFactory.getLogger(OrderValidator.class);

    /**
     * Validate order parameters and return adjusted values.
     *
     * @param symbol     Trading symbol (e.g., "BTCUSDT")
     * @param quantity   Order quantity
     * @param price      Order price
     * @param symbolInfo Symbol information with filters
     * @return Validation result with adjusted values or errors
     */
    public static OrderValidationResult validate(
            String symbol,
            BigDecimal quantity,
            BigDecimal price,
            SymbolInfo symbolInfo) {

        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        BigDecimal adjustedPrice = price;
        BigDecimal adjustedQty = quantity;

        if (symbolInfo == null) {
            String warning = "No exchange info available for " + symbol + " - skipping filter validation";
            logger.warn(warning);
            warnings.add(warning);
            return new OrderValidationResult(true, adjustedQty, adjustedPrice, warnings, errors);
        }

        if (!symbolInfo.isTradingEnabled()) {
            String error = "Symbol " + symbol + " is not tradable (status: " + symbolInfo.getStatus() + ")";
            logger.error(error);
            errors.add(error);
            return new OrderValidationResult(false, quantity, price, warnings, errors);
        }

        PriceFilter priceFilter = symbolInfo.getPriceFilter();
        if (priceFilter != null) {
            ValidationResult priceResult = validatePrice(adjustedPrice, priceFilter);
            if (!priceResult.isValid()) {
                errors.addAll(priceResult.getWarnings());
                return new OrderValidationResult(false, adjustedQty, adjustedPrice, warnings, errors);
            }
            adjustedPrice = priceResult.getAdjustedValue();
            warnings.addAll(priceResult.getWarnings());
        }

        LotSizeFilter lotSizeFilter = symbolInfo.getLotSizeFilter();
        if (lotSizeFilter != null) {
            ValidationResult qtyResult = validateQuantity(adjustedQty, lotSizeFilter);
            if (!qtyResult.isValid()) {
                errors.addAll(qtyResult.getWarnings());
                return new OrderValidationResult(false, adjustedQty, adjustedPrice, warnings, errors);
            }
            adjustedQty = qtyResult.getAdjustedValue();
            warnings.addAll(qtyResult.getWarnings());
        }

        MinNotionalFilter minNotionalFilter = symbolInfo.getMinNotionalFilter();
        if (minNotionalFilter != null) {
            ValidationResult notionalResult = validateMinNotional(adjustedQty, adjustedPrice, minNotionalFilter);
            if (!notionalResult.isValid()) {
                errors.addAll(notionalResult.getWarnings());
                return new OrderValidationResult(false, adjustedQty, adjustedPrice, warnings, errors);
            }
            warnings.addAll(notionalResult.getWarnings());
        }

        logger.debug("Order validation successful for {}: qty={} -> {}, price={} -> {}",
                symbol, quantity, adjustedQty, price, adjustedPrice);

        return new OrderValidationResult(true, adjustedQty, adjustedPrice, warnings, errors);
    }

    /**
     * Validate price according to PRICE_FILTER.
     *
     * Auto-adjusts price to nearest valid tick size (rounds DOWN).
     */
    private static ValidationResult validatePrice(BigDecimal price, PriceFilter filter) {
        BigDecimal minPrice = filter.getMinPrice();
        BigDecimal maxPrice = filter.getMaxPrice();
        BigDecimal tickSize = filter.getTickSize();

        if (price.compareTo(minPrice) < 0) {
            return ValidationResult.failure(price,
                    String.format("Price %s is below minimum %s", price.toPlainString(), minPrice.toPlainString()));
        }

        if (price.compareTo(maxPrice) > 0) {
            return ValidationResult.failure(price,
                    String.format("Price %s exceeds maximum %s", price.toPlainString(), maxPrice.toPlainString()));
        }

        BigDecimal adjustedPrice = adjustToTickSize(price, tickSize);

        if (price.compareTo(adjustedPrice) != 0) {
            String warning = String.format("Price adjusted: %s → %s (tickSize: %s)",
                    price.toPlainString(), adjustedPrice.toPlainString(), tickSize.toPlainString());
            return ValidationResult.adjusted(price, adjustedPrice, warning);
        }

        return ValidationResult.success(price);
    }

    /**
     * Validate quantity according to LOT_SIZE filter.
     *
     * Auto-adjusts quantity to nearest valid step size (rounds DOWN).
     */
    private static ValidationResult validateQuantity(BigDecimal quantity, LotSizeFilter filter) {
        BigDecimal minQty = filter.getMinQty();
        BigDecimal maxQty = filter.getMaxQty();
        BigDecimal stepSize = filter.getStepSize();

        if (quantity.compareTo(minQty) < 0) {
            return ValidationResult.failure(quantity,
                    String.format("Quantity %s is below minimum %s", quantity.toPlainString(), minQty.toPlainString()));
        }

        if (quantity.compareTo(maxQty) > 0) {
            return ValidationResult.failure(quantity,
                    String.format("Quantity %s exceeds maximum %s", quantity.toPlainString(), maxQty.toPlainString()));
        }

        BigDecimal adjustedQty = adjustToStepSize(quantity, stepSize);

        if (quantity.compareTo(adjustedQty) != 0) {
            String warning = String.format("Quantity adjusted: %s → %s (stepSize: %s)",
                    quantity.toPlainString(), adjustedQty.toPlainString(), stepSize.toPlainString());
            return ValidationResult.adjusted(quantity, adjustedQty, warning);
        }

        return ValidationResult.success(quantity);
    }

    /**
     * Validate minimum notional (order value).
     *
     * Cannot auto-adjust - fails with suggestions if below minimum.
     */
    private static ValidationResult validateMinNotional(BigDecimal quantity, BigDecimal price,
            MinNotionalFilter filter) {
        BigDecimal minNotional = filter.getMinNotional();
        BigDecimal orderValue = quantity.multiply(price);

        if (orderValue.compareTo(minNotional) < 0) {
            BigDecimal suggestedQty = minNotional.divide(price, 8, RoundingMode.UP);
            BigDecimal suggestedPrice = minNotional.divide(quantity, 8, RoundingMode.UP);

            String error = String.format(
                    "Order value %s is below minimum %s. Suggestions: increase quantity to %s OR increase price to %s",
                    orderValue.toPlainString(), minNotional.toPlainString(),
                    suggestedQty.toPlainString(), suggestedPrice.toPlainString());

            return ValidationResult.failure(orderValue, error);
        }

        return ValidationResult.success(orderValue);
    }

    /**
     * Adjust price to nearest valid tick (rounds DOWN).
     *
     * Formula: floor(price / tickSize) * tickSize
     */
    private static BigDecimal adjustToTickSize(BigDecimal price, BigDecimal tickSize) {
        if (tickSize.compareTo(BigDecimal.ZERO) == 0) {
            return price;
        }

        BigDecimal ticks = price.divide(tickSize, 0, RoundingMode.DOWN);
        return ticks.multiply(tickSize).stripTrailingZeros();
    }

    /**
     * Adjust quantity to nearest valid step (rounds DOWN).
     *
     * Formula: floor(quantity / stepSize) * stepSize
     */
    private static BigDecimal adjustToStepSize(BigDecimal quantity, BigDecimal stepSize) {
        if (stepSize.compareTo(BigDecimal.ZERO) == 0) {
            return quantity;
        }

        BigDecimal steps = quantity.divide(stepSize, 0, RoundingMode.DOWN);
        return steps.multiply(stepSize).stripTrailingZeros();
    }

    public static class OrderValidationResult {
        private final boolean valid;
        private final BigDecimal adjustedQuantity;
        private final BigDecimal adjustedPrice;
        private final List<String> warnings;
        private final List<String> errors;

        public OrderValidationResult(boolean valid, BigDecimal adjustedQuantity,
                BigDecimal adjustedPrice, List<String> warnings, List<String> errors) {
            this.valid = valid;
            this.adjustedQuantity = adjustedQuantity;
            this.adjustedPrice = adjustedPrice;
            this.warnings = new ArrayList<>(warnings);
            this.errors = new ArrayList<>(errors);
        }

        public boolean isValid() {
            return valid;
        }

        public BigDecimal getAdjustedQuantity() {
            return adjustedQuantity;
        }

        public BigDecimal getAdjustedPrice() {
            return adjustedPrice;
        }

        public List<String> getWarnings() {
            return Collections.unmodifiableList(warnings);
        }

        public List<String> getErrors() {
            return Collections.unmodifiableList(errors);
        }

        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }

        @Override
        public String toString() {
            return "OrderValidationResult{" +
                    "valid=" + valid +
                    ", adjustedPrice=" + adjustedPrice +
                    ", adjustedQuantity=" + adjustedQuantity +
                    ", warnings=" + warnings.size() +
                    ", errors=" + errors.size() +
                    '}';
        }
    }
}
