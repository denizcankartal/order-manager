package com.ordermanager.model.filter;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * MIN_NOTIONAL filter defines the minimum notional value allowed for an order
 * on a symbol.
 * 
 * https://developers.binance.com/docs/binance-spot-api-docs/filters#min_notional
 */
public class MinNotionalFilter extends SymbolFilter {

    @JsonProperty("minNotional")
    private BigDecimal minNotional;

    public MinNotionalFilter() {
    }

    public MinNotionalFilter(BigDecimal minNotional) {
        this.minNotional = minNotional;
    }

    @Override
    public String getFilterType() {
        return "MIN_NOTIONAL";
    }

    public BigDecimal getMinNotional() {
        return minNotional;
    }

    public void setMinNotional(BigDecimal minNotional) {
        this.minNotional = minNotional;
    }

    @Override
    public String toString() {
        return "MinNotionalFilter{" +
                "minNotional=" + minNotional +
                '}';
    }
}
