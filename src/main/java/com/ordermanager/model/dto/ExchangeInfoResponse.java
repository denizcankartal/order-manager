package com.ordermanager.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * DTO for exchange information response.
 * 
 * Endpoint: GET /api/v3/exchangeInfo
 * 
 * Example response:
 * {
 * "timezone": "UTC",
 * "serverTime": 123456789999,
 * "rateLimits": [...],
 * "exchangeFilters": [],
 * "symbols": [
 * {
 * "symbol": "ETHBTC",
 * "status": "TRADING",
 * "baseAsset": "BTC",
 * "quoteAsset": "USDT",
 * "filters": [...]
 * }
 * ]
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExchangeInfoResponse {

    @JsonProperty("timezone")
    private String timezone;

    @JsonProperty("serverTime")
    private long serverTime;

    @JsonProperty("symbols")
    private List<SymbolInfo> symbols;

    public ExchangeInfoResponse() {
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public long getServerTime() {
        return serverTime;
    }

    public void setServerTime(long serverTime) {
        this.serverTime = serverTime;
    }

    public List<SymbolInfo> getSymbols() {
        return symbols;
    }

    public void setSymbols(List<SymbolInfo> symbols) {
        this.symbols = symbols;
    }

    /**
     * Get symbol info by symbol name.
     * 
     * @param symbol Symbol name (e.g., "BTCUSDT")
     * @return SymbolInfo or null if not found
     */
    public SymbolInfo getSymbolInfo(String symbol) {
        if (symbols == null) {
            return null;
        }

        return symbols.stream()
                .filter(s -> s.getSymbol().equals(symbol))
                .findFirst()
                .orElse(null);
    }

    @Override
    public String toString() {
        return "ExchangeInfoResponse{" +
                "timezone='" + timezone + '\'' +
                ", serverTime=" + serverTime +
                ", symbols=" + (symbols != null ? symbols.size() + " symbols" : "null") +
                '}';
    }

    /**
     * Minimal symbol information.
     * Full filter details will be added in Phase 3.
     */
    public static class SymbolInfo {

        @JsonProperty("symbol")
        private String symbol;

        @JsonProperty("status")
        private String status;

        @JsonProperty("baseAsset")
        private String baseAsset;

        @JsonProperty("quoteAsset")
        private String quoteAsset;

        public SymbolInfo() {
        }

        public String getSymbol() {
            return symbol;
        }

        public void setSymbol(String symbol) {
            this.symbol = symbol;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getBaseAsset() {
            return baseAsset;
        }

        public void setBaseAsset(String baseAsset) {
            this.baseAsset = baseAsset;
        }

        public String getQuoteAsset() {
            return quoteAsset;
        }

        public void setQuoteAsset(String quoteAsset) {
            this.quoteAsset = quoteAsset;
        }

        @Override
        public String toString() {
            return "SymbolInfo{" +
                    "symbol='" + symbol + '\'' +
                    ", status='" + status + '\'' +
                    ", baseAsset='" + baseAsset + '\'' +
                    ", quoteAsset='" + quoteAsset + '\'' +
                    '}';
        }
    }
}
