package com.ordermanager.model;

/**
 * Time in force for orders, how long an order remains active
 * 
 * Essentials for limit orders:
 * GTC: Good Till Cancel - order remains active until filled or canceled
 * IOC: Immediate or Cancel - fill what you can immediately, cancel rest
 * FOK: Fill or Kill - fill entire order immediately or cancel
 * This order manager always uses GTC
 */
public enum TimeInForce {
    GTC
}
