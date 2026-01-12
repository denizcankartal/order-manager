package com.ordermanager.validator;

import com.ordermanager.model.SymbolInfo;
import com.ordermanager.model.OrderSide;
import com.ordermanager.model.filter.LotSizeFilter;
import com.ordermanager.model.filter.MinNotionalFilter;
import com.ordermanager.model.filter.PriceFilter;
import com.ordermanager.model.filter.PercentPriceBySideFilter;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OrderValidator.
 *
 * Tests all validation scenarios:
 * - PRICE_FILTER validation and adjustment
 * - LOT_SIZE validation and adjustment
 * - MIN_NOTIONAL validation and suggestions
 * - Symbol tradability checks
 * - Integration of multiple filters
 */
class OrderValidatorTest {

        // ==================== PRICE_FILTER Tests ====================

        @Test
        void testValidatePrice_WithinRange_NoAdjustment() {
                // BTCUSDT: price=$50000, tickSize=$0.01
                PriceFilter filter = new PriceFilter(
                                new BigDecimal("0.01"), // minPrice
                                new BigDecimal("1000000"), // maxPrice
                                new BigDecimal("0.01") // tickSize
                );

                SymbolInfo symbolInfo = createSymbolInfo("BTCUSDT", "TRADING", filter, null, null, null);

                OrderValidator.OrderValidationResult result = OrderValidator.validate(
                                "BTCUSDT",
                                OrderSide.BUY,
                                new BigDecimal("0.001"),
                                new BigDecimal("50000.00"), // Already valid
                                symbolInfo,
                                new BigDecimal("50000.00"));

                assertTrue(result.isValid());
                assertEquals(new BigDecimal("50000.00"), result.getAdjustedPrice());
                assertFalse(result.hasWarnings());
        }

        @Test
        void testValidatePrice_RequiresAdjustment_RoundsDown() {
                // BTCUSDT: price=$50000.123, tickSize=$0.01 → should round to $50000.12
                PriceFilter filter = new PriceFilter(
                                new BigDecimal("0.01"),
                                new BigDecimal("1000000"),
                                new BigDecimal("0.01"));

                SymbolInfo symbolInfo = createSymbolInfo("BTCUSDT", "TRADING", filter, null, null, null);

                OrderValidator.OrderValidationResult result = OrderValidator.validate(
                                "BTCUSDT",
                                OrderSide.BUY,
                                new BigDecimal("0.001"),
                                new BigDecimal("50000.123"), // Invalid precision
                                symbolInfo,
                                new BigDecimal("50000.00"));

                assertTrue(result.isValid());
                assertEquals(new BigDecimal("50000.12"), result.getAdjustedPrice());
                assertTrue(result.hasWarnings());
                assertTrue(result.getWarnings().get(0).contains("Price adjusted"));
        }

        @Test
        void testValidatePrice_BelowMinimum_Fails() {
                PriceFilter filter = new PriceFilter(
                                new BigDecimal("0.01"),
                                new BigDecimal("1000000"),
                                new BigDecimal("0.01"));

                SymbolInfo symbolInfo = createSymbolInfo("BTCUSDT", "TRADING", filter, null, null, null);

                OrderValidator.OrderValidationResult result = OrderValidator.validate(
                                "BTCUSDT",
                                OrderSide.BUY,
                                new BigDecimal("0.001"),
                                new BigDecimal("0.001"), // Below minimum
                                symbolInfo,
                                new BigDecimal("50000.00"));

                assertFalse(result.isValid());
                assertTrue(result.getErrors().get(0).contains("below minimum"));
        }

        @Test
        void testValidatePrice_AboveMaximum_Fails() {
                PriceFilter filter = new PriceFilter(
                                new BigDecimal("0.01"),
                                new BigDecimal("1000000"),
                                new BigDecimal("0.01"));

                SymbolInfo symbolInfo = createSymbolInfo("BTCUSDT", "TRADING", filter, null, null, null);

                OrderValidator.OrderValidationResult result = OrderValidator.validate(
                                "BTCUSDT",
                                OrderSide.BUY,
                                new BigDecimal("0.001"),
                                new BigDecimal("2000000"), // Above maximum
                                symbolInfo,
                                new BigDecimal("50000.00"));

                assertFalse(result.isValid());
                assertTrue(result.getErrors().get(0).contains("exceeds maximum"));
        }

        // ==================== LOT_SIZE Tests ====================

        @Test
        void testValidateQuantity_WithinRange_NoAdjustment() {
                // BTCUSDT: qty=0.001, stepSize=0.00001
                LotSizeFilter filter = new LotSizeFilter(
                                new BigDecimal("0.00001"), // minQty
                                new BigDecimal("9000"), // maxQty
                                new BigDecimal("0.00001") // stepSize
                );

                SymbolInfo symbolInfo = createSymbolInfo("BTCUSDT", "TRADING", null, filter, null, null);

                OrderValidator.OrderValidationResult result = OrderValidator.validate(
                                "BTCUSDT",
                                OrderSide.BUY,
                                new BigDecimal("0.001"), // Already valid
                                new BigDecimal("50000"),
                                symbolInfo,
                                new BigDecimal("50000"));

                assertTrue(result.isValid());
                assertEquals(new BigDecimal("0.001"), result.getAdjustedQuantity());
                assertFalse(result.hasWarnings());
        }

        @Test
        void testValidateQuantity_RequiresAdjustment_RoundsDown() {
                // BTCUSDT: qty=0.0012345, stepSize=0.00001 → should round to 0.00123
                LotSizeFilter filter = new LotSizeFilter(
                                new BigDecimal("0.00001"),
                                new BigDecimal("9000"),
                                new BigDecimal("0.00001"));

                SymbolInfo symbolInfo = createSymbolInfo("BTCUSDT", "TRADING", null, filter, null, null);

                OrderValidator.OrderValidationResult result = OrderValidator.validate(
                                "BTCUSDT",
                                OrderSide.BUY,
                                new BigDecimal("0.0012345"), // Invalid precision
                                new BigDecimal("50000"),
                                symbolInfo,
                                new BigDecimal("50000"));

                assertTrue(result.isValid());
                assertEquals(new BigDecimal("0.00123"), result.getAdjustedQuantity());
                assertTrue(result.hasWarnings());
                assertTrue(result.getWarnings().get(0).contains("Quantity adjusted"));
        }

        @Test
        void testValidateQuantity_BelowMinimum_Fails() {
                LotSizeFilter filter = new LotSizeFilter(
                                new BigDecimal("0.00001"),
                                new BigDecimal("9000"),
                                new BigDecimal("0.00001"));

                SymbolInfo symbolInfo = createSymbolInfo("BTCUSDT", "TRADING", null, filter, null, null);

                OrderValidator.OrderValidationResult result = OrderValidator.validate(
                                "BTCUSDT",
                                OrderSide.BUY,
                                new BigDecimal("0.000001"), // Below minimum
                                new BigDecimal("50000"),
                                symbolInfo,
                                new BigDecimal("50000"));

                assertFalse(result.isValid());
                assertTrue(result.getErrors().get(0).contains("below minimum"));
        }

        @Test
        void testValidateQuantity_AboveMaximum_Fails() {
                LotSizeFilter filter = new LotSizeFilter(
                                new BigDecimal("0.00001"),
                                new BigDecimal("9000"),
                                new BigDecimal("0.00001"));

                SymbolInfo symbolInfo = createSymbolInfo("BTCUSDT", "TRADING", null, filter, null, null);

                OrderValidator.OrderValidationResult result = OrderValidator.validate(
                                "BTCUSDT",
                                OrderSide.BUY,
                                new BigDecimal("10000"), // Above maximum
                                new BigDecimal("50000"),
                                symbolInfo,
                                new BigDecimal("50000"));

                assertFalse(result.isValid());
                assertTrue(result.getErrors().get(0).contains("exceeds maximum"));
        }

        // ==================== MIN_NOTIONAL Tests ====================

        @Test
        void testValidateMinNotional_AboveMinimum_Passes() {
                // Order value = 0.001 * 50000 = $50 (above $10 minimum)
                MinNotionalFilter filter = new MinNotionalFilter(new BigDecimal("10.00"));

                SymbolInfo symbolInfo = createSymbolInfo("BTCUSDT", "TRADING", null, null, filter, null);

                OrderValidator.OrderValidationResult result = OrderValidator.validate(
                                "BTCUSDT",
                                OrderSide.BUY,
                                new BigDecimal("0.001"),
                                new BigDecimal("50000"),
                                symbolInfo,
                                new BigDecimal("50000"));

                assertTrue(result.isValid());
                assertFalse(result.hasWarnings());
        }

        @Test
        void testValidateMinNotional_BelowMinimum_FailsWithSuggestions() {
                // Order value = 0.0001 * 50000 = $5 (below $10 minimum)
                MinNotionalFilter filter = new MinNotionalFilter(new BigDecimal("10.00"));

                SymbolInfo symbolInfo = createSymbolInfo("BTCUSDT", "TRADING", null, null, filter, null);

                OrderValidator.OrderValidationResult result = OrderValidator.validate(
                                "BTCUSDT",
                                OrderSide.BUY,
                                new BigDecimal("0.0001"),
                                new BigDecimal("50000"),
                                symbolInfo,
                                new BigDecimal("50000"));

                assertFalse(result.isValid());
                String error = result.getErrors().get(0);
                assertTrue(error.contains("below minimum"), "Should mention below minimum");
                assertTrue(error.contains("increase quantity") || error.contains("increase price"),
                                "Should provide suggestions");
        }

        // ==================== Integration Tests ====================

        @Test
        void testValidate_AllFilters_Success() {
                // Valid order with all filters
                PriceFilter priceFilter = new PriceFilter(
                                new BigDecimal("0.01"),
                                new BigDecimal("1000000"),
                                new BigDecimal("0.01"));
                LotSizeFilter lotSizeFilter = new LotSizeFilter(
                                new BigDecimal("0.00001"),
                                new BigDecimal("9000"),
                                new BigDecimal("0.00001"));
                MinNotionalFilter minNotionalFilter = new MinNotionalFilter(new BigDecimal("10.00"));

                SymbolInfo symbolInfo = createSymbolInfo("BTCUSDT", "TRADING",
                                priceFilter, lotSizeFilter, minNotionalFilter, null);

                OrderValidator.OrderValidationResult result = OrderValidator.validate(
                                "BTCUSDT",
                                OrderSide.BUY,
                                new BigDecimal("0.001"),
                                new BigDecimal("50000.00"),
                                symbolInfo,
                                new BigDecimal("50000.00"));

                assertTrue(result.isValid());
                assertEquals(new BigDecimal("0.001"), result.getAdjustedQuantity());
                assertEquals(new BigDecimal("50000.00"), result.getAdjustedPrice());
                assertFalse(result.hasWarnings());
        }

        @Test
        void testValidate_AllFilters_AdjustsBoth() {
                // Order requires adjustment for both price and quantity
                PriceFilter priceFilter = new PriceFilter(
                                new BigDecimal("0.01"),
                                new BigDecimal("1000000"),
                                new BigDecimal("0.01"));
                LotSizeFilter lotSizeFilter = new LotSizeFilter(
                                new BigDecimal("0.00001"),
                                new BigDecimal("9000"),
                                new BigDecimal("0.00001"));
                MinNotionalFilter minNotionalFilter = new MinNotionalFilter(new BigDecimal("10.00"));

                SymbolInfo symbolInfo = createSymbolInfo("BTCUSDT", "TRADING",
                                priceFilter, lotSizeFilter, minNotionalFilter, null);

                OrderValidator.OrderValidationResult result = OrderValidator.validate(
                                "BTCUSDT",
                                OrderSide.BUY,
                                new BigDecimal("0.0012345"), // Will adjust to 0.00123
                                new BigDecimal("50000.567"), // Will adjust to 50000.56
                                symbolInfo,
                                new BigDecimal("50000.00"));

                assertTrue(result.isValid());
                assertEquals(new BigDecimal("0.00123"), result.getAdjustedQuantity());
                assertEquals(new BigDecimal("50000.56"), result.getAdjustedPrice());
                assertEquals(2, result.getWarnings().size()); // Both adjusted
        }

        @Test
        void testValidate_AllFilters_FailsMinNotionalAfterAdjustment() {
                // After adjustment, order value falls below minimum
                PriceFilter priceFilter = new PriceFilter(
                                new BigDecimal("0.01"),
                                new BigDecimal("1000000"),
                                new BigDecimal("0.01"));
                LotSizeFilter lotSizeFilter = new LotSizeFilter(
                                new BigDecimal("0.00001"),
                                new BigDecimal("9000"),
                                new BigDecimal("0.00001"));
                MinNotionalFilter minNotionalFilter = new MinNotionalFilter(new BigDecimal("10.00"));

                SymbolInfo symbolInfo = createSymbolInfo("BTCUSDT", "TRADING",
                                priceFilter, lotSizeFilter, minNotionalFilter, null);

                // Original: 0.00025 * 50000.99 = $12.50 (OK)
                // After adjustment: 0.00025 * 50000.99 → rounds down → may fail notional
                OrderValidator.OrderValidationResult result = OrderValidator.validate(
                                "BTCUSDT",
                                OrderSide.BUY,
                                new BigDecimal("0.000199"), // Rounds down to 0.00019
                                new BigDecimal("50.00"), // Price OK
                                symbolInfo,
                                new BigDecimal("50.00"));

                assertFalse(result.isValid());
                assertTrue(result.getErrors().get(0).contains("below minimum"));
        }

        // ==================== Edge Cases ====================

        @Test
        void testValidate_SymbolNotTradable_Fails() {
                PriceFilter priceFilter = new PriceFilter(
                                new BigDecimal("0.01"),
                                new BigDecimal("1000000"),
                                new BigDecimal("0.01"));

                SymbolInfo symbolInfo = createSymbolInfo("BTCUSDT", "HALT", priceFilter, null, null, null);

                OrderValidator.OrderValidationResult result = OrderValidator.validate(
                                "BTCUSDT",
                                OrderSide.BUY,
                                new BigDecimal("0.001"),
                                new BigDecimal("50000"),
                                symbolInfo,
                                new BigDecimal("50000"));

                assertFalse(result.isValid());
                assertTrue(result.getErrors().get(0).contains("not tradable"));
                assertTrue(result.getErrors().get(0).contains("HALT"));
        }

        @Test
        void testValidate_NullSymbolInfo_ReturnsWarning() {
                OrderValidator.OrderValidationResult result = OrderValidator.validate(
                                "BTCUSDT",
                                OrderSide.BUY,
                                new BigDecimal("0.001"),
                                new BigDecimal("50000"),
                                null,
                                new BigDecimal("50000"));

                assertTrue(result.isValid()); // Still valid but with warning
                assertTrue(result.hasWarnings());
                assertTrue(result.getWarnings().get(0).contains("No exchange info available"));
        }

        @Test
        void testValidate_NoFilters_Passes() {
                // Symbol with no filters (unusual but possible)
                SymbolInfo symbolInfo = createSymbolInfo("BTCUSDT", "TRADING", null, null, null, null);

                OrderValidator.OrderValidationResult result = OrderValidator.validate(
                                "BTCUSDT",
                                OrderSide.BUY,
                                new BigDecimal("0.001"),
                                new BigDecimal("50000"),
                                symbolInfo,
                                new BigDecimal("50000"));

                assertTrue(result.isValid());
                assertFalse(result.hasWarnings());
        }

        // ==================== Helper Methods ====================

        @Test
        void testPercentPriceBySide_OutOfRange_Fails() {
                PercentPriceBySideFilter filter = new PercentPriceBySideFilter();
                filter.setBidMultiplierDown(new BigDecimal("0.9"));
                filter.setBidMultiplierUp(new BigDecimal("1.1"));
                filter.setAskMultiplierDown(new BigDecimal("0.9"));
                filter.setAskMultiplierUp(new BigDecimal("1.1"));

                SymbolInfo symbolInfo = createSymbolInfo("BTCUSDT", "TRADING",
                                null, null, null, filter);

                OrderValidator.OrderValidationResult result = OrderValidator.validate(
                                "BTCUSDT",
                                OrderSide.BUY,
                                new BigDecimal("0.001"),
                                new BigDecimal("120"), // 20% above ref
                                symbolInfo,
                                new BigDecimal("100"));

                assertFalse(result.isValid());
                assertTrue(result.getErrors().get(0).contains("out of allowed range"));
        }

        @Test
        void testPercentPriceBySide_SkipsWhenNoReferencePrice() {
                PercentPriceBySideFilter filter = new PercentPriceBySideFilter();
                filter.setBidMultiplierDown(new BigDecimal("0.9"));
                filter.setBidMultiplierUp(new BigDecimal("1.1"));
                filter.setAskMultiplierDown(new BigDecimal("0.9"));
                filter.setAskMultiplierUp(new BigDecimal("1.1"));

                SymbolInfo symbolInfo = createSymbolInfo("BTCUSDT", "TRADING",
                                null, null, null, filter);

                OrderValidator.OrderValidationResult result = OrderValidator.validate(
                                "BTCUSDT",
                                OrderSide.SELL,
                                new BigDecimal("0.001"),
                                new BigDecimal("120"),
                                symbolInfo,
                                null);

                assertTrue(result.isValid());
                assertTrue(result.hasWarnings());
                assertTrue(result.getWarnings().get(0).contains("Reference price unavailable"));
        }

        private SymbolInfo createSymbolInfo(String symbol, String status,
                        PriceFilter priceFilter,
                        LotSizeFilter lotSizeFilter,
                        MinNotionalFilter minNotionalFilter,
                        PercentPriceBySideFilter percentFilter) {
                SymbolInfo info = new SymbolInfo();
                info.setSymbol(symbol);
                info.setStatus(status);

                if (priceFilter != null || lotSizeFilter != null || minNotionalFilter != null || percentFilter != null) {
                        info.setFilters(Arrays.asList(priceFilter, lotSizeFilter, minNotionalFilter, percentFilter)
                                        .stream()
                                        .filter(f -> f != null)
                                        .toList());
                }

                return info;
        }

}
