package com.ordermanager.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * DTO for GET /api/v3/ticker/price
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TickerPriceResponse {

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("price")
    private String price;

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getPrice() {
        return price;
    }

    public void setPrice(String price) {
        this.price = price;
    }

    public BigDecimal getPriceAsBigDecimal() {
        return price != null ? new BigDecimal(price) : BigDecimal.ZERO;
    }
}
