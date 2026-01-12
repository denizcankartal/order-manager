package com.ordermanager.model.filter;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * The LOT_SIZE filter defines the quantity (aka "lots" in auction terms) rules
 * for a symbol
 * 
 * In order to pass the lot size, the following must be true for
 * quantity/icebergQty:
 * 
 * quantity >= minQty
 * quantity <= maxQty
 * quantity % stepSize == 0
 * 
 * https://developers.binance.com/docs/binance-spot-api-docs/filters#lot_size
 */
public class LotSizeFilter extends SymbolFilter {

    @JsonProperty("minQty")
    private BigDecimal minQty;

    @JsonProperty("maxQty")
    private BigDecimal maxQty;

    @JsonProperty("stepSize")
    private BigDecimal stepSize;

    public LotSizeFilter() {
    }

    public LotSizeFilter(BigDecimal minQty, BigDecimal maxQty, BigDecimal stepSize) {
        this.minQty = minQty;
        this.maxQty = maxQty;
        this.stepSize = stepSize;
    }

    @Override
    public String getFilterType() {
        return "LOT_SIZE";
    }

    public BigDecimal getMinQty() {
        return minQty;
    }

    public void setMinQty(BigDecimal minQty) {
        this.minQty = minQty;
    }

    public BigDecimal getMaxQty() {
        return maxQty;
    }

    public void setMaxQty(BigDecimal maxQty) {
        this.maxQty = maxQty;
    }

    public BigDecimal getStepSize() {
        return stepSize;
    }

    public void setStepSize(BigDecimal stepSize) {
        this.stepSize = stepSize;
    }
}
