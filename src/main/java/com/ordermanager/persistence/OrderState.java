package com.ordermanager.persistence;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ordermanager.model.Order;

import java.util.HashMap;
import java.util.Map;

/**
 * Object for order state persistence.
 */
public class OrderState {

    @JsonProperty("version")
    private String version;

    @JsonProperty("lastUpdate")
    private long lastUpdate;

    @JsonProperty("orders")
    private Map<String, Order> orders;

    /**
     * Default constructor for Jackson deserialization.
     */
    public OrderState() {
        this.version = "1.0";
        this.lastUpdate = System.currentTimeMillis();
        this.orders = new HashMap<>();
    }

    /**
     * Create order state from existing orders map.
     *
     * @param orders Map of clientOrderId -> Order
     */
    public OrderState(Map<String, Order> orders) {
        this.version = "1.0";
        this.lastUpdate = System.currentTimeMillis();
        this.orders = new HashMap<>(orders); // Defensive copy
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public long getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(long lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    public Map<String, Order> getOrders() {
        return orders;
    }

    public void setOrders(Map<String, Order> orders) {
        this.orders = orders;
    }

    @Override
    public String toString() {
        return "OrderState{" +
                "version='" + version + '\'' +
                ", lastUpdate=" + lastUpdate +
                ", orders=" + (orders != null ? orders.size() : 0) + " orders" +
                '}';
    }
}
