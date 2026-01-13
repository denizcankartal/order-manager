package com.ordermanager.service;

import com.ordermanager.client.BinanceRestClient;
import com.ordermanager.exception.ApiException;
import com.ordermanager.model.SymbolInfo;
import com.ordermanager.model.dto.ExchangeInfoResponse;
import com.ordermanager.util.RetryUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for exchange information and symbol filters.
 *
 * Provides cached access to symbol trading rules and filters from Binance.
 * Cache is populated on first access and can be refreshed as needed.
 *
 * Thread-safe implementation using ConcurrentHashMap for concurrent access.
 *
 * https://developers.binance.com/docs/binance-spot-api-docs/rest-api/general-endpoints#exchange-information
 */
public class ExchangeInfoService {

    private static final Logger logger = LoggerFactory.getLogger(ExchangeInfoService.class);

    private final BinanceRestClient restClient;
    private final Map<String, SymbolInfo> symbolCache;
    private volatile boolean initialized;

    public ExchangeInfoService(BinanceRestClient restClient) {
        this.restClient = restClient;
        this.symbolCache = new ConcurrentHashMap<>();
        this.initialized = false;
    }

    /**
     * Get symbol information with filters for a specific trading pair.
     *
     * Initializes cache on first call. Returns null if symbol not found.
     *
     * @param symbol Trading symbol (e,g. BTCUSDT)
     * @return SymbolInfo with filters, or null if not found
     */
    public SymbolInfo getSymbolInfo(String symbol) {
        ensureInitialized();

        SymbolInfo info = symbolCache.get(symbol);
        if (info == null) {
            logger.warn("Symbol {} not found in exchange info cache", symbol);
        }
        return info;
    }

    /**
     * Ensure cache is initialized before use.
     *
     * Thread-safe lazy initialization using double-checked locking.
     */
    private void ensureInitialized() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    loadExchangeInfo();
                    initialized = true;
                }
            }
        }
    }

    /**
     * Load exchange information from Binance API and populate cache.
     */
    private void loadExchangeInfo() {
        try {
            ExchangeInfoResponse response = RetryUtils.executeWithRetry(
                    () -> restClient.get("/api/v3/exchangeInfo", ExchangeInfoResponse.class),
                    "load exchange info", logger);

            if (response.getSymbols() == null) {
                logger.warn("Exchange info response contains no symbols");
                return;
            }

            symbolCache.clear();
            for (SymbolInfo symbolInfo : response.getSymbols()) {
                if (symbolInfo.getSymbol() != null) {
                    symbolCache.put(symbolInfo.getSymbol(), symbolInfo);
                }
            }

        } catch (ApiException e) {
            logger.error("Failed to load exchange info: error={}", e.getMessage());

            if (e.isRateLimit()) {
                throw new RuntimeException(
                        "Rate limit exceeded while loading exchange info. Wait 60 seconds and retry. Error: "
                                + e.getMessage(),
                        e);
            }

            throw new RuntimeException(String.format(
                    "Failed to load exchange info: %s (error code: %d)", e.getMessage(), e.getStatusCode()), e);

        } catch (Exception e) {
            logger.error("Unexpected error loading exchange info", e);
            throw new RuntimeException("Failed to initialize exchange info cache", e);
        }
    }
}
