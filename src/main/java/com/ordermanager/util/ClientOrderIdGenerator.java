package com.ordermanager.util;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Generates unique client order IDs for Binance orders.
 *
 * Format: cli-{timestamp}-{counter}
 *
 * Thread-safe implementation using AtomicLong for counter.
 */
public class ClientOrderIdGenerator {

    private static final AtomicLong counter = new AtomicLong(0);

    /**
     * Generate a unique client order ID.
     *
     * @return Unique client order ID
     */
    public static String generate() {
        long timestamp = System.currentTimeMillis();
        long count = counter.incrementAndGet();
        return String.format("cli-%d-%d", timestamp, count);
    }

    public static void resetCounter() {
        counter.set(0);
    }
}
