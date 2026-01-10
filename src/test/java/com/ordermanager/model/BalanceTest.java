package com.ordermanager.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class BalanceTest {

    @Test
    void testGetTotal_withBothValues() {
        Balance balance = new Balance("BTC", new BigDecimal("1.5"), new BigDecimal("0.3"));
        assertEquals(new BigDecimal("1.8"), balance.getTotal());
    }

    @Test
    void testGetTotal_withNullFree() {
        Balance balance = new Balance();
        balance.setAsset("BTC");
        balance.setLocked(new BigDecimal("0.5"));
        // free is null, should treat as zero
        assertEquals(new BigDecimal("0.5"), balance.getTotal());
    }

    @Test
    void testGetTotal_withNullLocked() {
        Balance balance = new Balance();
        balance.setAsset("USDT");
        balance.setFree(new BigDecimal("1000"));
        // locked is null, should treat as zero
        assertEquals(new BigDecimal("1000"), balance.getTotal());
    }

    @Test
    void testGetTotal_withBothNull() {
        Balance balance = new Balance();
        balance.setAsset("BTC");
        // both are null, should return zero
        assertEquals(BigDecimal.ZERO, balance.getTotal());
    }

    @Test
    void testHasBalance_withPositiveBalance() {
        Balance balance = new Balance("BTC", new BigDecimal("0.000001"), BigDecimal.ZERO);
        assertTrue(balance.hasBalance());
    }

    @Test
    void testHasBalance_withZeroBalance() {
        Balance balance = new Balance("BTC", BigDecimal.ZERO, BigDecimal.ZERO);
        assertFalse(balance.hasBalance());
    }

    @Test
    void testHasBalance_withOnlyLockedBalance() {
        Balance balance = new Balance("BTC", BigDecimal.ZERO, new BigDecimal("0.5"));
        assertTrue(balance.hasBalance());
    }

    @Test
    void testHasBalance_withNullValues() {
        Balance balance = new Balance();
        balance.setAsset("BTC");
        // both null, total = 0, should return false
        assertFalse(balance.hasBalance());
    }

    @Test
    void testConstructor() {
        Balance balance = new Balance("BTC", new BigDecimal("1.0"), new BigDecimal("0.5"));

        assertEquals("BTC", balance.getAsset());
        assertEquals(new BigDecimal("1.0"), balance.getFree());
        assertEquals(new BigDecimal("0.5"), balance.getLocked());
    }

    @Test
    void testGetTotal_withOnlyFree() {
        Balance balance = new Balance("BTC", new BigDecimal("2.5"), BigDecimal.ZERO);
        assertEquals(new BigDecimal("2.5"), balance.getTotal());
        assertTrue(balance.hasBalance());
    }

    @Test
    void testGetTotal_withOnlyLocked() {
        Balance balance = new Balance("BTC", BigDecimal.ZERO, new BigDecimal("1.2"));
        assertEquals(new BigDecimal("1.2"), balance.getTotal());
        assertTrue(balance.hasBalance());
    }
}
