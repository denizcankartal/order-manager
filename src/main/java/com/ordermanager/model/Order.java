package com.ordermanager.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Order entity representing a trading order on Binance.
 * 
 * https://developers.binance.com/docs/binance-spot-api-docs/rest-api/trading-endpoints
 * 
 * This model is used for:
 * - Local state management (in-memory cache)
 * - Persistent storage (JSON serialization)
 * - API responses (mapping from Binance DTOs)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Order {

    @JsonProperty("clientOrderId")
    private String clientOrderId;

    @JsonProperty("orderId")
    private Long orderId;

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("side")
    private OrderSide side;

    @JsonProperty("price")
    private BigDecimal price;

    @JsonProperty("origQty")
    private BigDecimal origQty;

    @JsonProperty("executedQty")
    private BigDecimal executedQty;

    @JsonProperty("status")
    private OrderStatus status;

    /**
     * Order creation timestamp (milliseconds since epoch)
     */
    @JsonProperty("time")
    private Long time;

    /**
     * Last update timestamp (milliseconds since epoch)
     */
    @JsonProperty("updateTime")
    private long updateTime;

    public Order() {
    }

    public Order(String clientOrderId, String symbol, OrderSide side, BigDecimal price, BigDecimal origQty) {
        this.clientOrderId = clientOrderId;
        this.symbol = symbol;
        this.side = side;
        this.price = price;
        this.origQty = origQty;
        this.executedQty = BigDecimal.ZERO;
        this.status = OrderStatus.PENDING_NEW;
        this.updateTime = System.currentTimeMillis();
    }

    public String getClientOrderId() {
        return clientOrderId;
    }

    public void setClientOrderId(String clientOrderId) {
        this.clientOrderId = clientOrderId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public OrderSide getSide() {
        return side;
    }

    public void setSide(OrderSide side) {
        this.side = side;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getOrigQty() {
        return origQty;
    }

    public void setOrigQty(BigDecimal origQty) {
        this.origQty = origQty;
    }

    public BigDecimal getExecutedQty() {
        return executedQty;
    }

    public void setExecutedQty(BigDecimal executedQty) {
        this.executedQty = executedQty;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public Long getTime() {
        return time;
    }

    public void setTime(Long time) {
        this.time = time;
    }

    public long getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(long updateTime) {
        this.updateTime = updateTime;
    }

    /**
     * Check if this order is active (can be filled or canceled)
     */
    public boolean isActive() {
        return status != null && status.isActive();
    }

    /**
     * Check if this order is in a terminal state (no longer active)
     */
    public boolean isTerminal() {
        return status != null && status.isTerminal();
    }

    /**
     * Orders are equal if they have the same clientOrderId
     */
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Order order = (Order) o;
        return Objects.equals(clientOrderId, order.clientOrderId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clientOrderId);
    }

    @Override
    public String toString() {
        return "Order{" +
                "clientOrderId='" + clientOrderId + '\'' +
                ", orderId=" + orderId +
                ", symbol='" + symbol + '\'' +
                ", side=" + side +
                ", price=" + price +
                ", origQty=" + origQty +
                ", executedQty=" + executedQty +
                ", status=" + status +
                ", updateTime=" + updateTime +
                '}';
    }
}
