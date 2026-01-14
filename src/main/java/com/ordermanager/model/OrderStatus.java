package com.ordermanager.model;

public enum OrderStatus {
    NEW, // Order accepted by the exchange
    PARTIALLY_FILLED, // Order partially filled
    FILLED, // Order completely filled
    CANCELED, // Order canceled by user or exchange
    REJECTED, // Order rejected by exchange
    EXPIRED; // Order expired

    public boolean isActive() {
        return this == NEW || this == PARTIALLY_FILLED;
    }

    public boolean isTerminal() {
        return this == FILLED || this == CANCELED || this == REJECTED || this == EXPIRED;
    }
}
