package com.ordermanager.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * DTO for Binance order operation responses (place, cancel, query).
 * 
 * https://developers.binance.com/docs/binance-spot-api-docs/rest-api/trading-endpoints
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderResponse {

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("orderId")
    private Long orderId;

    @JsonProperty("clientOrderId")
    private String clientOrderId;

    @JsonProperty("transactTime")
    private Long transactTime;

    @JsonProperty("price")
    private String price;

    @JsonProperty("origQty")
    private String origQty;

    @JsonProperty("executedQty")
    private String executedQty;

    @JsonProperty("status")
    private String status;

    @JsonProperty("side")
    private String side;

    public OrderResponse() {
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getClientOrderId() {
        return clientOrderId;
    }

    public void setClientOrderId(String clientOrderId) {
        this.clientOrderId = clientOrderId;
    }

    public Long getTransactTime() {
        return transactTime;
    }

    public void setTransactTime(Long transactTime) {
        this.transactTime = transactTime;
    }

    public String getPrice() {
        return price;
    }

    public void setPrice(String price) {
        this.price = price;
    }

    public String getOrigQty() {
        return origQty;
    }

    public void setOrigQty(String origQty) {
        this.origQty = origQty;
    }

    public String getExecutedQty() {
        return executedQty;
    }

    public void setExecutedQty(String executedQty) {
        this.executedQty = executedQty;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSide() {
        return side;
    }

    public void setSide(String side) {
        this.side = side;
    }

    /**
     * Helper to get executedQty as BigDecimal
     */
    public BigDecimal getExecutedQtyAsBigDecimal() {
        return executedQty != null ? new BigDecimal(executedQty) : BigDecimal.ZERO;
    }

    /**
     * Helper to get price as BigDecimal
     */
    public BigDecimal getPriceAsBigDecimal() {
        return price != null ? new BigDecimal(price) : BigDecimal.ZERO;
    }

    /**
     * Helper to get origQty as BigDecimal
     */
    public BigDecimal getOrigQtyAsBigDecimal() {
        return origQty != null ? new BigDecimal(origQty) : BigDecimal.ZERO;
    }

    @Override
    public String toString() {
        return "OrderResponse{" +
                "symbol='" + symbol + '\'' +
                ", orderId=" + orderId +
                ", clientOrderId='" + clientOrderId + '\'' +
                ", transactTime=" + transactTime +
                ", price='" + price + '\'' +
                ", origQty='" + origQty + '\'' +
                ", executedQty='" + executedQty + '\'' +
                ", status='" + status + '\'' +
                ", side='" + side + '\'' +
                '}';
    }
}
