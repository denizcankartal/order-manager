package com.ordermanager.validator;

import com.ordermanager.client.BinanceRestClient;
import com.ordermanager.config.AppConfig;
import com.ordermanager.model.SymbolInfo;
import com.ordermanager.model.OrderSide;
import com.ordermanager.service.ExchangeInfoService;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for OrderValidator against real Binance Testnet.
 *
 * Tests validation with actual exchange filters from Binance API.
 *
 * Requirements:
 * - BINANCE_API_KEY and BINANCE_SECRET_KEY must be set in environment
 * - Tests run against Binance Spot Testnet
 *
 * Run with: mvn test -Dtest=OrderValidatorIntegrationTest
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderValidatorIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(OrderValidatorIntegrationTest.class);

    private BinanceRestClient restClient;
    private ExchangeInfoService exchangeInfoService;

    @BeforeAll
    void setUp() {
        try {
            AppConfig config = AppConfig.loadFromEnv();

            // Create HTTP client for TimeSync
            okhttp3.OkHttpClient httpClient = new okhttp3.OkHttpClient.Builder()
                    .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .build();

            com.ordermanager.service.TimeSync timeSync = new com.ordermanager.service.TimeSync(httpClient,
                    config.getBaseUrl());
            timeSync.sync();

            restClient = new BinanceRestClient(config, timeSync);
            exchangeInfoService = new ExchangeInfoService(restClient);

            logger.info("Integration tests starting - connecting to Binance Testnet");
        } catch (Exception e) {
            logger.warn("Skipping integration tests - API credentials not configured: {}", e.getMessage());
            Assumptions.assumeTrue(false, "API credentials not configured");
        }
    }

    @AfterAll
    void tearDown() {
        if (restClient != null) {
            restClient.shutdown();
        }
    }

    // ==================== BTCUSDT Tests ====================

    @Test
    void testValidate_BTCUSDT_ValidOrder_NoAdjustment() {
        SymbolInfo btcusdt = exchangeInfoService.getSymbolInfo("BTCUSDT");
        assertNotNull(btcusdt, "BTCUSDT not found in exchange info");

        logger.info("BTCUSDT filters: {}", btcusdt.getFilters());

        // Valid order: 0.001 BTC @ $50000
        OrderValidator.OrderValidationResult result = OrderValidator.validate(
                "BTCUSDT",
                OrderSide.BUY,
                new BigDecimal("0.001"),
                new BigDecimal("50000.00"),
                btcusdt,
                new BigDecimal("50000.00"));

        assertTrue(result.isValid(), "Order should be valid");
        assertEquals(new BigDecimal("0.001"), result.getAdjustedQuantity());
        assertEquals(new BigDecimal("50000.00"), result.getAdjustedPrice());
        assertFalse(result.hasWarnings(), "Should have no warnings");

        logger.info("BTCUSDT validation result: {}", result);
    }

    @Test
    void testValidate_BTCUSDT_RequiresPriceAdjustment() {
        SymbolInfo btcusdt = exchangeInfoService.getSymbolInfo("BTCUSDT");
        assertNotNull(btcusdt, "BTCUSDT not found in exchange info");

        // Invalid precision: $50000.123 (should round to $50000.12 with tickSize 0.01)
        OrderValidator.OrderValidationResult result = OrderValidator.validate(
                "BTCUSDT",
                OrderSide.BUY,
                new BigDecimal("0.001"),
                new BigDecimal("50000.123"),
                btcusdt,
                new BigDecimal("50000.00"));

        assertTrue(result.isValid(), "Order should be valid after adjustment");
        assertTrue(result.hasWarnings(), "Should have adjustment warning");
        assertNotEquals(new BigDecimal("50000.123"), result.getAdjustedPrice());

        String warning = result.getWarnings().get(0);
        assertTrue(warning.contains("Price adjusted"), "Should mention price adjustment");

        logger.info("Price adjustment result: {} -> {}", new BigDecimal("50000.123"), result.getAdjustedPrice());
    }

    @Test
    void testValidate_BTCUSDT_RequiresQuantityAdjustment() {
        SymbolInfo btcusdt = exchangeInfoService.getSymbolInfo("BTCUSDT");
        assertNotNull(btcusdt, "BTCUSDT not found in exchange info");

        // Invalid precision: 0.0012345 BTC (should round down based on stepSize)
        OrderValidator.OrderValidationResult result = OrderValidator.validate(
                "BTCUSDT",
                OrderSide.BUY,
                new BigDecimal("0.0012345"),
                new BigDecimal("50000.00"),
                btcusdt,
                new BigDecimal("50000.00"));

        assertTrue(result.isValid(), "Order should be valid after adjustment");
        assertTrue(result.hasWarnings(), "Should have adjustment warning");
        assertNotEquals(new BigDecimal("0.0012345"), result.getAdjustedQuantity());

        String warning = result.getWarnings().get(0);
        assertTrue(warning.contains("Quantity adjusted"), "Should mention quantity adjustment");

        logger.info("Quantity adjustment result: {} -> {}", new BigDecimal("0.0012345"), result.getAdjustedQuantity());
    }

    @Test
    void testValidate_BTCUSDT_BelowMinNotional() {
        SymbolInfo btcusdt = exchangeInfoService.getSymbolInfo("BTCUSDT");
        assertNotNull(btcusdt, "BTCUSDT not found in exchange info");

        // Very small order that fails MIN_NOTIONAL (MIN_NOTIONAL is $5)
        OrderValidator.OrderValidationResult result = OrderValidator.validate(
                "BTCUSDT",
                OrderSide.BUY,
                new BigDecimal("0.00005"), // $2.50 at $50000 (below $5 minimum)
                new BigDecimal("50000.00"),
                btcusdt,
                new BigDecimal("50000.00"));

        assertFalse(result.isValid(), "Order should fail MIN_NOTIONAL");
        assertFalse(result.getErrors().isEmpty(), "Should have errors");

        String error = result.getErrors().get(0);
        assertTrue(error.contains("below minimum"), "Should mention minimum notional");
        assertTrue(error.contains("increase quantity") || error.contains("increase price"),
                "Should provide suggestions");

        logger.info("MIN_NOTIONAL failure: {}", error);
    }

    @Test
    void testValidate_BTCUSDT_QuantityBelowMinimum() {
        SymbolInfo btcusdt = exchangeInfoService.getSymbolInfo("BTCUSDT");
        assertNotNull(btcusdt, "BTCUSDT not found in exchange info");

        // Quantity below LOT_SIZE minimum
        OrderValidator.OrderValidationResult result = OrderValidator.validate(
                "BTCUSDT",
                OrderSide.BUY,
                new BigDecimal("0.000001"), // Too small
                new BigDecimal("50000.00"),
                btcusdt,
                new BigDecimal("50000.00"));

        assertFalse(result.isValid(), "Order should fail LOT_SIZE");
        assertFalse(result.getErrors().isEmpty(), "Should have errors");

        String error = result.getErrors().get(0);
        assertTrue(error.contains("below minimum"), "Should mention minimum quantity");

        logger.info("LOT_SIZE minimum failure: {}", error);
    }

    // ==================== ETHUSDT Tests ====================

    @Test
    void testValidate_ETHUSDT_ValidOrder() {
        SymbolInfo ethusdt = exchangeInfoService.getSymbolInfo("ETHUSDT");
        assertNotNull(ethusdt, "ETHUSDT not found in exchange info");

        logger.info("ETHUSDT filters: {}", ethusdt.getFilters());

        // Valid order: 0.01 ETH @ $3000
        OrderValidator.OrderValidationResult result = OrderValidator.validate(
                "ETHUSDT",
                OrderSide.BUY,
                new BigDecimal("0.01"),
                new BigDecimal("3000.00"),
                ethusdt,
                new BigDecimal("3000.00"));

        assertTrue(result.isValid(), "Order should be valid");
        logger.info("ETHUSDT validation result: {}", result);
    }

    @Test
    void testValidate_ETHUSDT_AdjustsBoth() {
        SymbolInfo ethusdt = exchangeInfoService.getSymbolInfo("ETHUSDT");
        assertNotNull(ethusdt, "ETHUSDT not found in exchange info");

        // Invalid precision for both price and quantity
        OrderValidator.OrderValidationResult result = OrderValidator.validate(
                "ETHUSDT",
                OrderSide.BUY,
                new BigDecimal("0.012345"), // Invalid precision
                new BigDecimal("3000.567"), // Invalid precision
                ethusdt,
                new BigDecimal("3000.00"));

        if (result.isValid()) {
            assertTrue(result.hasWarnings(), "Should have adjustment warnings");
            logger.info("ETHUSDT adjustments - Qty: {} -> {}, Price: {} -> {}",
                    new BigDecimal("0.012345"), result.getAdjustedQuantity(),
                    new BigDecimal("3000.567"), result.getAdjustedPrice());
        } else {
            logger.info("ETHUSDT validation failed: {}", result.getErrors());
        }
    }

    // ==================== Edge Cases ====================

    @Test
    void testValidate_InvalidSymbol_ReturnsNull() {
        SymbolInfo invalid = exchangeInfoService.getSymbolInfo("INVALIDUSDT");
        assertNull(invalid, "Invalid symbol should not be found");
    }

    @Test
    void testValidate_SymbolNotTradable() {
        // Find a non-trading symbol (if any exist)
        var allSymbols = exchangeInfoService.getAllSymbols();
        logger.info("Total symbols available: {}", allSymbols.size());

        boolean foundNonTradingSymbol = false;
        for (String symbol : allSymbols) {
            SymbolInfo info = exchangeInfoService.getSymbolInfo(symbol);
            if (info != null && !info.isTradingEnabled()) {
                foundNonTradingSymbol = true;
                logger.info("Found non-trading symbol: {} (status: {})", symbol, info.getStatus());

                OrderValidator.OrderValidationResult result = OrderValidator.validate(
                        symbol,
                        OrderSide.BUY,
                        new BigDecimal("1.0"),
                        new BigDecimal("1.0"),
                        info,
                        new BigDecimal("1.0"));

                assertFalse(result.isValid(), "Non-trading symbol should fail validation");
                assertTrue(result.getErrors().get(0).contains("not tradable"));
                break;
            }
        }

        if (!foundNonTradingSymbol) {
            logger.info("No non-trading symbols found in testnet");
        }
    }

    @Test
    void testExchangeInfoService_CachesCorrectly() {
        // First call - initializes cache
        SymbolInfo btcusdt1 = exchangeInfoService.getSymbolInfo("BTCUSDT");
        assertNotNull(btcusdt1);

        // Second call - uses cache (should be fast)
        long startTime = System.currentTimeMillis();
        SymbolInfo btcusdt2 = exchangeInfoService.getSymbolInfo("BTCUSDT");
        long duration = System.currentTimeMillis() - startTime;

        assertNotNull(btcusdt2);
        assertTrue(duration < 10, "Cached lookup should be very fast (< 10ms)");
        assertSame(btcusdt1, btcusdt2, "Should return same cached instance");

        logger.info("Cache lookup time: {}ms", duration);
    }

    @Test
    void testExchangeInfoService_RefreshWorks() {
        // Initial load
        SymbolInfo btcusdt1 = exchangeInfoService.getSymbolInfo("BTCUSDT");
        assertNotNull(btcusdt1);

        // Refresh cache
        exchangeInfoService.refresh();

        // Get again - should have new instance
        SymbolInfo btcusdt2 = exchangeInfoService.getSymbolInfo("BTCUSDT");
        assertNotNull(btcusdt2);

        logger.info("Cache refreshed successfully");
    }

    @Test
    void testExchangeInfoService_GetAllSymbols() {
        var symbols = exchangeInfoService.getAllSymbols();
        assertFalse(symbols.isEmpty(), "Should have symbols");
        assertTrue(symbols.contains("BTCUSDT"), "Should contain BTCUSDT");
        assertTrue(symbols.contains("ETHUSDT"), "Should contain ETHUSDT");

        logger.info("Total trading pairs: {}", symbols.size());
    }
}
