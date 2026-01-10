package com.ordermanager.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OrderStatus enum helper methods
 */
class OrderStatusTest {

    @Test
    void testIsActive() {
        assertTrue(OrderStatus.PENDING_NEW.isActive());
        assertTrue(OrderStatus.NEW.isActive());
        assertTrue(OrderStatus.PARTIALLY_FILLED.isActive());

        assertFalse(OrderStatus.FILLED.isActive());
        assertFalse(OrderStatus.CANCELED.isActive());
        assertFalse(OrderStatus.REJECTED.isActive());
        assertFalse(OrderStatus.EXPIRED.isActive());
    }

    @Test
    void testIsTerminal() {
        assertTrue(OrderStatus.FILLED.isTerminal());
        assertTrue(OrderStatus.CANCELED.isTerminal());
        assertTrue(OrderStatus.REJECTED.isTerminal());
        assertTrue(OrderStatus.EXPIRED.isTerminal());

        assertFalse(OrderStatus.PENDING_NEW.isTerminal());
        assertFalse(OrderStatus.NEW.isTerminal());
        assertFalse(OrderStatus.PARTIALLY_FILLED.isTerminal());
    }

    @Test
    void testCanFill() {
        assertTrue(OrderStatus.NEW.canFill());
        assertTrue(OrderStatus.PARTIALLY_FILLED.canFill());

        assertFalse(OrderStatus.PENDING_NEW.canFill());
        assertFalse(OrderStatus.FILLED.canFill());
        assertFalse(OrderStatus.CANCELED.canFill());
        assertFalse(OrderStatus.REJECTED.canFill());
        assertFalse(OrderStatus.EXPIRED.canFill());
    }

    @Test
    void testCanCancel() {
        assertTrue(OrderStatus.PENDING_NEW.canCancel());
        assertTrue(OrderStatus.NEW.canCancel());
        assertTrue(OrderStatus.PARTIALLY_FILLED.canCancel());

        assertFalse(OrderStatus.FILLED.canCancel());
        assertFalse(OrderStatus.CANCELED.canCancel());
        assertFalse(OrderStatus.REJECTED.canCancel());
        assertFalse(OrderStatus.EXPIRED.canCancel());
    }

    @Test
    void testActiveAndTerminalAreMutuallyExclusive() {
        // Every status should be either active or terminal, never both
        for (OrderStatus status : OrderStatus.values()) {
            assertTrue(status.isActive() != status.isTerminal(),
                    "Status " + status + " should be either active or terminal, not both or neither");
        }
    }

    @Test
    void testPendingNewCannotFill() {
        // PENDING_NEW is active but cannot be filled because not yet on exchange
        assertTrue(OrderStatus.PENDING_NEW.isActive());
        assertFalse(OrderStatus.PENDING_NEW.canFill());
        assertTrue(OrderStatus.PENDING_NEW.canCancel());
    }
}
