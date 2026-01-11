package com.ordermanager.service;

import com.ordermanager.model.SymbolInfo;
import com.ordermanager.model.dto.ExchangeInfoResponse;
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

    private final BinanceApiService apiService;
    private final Map<String, SymbolInfo> symbolCache;
    private volatile boolean initialized;

    public ExchangeInfoService(BinanceApiService apiService) {
        this.apiService = apiService;
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
     * Check if a symbol exists in the exchange.
     *
     * @param symbol Trading symbol (e.g., "BTCUSDT")
     * @return true if symbol exists
     */
    public boolean symbolExists(String symbol) {
        ensureInitialized();
        return symbolCache.containsKey(symbol);
    }

    /**
     * Get all cached symbol names.
     *
     * @return Set of all symbol names
     */
    public java.util.Set<String> getAllSymbols() {
        ensureInitialized();
        return symbolCache.keySet();
    }

    /**
     * Refresh the exchange info cache.
     *
     * Fetches latest exchange information from Binance and updates cache.
     * Should be called periodically to keep filters up-to-date.
     */
    public void refresh() {
        logger.info("Refreshing exchange info cache");
        loadExchangeInfo();
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
            ExchangeInfoResponse response = apiService.getExchangeInfo();

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

            logger.info("Exchange info cache loaded: {} symbols", symbolCache.size());

        } catch (Exception e) {
            logger.error("Failed to load exchange info", e);
            throw new RuntimeException("Failed to initialize exchange info cache", e);
        }
    }
}
