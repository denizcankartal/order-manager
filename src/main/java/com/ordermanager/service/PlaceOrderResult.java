package com.ordermanager.service;

import com.ordermanager.model.Order;

import java.util.Collections;
import java.util.List;

/**
 * Lightweight wrapper for a placed order along with any validation warnings
 * (e.g., auto-adjusted price/qty).
 */
public class PlaceOrderResult {
    private final Order order;
    private final List<String> warnings;

    public PlaceOrderResult(Order order, List<String> warnings) {
        this.order = order;
        this.warnings = warnings != null ? List.copyOf(warnings) : Collections.emptyList();
    }

    public Order getOrder() {
        return order;
    }

    public List<String> getWarnings() {
        return warnings;
    }
}
