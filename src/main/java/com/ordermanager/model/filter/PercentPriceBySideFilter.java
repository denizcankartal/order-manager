package com.ordermanager.model.filter;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * The PERCENT_PRICE_BY_SIDE filter defines the valid range for the price based
 * on the average of the previous trades.
 *
 * Buy orders will succeed on this filter if:
 * 
 * - Order price <= weightedAveragePrice * bidMultiplierUp
 * - Order price >= weightedAveragePrice * bidMultiplierDown
 * 
 * 
 * Sell orders will succeed on this filter if:
 * 
 * - Order Price <= weightedAveragePrice * askMultiplierUp
 * - Order Price >= weightedAveragePrice * askMultiplierDown
 * 
 * https://developers.binance.com/docs/binance-spot-api-docs/filters#percent_price_by_side
 */
public class PercentPriceBySideFilter extends SymbolFilter {

    @JsonProperty("bidMultiplierUp")
    private BigDecimal bidMultiplierUp;

    @JsonProperty("bidMultiplierDown")
    private BigDecimal bidMultiplierDown;

    @JsonProperty("askMultiplierUp")
    private BigDecimal askMultiplierUp;

    @JsonProperty("askMultiplierDown")
    private BigDecimal askMultiplierDown;

    @Override
    public String getFilterType() {
        return "PERCENT_PRICE_BY_SIDE";
    }

    public BigDecimal getBidMultiplierUp() {
        return bidMultiplierUp;
    }

    public void setBidMultiplierUp(BigDecimal bidMultiplierUp) {
        this.bidMultiplierUp = bidMultiplierUp;
    }

    public BigDecimal getBidMultiplierDown() {
        return bidMultiplierDown;
    }

    public void setBidMultiplierDown(BigDecimal bidMultiplierDown) {
        this.bidMultiplierDown = bidMultiplierDown;
    }

    public BigDecimal getAskMultiplierUp() {
        return askMultiplierUp;
    }

    public void setAskMultiplierUp(BigDecimal askMultiplierUp) {
        this.askMultiplierUp = askMultiplierUp;
    }

    public BigDecimal getAskMultiplierDown() {
        return askMultiplierDown;
    }

    public void setAskMultiplierDown(BigDecimal askMultiplierDown) {
        this.askMultiplierDown = askMultiplierDown;
    }
}
