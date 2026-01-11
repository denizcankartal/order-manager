package com.ordermanager.model.filter;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * PRICE_FILTER defines the price rules for a symbol
 * 
 * https://developers.binance.com/docs/binance-spot-api-docs/filters#price_filter
 */
public class PriceFilter extends SymbolFilter {

    @JsonProperty("minPrice")
    private BigDecimal minPrice;

    @JsonProperty("maxPrice")
    private BigDecimal maxPrice;

    @JsonProperty("tickSize")
    private BigDecimal tickSize;

    public PriceFilter() {
    }

    public PriceFilter(BigDecimal minPrice, BigDecimal maxPrice, BigDecimal tickSize) {
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
        this.tickSize = tickSize;
    }

    @Override
    public String getFilterType() {
        return "PRICE_FILTER";
    }

    public BigDecimal getMinPrice() {
        return minPrice;
    }

    public void setMinPrice(BigDecimal minPrice) {
        this.minPrice = minPrice;
    }

    public BigDecimal getMaxPrice() {
        return maxPrice;
    }

    public void setMaxPrice(BigDecimal maxPrice) {
        this.maxPrice = maxPrice;
    }

    public BigDecimal getTickSize() {
        return tickSize;
    }

    public void setTickSize(BigDecimal tickSize) {
        this.tickSize = tickSize;
    }

    @Override
    public String toString() {
        return "PriceFilter{" +
                "minPrice=" + minPrice +
                ", maxPrice=" + maxPrice +
                ", tickSize=" + tickSize +
                '}';
    }
}
