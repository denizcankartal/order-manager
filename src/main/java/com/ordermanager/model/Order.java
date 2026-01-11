package com.ordermanager.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    /**
     * Client-generated order ID
     */
    @JsonProperty("clientOrderId")
    private String clientOrderId;

    /**
     * Exchange-generated order ID
     */
    @JsonProperty("orderId")
    private Long orderId;

    /**
     * Trading pair symbol (e.g., "BTCUSDT")
     */
    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("side")
    private OrderSide side;

    @JsonProperty("price")
    private BigDecimal price;

    /**
     * Original order quantity
     */
    @JsonProperty("origQty")
    private BigDecimal origQty;

    /**
     * Executed quantity
     */
    @JsonProperty("executedQty")
    private BigDecimal executedQty;

    /**
     * Current order status
     */
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

    // Constructors

    /**
     * Default constructor for Jackson deserialization
     */
    public Order() {
    }

    public Order(String clientOrderId, String symbol, OrderSide side,
            BigDecimal price, BigDecimal origQty) {
        this.clientOrderId = clientOrderId;
        this.symbol = symbol;
        this.side = side;
        this.price = price;
        this.origQty = origQty;
        this.executedQty = BigDecimal.ZERO;
        this.status = OrderStatus.PENDING_NEW;
        this.updateTime = System.currentTimeMillis();
    }

    // Builder Pattern

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String clientOrderId;
        private Long orderId;
        private String symbol;
        private OrderSide side;
        private BigDecimal price;
        private BigDecimal origQty;
        private BigDecimal executedQty;
        private OrderStatus status;
        private Long time;
        private long updateTime;

        public Builder clientOrderId(String clientOrderId) {
            this.clientOrderId = clientOrderId;
            return this;
        }

        public Builder orderId(Long orderId) {
            this.orderId = orderId;
            return this;
        }

        public Builder symbol(String symbol) {
            this.symbol = symbol;
            return this;
        }

        public Builder side(OrderSide side) {
            this.side = side;
            return this;
        }

        public Builder price(BigDecimal price) {
            this.price = price;
            return this;
        }

        public Builder origQty(BigDecimal origQty) {
            this.origQty = origQty;
            return this;
        }

        public Builder executedQty(BigDecimal executedQty) {
            this.executedQty = executedQty;
            return this;
        }

        public Builder status(OrderStatus status) {
            this.status = status;
            return this;
        }

        public Builder time(Long time) {
            this.time = time;
            return this;
        }

        public Builder updateTime(long updateTime) {
            this.updateTime = updateTime;
            return this;
        }

        /**
         * Build the Order instance with defaults for unset fields
         */
        public Order build() {
            Order order = new Order();
            order.clientOrderId = this.clientOrderId;
            order.orderId = this.orderId;
            order.symbol = this.symbol;
            order.side = this.side;
            order.price = this.price;
            order.origQty = this.origQty;
            order.executedQty = this.executedQty != null ? this.executedQty : BigDecimal.ZERO;
            order.status = this.status != null ? this.status : OrderStatus.PENDING_NEW;
            order.time = this.time;
            order.updateTime = this.updateTime != 0 ? this.updateTime : System.currentTimeMillis();
            return order;
        }
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
     * Get remaining quantity to be filled
     */
    public BigDecimal getRemainingQty() {
        if (origQty == null || executedQty == null) {
            return BigDecimal.ZERO;
        }
        return origQty.subtract(executedQty);
    }

    /**
     * Get fill percentage (0-100)
     */
    public BigDecimal getFillPercentage() {
        if (origQty == null || origQty.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        if (executedQty == null) {
            return BigDecimal.ZERO;
        }
        return executedQty.divide(origQty, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
    }

    /**
     * Check if order is partially filled
     */
    public boolean isPartiallyFilled() {
        return executedQty != null && executedQty.compareTo(BigDecimal.ZERO) > 0
                && origQty != null && executedQty.compareTo(origQty) < 0;
    }

    /**
     * Check if order is fully filled
     */
    public boolean isFilled() {
        return status == OrderStatus.FILLED ||
                (executedQty != null && origQty != null && executedQty.compareTo(origQty) == 0);
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
