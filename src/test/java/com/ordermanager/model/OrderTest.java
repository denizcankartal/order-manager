package com.ordermanager.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Order model business methods and builder
 */
class OrderTest {

        @Test
        void testConstructor_setsDefaultValues() {
                Order order = new Order("cli-123", "BTCUSDT", OrderSide.BUY,
                                OrderType.LIMIT, new BigDecimal("45000"), new BigDecimal("0.001"));

                assertEquals("cli-123", order.getClientOrderId());
                assertEquals("BTCUSDT", order.getSymbol());
                assertEquals(OrderSide.BUY, order.getSide());
                assertEquals(OrderType.LIMIT, order.getType());
                assertEquals(new BigDecimal("45000"), order.getPrice());
                assertEquals(new BigDecimal("0.001"), order.getOrigQty());
                assertEquals(BigDecimal.ZERO, order.getExecutedQty());
                assertEquals(OrderStatus.PENDING_NEW, order.getStatus());
                assertEquals(TimeInForce.GTC, order.getTimeInForce());
                assertTrue(order.getUpdateTime() > 0);
        }

        @Test
        void testBuilder_withAllFields() {
                Order order = Order.builder()
                                .clientOrderId("cli-123")
                                .orderId(999L)
                                .symbol("BTCUSDT")
                                .side(OrderSide.SELL)
                                .type(OrderType.LIMIT)
                                .price(new BigDecimal("3000"))
                                .origQty(new BigDecimal("1.5"))
                                .executedQty(new BigDecimal("0.5"))
                                .status(OrderStatus.PARTIALLY_FILLED)
                                .timeInForce(TimeInForce.GTC)
                                .updateTime(1234567890L)
                                .build();

                assertEquals("cli-123", order.getClientOrderId());
                assertEquals(Long.valueOf(999L), order.getOrderId());
                assertEquals("BTCUSDT", order.getSymbol());
                assertEquals(OrderSide.SELL, order.getSide());
                assertEquals(new BigDecimal("3000"), order.getPrice());
                assertEquals(new BigDecimal("1.5"), order.getOrigQty());
                assertEquals(new BigDecimal("0.5"), order.getExecutedQty());
                assertEquals(OrderStatus.PARTIALLY_FILLED, order.getStatus());
                assertEquals(TimeInForce.GTC, order.getTimeInForce());
                assertEquals(1234567890L, order.getUpdateTime());
        }

        @Test
        void testBuilder_autoGeneratesUpdateTime() {
                long beforeBuild = System.currentTimeMillis();
                Order order = Order.builder()
                                .clientOrderId("cli-123")
                                .symbol("BTCUSDT")
                                .build();
                long afterBuild = System.currentTimeMillis();

                // updateTime should be auto-generated between beforeBuild and afterBuild
                assertTrue(order.getUpdateTime() >= beforeBuild);
                assertTrue(order.getUpdateTime() <= afterBuild);
        }

        @Test
        void testIsActive_delegatesToStatus() {
                Order newOrder = Order.builder()
                                .clientOrderId("cli-1")
                                .symbol("BTCUSDT")
                                .status(OrderStatus.NEW)
                                .build();
                assertTrue(newOrder.isActive());

                Order filledOrder = Order.builder()
                                .clientOrderId("cli-2")
                                .symbol("BTCUSDT")
                                .status(OrderStatus.FILLED)
                                .build();
                assertFalse(filledOrder.isActive());
        }

        @Test
        void testIsActive_withNullStatus() {
                Order order = Order.builder()
                                .clientOrderId("cli-1")
                                .symbol("BTCUSDT")
                                .build();
                order.setStatus(null);

                assertFalse(order.isActive()); // null status should return false
        }

        @Test
        void testIsTerminal_delegatesToStatus() {
                Order filledOrder = Order.builder()
                                .clientOrderId("cli-1")
                                .symbol("BTCUSDT")
                                .status(OrderStatus.FILLED)
                                .build();
                assertTrue(filledOrder.isTerminal());

                Order newOrder = Order.builder()
                                .clientOrderId("cli-2")
                                .symbol("BTCUSDT")
                                .status(OrderStatus.NEW)
                                .build();
                assertFalse(newOrder.isTerminal());
        }

        @Test
        void testGetRemainingQty_partiallyFilled() {
                Order order = Order.builder()
                                .clientOrderId("cli-1")
                                .symbol("BTCUSDT")
                                .origQty(new BigDecimal("1.0"))
                                .executedQty(new BigDecimal("0.3"))
                                .build();

                assertEquals(new BigDecimal("0.7"), order.getRemainingQty());
        }

        @Test
        void testGetRemainingQty_notFilled() {
                Order order = Order.builder()
                                .clientOrderId("cli-1")
                                .symbol("BTCUSDT")
                                .origQty(new BigDecimal("1.0"))
                                .executedQty(BigDecimal.ZERO)
                                .build();

                assertEquals(new BigDecimal("1.0"), order.getRemainingQty());
        }

        @Test
        void testGetRemainingQty_fullyFilled() {
                Order order = Order.builder()
                                .clientOrderId("cli-1")
                                .symbol("BTCUSDT")
                                .origQty(new BigDecimal("1.0"))
                                .executedQty(new BigDecimal("1.0"))
                                .build();

                assertEquals(new BigDecimal("0.0"), order.getRemainingQty());
        }

        @Test
        void testGetRemainingQty_withNullValues() {
                Order order = Order.builder()
                                .clientOrderId("cli-1")
                                .symbol("BTCUSDT")
                                .build();
                order.setOrigQty(null);
                order.setExecutedQty(null);

                assertEquals(BigDecimal.ZERO, order.getRemainingQty());
        }

        @Test
        void testGetRemainingQty_withPartialFill() {
                Order order = Order.builder()
                                .clientOrderId("cli-1")
                                .symbol("BTCUSDT")
                                .origQty(new BigDecimal("1.0"))
                                .executedQty(new BigDecimal("0.75"))
                                .build();

                assertEquals(new BigDecimal("0.25"), order.getRemainingQty());
        }

        @Test
        void testGetFillPercentage_partiallyFilled() {
                Order order = Order.builder()
                                .clientOrderId("cli-1")
                                .symbol("BTCUSDT")
                                .origQty(new BigDecimal("1.0"))
                                .executedQty(new BigDecimal("0.25"))
                                .build();

                assertEquals(new BigDecimal("25.0000"), order.getFillPercentage());
        }

        @Test
        void testGetFillPercentage_fullyFilled() {
                Order order = Order.builder()
                                .clientOrderId("cli-1")
                                .symbol("BTCUSDT")
                                .origQty(new BigDecimal("1.0"))
                                .executedQty(new BigDecimal("1.0"))
                                .build();

                assertEquals(new BigDecimal("100.0000"), order.getFillPercentage());
        }

        @Test
        void testGetFillPercentage_notFilled() {
                Order order = Order.builder()
                                .clientOrderId("cli-1")
                                .symbol("BTCUSDT")
                                .origQty(new BigDecimal("1.0"))
                                .executedQty(BigDecimal.ZERO)
                                .build();

                assertEquals(new BigDecimal("0.0000"), order.getFillPercentage());
        }

        @Test
        void testGetFillPercentage_withZeroOrigQty() {
                Order order = Order.builder()
                                .clientOrderId("cli-1")
                                .symbol("BTCUSDT")
                                .origQty(BigDecimal.ZERO)
                                .executedQty(BigDecimal.ZERO)
                                .build();

                assertEquals(BigDecimal.ZERO, order.getFillPercentage());
        }

        @Test
        void testGetFillPercentage_withNullValues() {
                Order order = Order.builder()
                                .clientOrderId("cli-1")
                                .symbol("BTCUSDT")
                                .build();
                order.setOrigQty(null);
                order.setExecutedQty(null);

                assertEquals(BigDecimal.ZERO, order.getFillPercentage());
        }

        @Test
        void testGetFillPercentage_partialFill() {
                Order order = Order.builder()
                                .clientOrderId("cli-1")
                                .symbol("BTCUSDT")
                                .origQty(new BigDecimal("1.0"))
                                .executedQty(new BigDecimal("0.75"))
                                .build();

                assertEquals(new BigDecimal("75.0000"), order.getFillPercentage());
        }

        @Test
        void testIsPartiallyFilled_true() {
                Order order = Order.builder()
                                .clientOrderId("cli-1")
                                .symbol("BTCUSDT")
                                .origQty(new BigDecimal("1.0"))
                                .executedQty(new BigDecimal("0.5"))
                                .build();

                assertTrue(order.isPartiallyFilled());
        }

        @Test
        void testIsPartiallyFilled_notFilled() {
                Order order = Order.builder()
                                .clientOrderId("cli-1")
                                .symbol("BTCUSDT")
                                .origQty(new BigDecimal("1.0"))
                                .executedQty(BigDecimal.ZERO)
                                .build();

                assertFalse(order.isPartiallyFilled());
        }

        @Test
        void testIsPartiallyFilled_fullyFilled() {
                Order order = Order.builder()
                                .clientOrderId("cli-1")
                                .symbol("BTCUSDT")
                                .origQty(new BigDecimal("1.0"))
                                .executedQty(new BigDecimal("1.0"))
                                .build();

                assertFalse(order.isPartiallyFilled());
        }

        @Test
        void testIsPartiallyFilled_withZeroExecuted() {
                Order order = Order.builder()
                                .clientOrderId("cli-1")
                                .symbol("BTCUSDT")
                                .origQty(new BigDecimal("1.0"))
                                .executedQty(BigDecimal.ZERO)
                                .build();

                assertFalse(order.isPartiallyFilled());
        }

        @Test
        void testIsFilled_byStatus() {
                Order order = Order.builder()
                                .clientOrderId("cli-1")
                                .symbol("BTCUSDT")
                                .status(OrderStatus.FILLED)
                                .build();

                assertTrue(order.isFilled());
        }

        @Test
        void testIsFilled_byQuantity() {
                Order order = Order.builder()
                                .clientOrderId("cli-1")
                                .symbol("BTCUSDT")
                                .origQty(new BigDecimal("1.0"))
                                .executedQty(new BigDecimal("1.0"))
                                .status(OrderStatus.PARTIALLY_FILLED) // status not FILLED, but qty matches
                                .build();

                assertTrue(order.isFilled());
        }

        @Test
        void testIsFilled_notFilled() {
                Order order = Order.builder()
                                .clientOrderId("cli-1")
                                .symbol("BTCUSDT")
                                .origQty(new BigDecimal("1.0"))
                                .executedQty(new BigDecimal("0.3"))
                                .status(OrderStatus.PARTIALLY_FILLED)
                                .build();

                assertFalse(order.isFilled());
        }

        @Test
        void testIsFilled_withNullExecutedQty() {
                Order order = Order.builder()
                                .clientOrderId("cli-1")
                                .symbol("BTCUSDT")
                                .origQty(new BigDecimal("1.0"))
                                .status(OrderStatus.NEW)
                                .build();
                order.setExecutedQty(null);

                assertFalse(order.isFilled());
        }

        @Test
        void testEquals_differentClientOrderId() {
                Order order1 = Order.builder()
                                .clientOrderId("cli-123")
                                .symbol("BTCUSDT")
                                .build();

                Order order2 = Order.builder()
                                .clientOrderId("cli-456")
                                .symbol("BTCUSDT")
                                .build();

                assertNotEquals(order1, order2);
        }

        @Test
        void testEquals_nullClientOrderId() {
                Order order1 = Order.builder()
                                .symbol("BTCUSDT")
                                .build();
                order1.setClientOrderId(null);

                Order order2 = Order.builder()
                                .symbol("BTCUSDT")
                                .build();
                order2.setClientOrderId(null);

                assertEquals(order1, order2);
        }
}
