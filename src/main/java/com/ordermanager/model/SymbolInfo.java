package com.ordermanager.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ordermanager.model.filter.SymbolFilter;
import com.ordermanager.model.filter.PriceFilter;
import com.ordermanager.model.filter.LotSizeFilter;
import com.ordermanager.model.filter.MinNotionalFilter;
import com.ordermanager.model.filter.PercentPriceBySideFilter;

import java.util.List;

/**
 * Symbol information from GET /api/v3/exchangeInfo?symbol=BTCUSDT.
 *
 * Contains trading rules and filters for a specific trading symbol. We need
 * this for filters
 *
 * https://developers.binance.com/docs/binance-spot-api-docs/rest-api/general-endpoints#exchange-information
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SymbolInfo {

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("status")
    private String status;

    @JsonProperty("filters")
    private List<SymbolFilter> filters;

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

    public List<SymbolFilter> getFilters() {
        return filters;
    }

    public void setFilters(List<SymbolFilter> filters) {
        this.filters = filters;
    }

    /**
     * Get a specific filter by type.
     *
     * @param filterClass Filter class (e.g., PriceFilter.class)
     * @param <T>         Filter type
     * @return Filter instance or null if not found
     */
    private <T extends SymbolFilter> T getFilter(Class<T> filterClass) {
        if (filters == null) {
            return null;
        }

        for (SymbolFilter filter : filters) {
            if (filterClass.isInstance(filter)) {
                return filterClass.cast(filter);
            }
        }

        return null;
    }

    /**
     * Get PRICE_FILTER for this symbol.
     *
     * @return PriceFilter or null if not found
     */
    public PriceFilter getPriceFilter() {
        return getFilter(PriceFilter.class);
    }

    /**
     * Get LOT_SIZE filter for this symbol.
     *
     * @return LotSizeFilter or null if not found
     */
    public LotSizeFilter getLotSizeFilter() {
        return getFilter(LotSizeFilter.class);
    }

    /**
     * Get MIN_NOTIONAL filter for this symbol.
     *
     * @return MinNotionalFilter or null if not found
     */
    public MinNotionalFilter getMinNotionalFilter() {
        return getFilter(MinNotionalFilter.class);
    }

    /**
     * Get PERCENT_PRICE_BY_SIDE filter for this symbol.
     *
     * @return PercentPriceBySideFilter or null if not found
     */
    public PercentPriceBySideFilter getPercentPriceBySideFilter() {
        return getFilter(PercentPriceBySideFilter.class);
    }

    /**
     * Check if symbol is currently tradable.
     *
     * @return true if status is "TRADING"
     */
    public boolean isTradingEnabled() {
        return "TRADING".equals(status);
    }

    @Override
    public String toString() {
        return "SymbolInfo{" +
                "symbol='" + symbol + '\'' +
                ", status='" + status + '\'' +
                ", filters=" + (filters != null ? filters.size() : 0) + " filters" +
                '}';
    }
}
