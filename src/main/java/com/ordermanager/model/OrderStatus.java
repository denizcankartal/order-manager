package com.ordermanager.model;

public enum OrderStatus {
    NEW, // Order accepted by the exchange
    PARTIALLY_FILLED, // Order partially filled
    FILLED, // Order completely filled
    CANCELED, // Order canceled by user or exchange
    REJECTED, // Order rejected by exchange
    EXPIRED; // Order expired

    /**
     * Check if this status represents an active order that can still be filled or
     * canceled
     */
    public boolean isActive() {
        return this == NEW || this == PARTIALLY_FILLED;
    }

    /**
     * Check if this status represents a terminal state (order is done, no more
     * updates expected)
     */
    public boolean isTerminal() {
        return this == FILLED || this == CANCELED || this == REJECTED || this == EXPIRED;
    }
}
