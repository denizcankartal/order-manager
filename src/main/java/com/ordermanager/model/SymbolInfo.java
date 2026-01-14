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

    public PriceFilter getPriceFilter() {
        return getFilter(PriceFilter.class);
    }

    public LotSizeFilter getLotSizeFilter() {
        return getFilter(LotSizeFilter.class);
    }

    public MinNotionalFilter getMinNotionalFilter() {
        return getFilter(MinNotionalFilter.class);
    }

    public PercentPriceBySideFilter getPercentPriceBySideFilter() {
        return getFilter(PercentPriceBySideFilter.class);
    }

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
