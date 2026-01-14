package com.ordermanager.model;

import java.util.Collections;
import java.util.List;

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
