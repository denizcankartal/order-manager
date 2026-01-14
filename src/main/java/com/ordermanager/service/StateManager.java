package com.ordermanager.service;

import com.ordermanager.model.Order;
import com.ordermanager.model.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Thread-safe in-memory state manager for orders.
 *
 * This class:
 * - Stores orders in ConcurrentHashMap for fast, thread-safe access
 * - Supports lookup by both orderId and clientOrderId
 * - Filters orders by symbol and status
 * - Does NOT handle persistence (that's StatePersistence's job)
 *
 * Thread Model:
 * - Main thread: CLI commands (add, cancel, list, show)
 * - WebSocket thread: Real-time updates
 * - Disk writer thread: Async persistence
 *
 * All access is lock-free (ConcurrentHashMap provides this).
 */
public class StateManager {

    private static final Logger logger = LoggerFactory.getLogger(StateManager.class);

    /**
     * Primary storage: clientOrderId -> Order
     */
    private final ConcurrentHashMap<String, Order> ordersByClientId;

    /**
     * Secondary index: orderId -> clientOrderId
     * This allows fast lookup by exchange orderId
     */
    private final ConcurrentHashMap<Long, String> clientIdByOrderId;

    public StateManager() {
        this.ordersByClientId = new ConcurrentHashMap<>();
        this.clientIdByOrderId = new ConcurrentHashMap<>();
    }

    /**
     * Add a new order to state.
     *
     * @param order Order to add
     */
    public void addOrder(Order order) {
        if (order == null || order.getClientOrderId() == null) {
            throw new IllegalArgumentException("Order and clientOrderId must not be null");
        }

        ordersByClientId.put(order.getClientOrderId(), order);

        // Add to secondary index if orderId is set
        if (order.getOrderId() != null) {
            clientIdByOrderId.put(order.getOrderId(), order.getClientOrderId());
        }

        logger.info("Added order: clientOrderId={}, orderId={}, status={}", order.getClientOrderId(),
                order.getOrderId(), order.getStatus());
    }

    /**
     * Update an existing order.
     *
     * If order has orderId and it's not in the index, add it.
     *
     * @param order Updated order
     */
    public void updateOrder(Order order) {
        if (order == null || order.getClientOrderId() == null) {
            throw new IllegalArgumentException("Order and clientOrderId must not be null");
        }

        ordersByClientId.put(order.getClientOrderId(), order);

        // Update secondary index if orderId is set
        if (order.getOrderId() != null) {
            clientIdByOrderId.put(order.getOrderId(), order.getClientOrderId());
        }

        logger.info("Updated order: clientOrderId={}, orderId={}, status={}", order.getClientOrderId(),
                order.getOrderId(), order.getStatus());
    }

    /**
     * Get order by client order ID.
     *
     * @param clientOrderId Client order ID
     * @return Order or null if not found
     */
    public Order getOrderByClientId(String clientOrderId) {
        return ordersByClientId.get(clientOrderId);
    }

    /**
     * Get order by exchange order ID.
     *
     * @param orderId Exchange order ID
     * @return Order or null if not found
     */
    public Order getOrderByOrderId(Long orderId) {
        String clientOrderId = clientIdByOrderId.get(orderId);
        if (clientOrderId == null) {
            return null;
        }
        return ordersByClientId.get(clientOrderId);
    }

    /**
     * Get order by either client order ID or exchange order ID.
     *
     * Tries to parse as Long (orderId) first, then falls back to clientOrderId.
     *
     * @param id Order ID (can be either type)
     * @return Order or null if not found
     */
    public Order getOrder(String id) {
        // Try as orderId first
        try {
            Long orderId = Long.parseLong(id);
            Order order = getOrderByOrderId(orderId);
            if (order != null) {
                return order;
            }
        } catch (NumberFormatException e) {
            // Not a number, try as clientOrderId
        }
        return getOrderByClientId(id);
    }

    /**
     * Get open orders (NEW or PARTIALLY_FILLED).
     *
     * @return List of open orders
     */
    public List<Order> getOpenOrders() {
        return ordersByClientId.values().stream()
                .filter(order -> order.getStatus() == OrderStatus.NEW ||
                        order.getStatus() == OrderStatus.PARTIALLY_FILLED)
                .collect(Collectors.toList());
    }

    /**
     * Get open orders for a specific symbol.
     *
     * @param symbol Trading pair
     * @return List of open orders for this symbol
     */
    public List<Order> getOpenOrders(String symbol) {
        return ordersByClientId.values().stream()
                .filter(order -> order.getSymbol().equals(symbol))
                .filter(order -> order.isActive())
                .collect(Collectors.toList());
    }

    /**
     * Remove an order from state.
     *
     * Used for cleanup of terminal orders.
     *
     * @param clientOrderId Client order ID
     * @return Removed order or null if not found
     */
    public Order removeOrder(String clientOrderId) {
        Order order = ordersByClientId.remove(clientOrderId);

        if (order != null && order.getOrderId() != null) {
            clientIdByOrderId.remove(order.getOrderId());
            logger.debug("Removed order: clientOrderId={}, orderId={}",
                    clientOrderId, order.getOrderId());
        }

        return order;
    }

    /**
     * Get current state snapshot for persistence.
     *
     * Returns a copy of the orders map.
     *
     * @return Map of clientOrderId -> Order
     */
    public Map<String, Order> getStateSnapshot() {
        return new HashMap<>(ordersByClientId);
    }

    /**
     * Load state from snapshot (e.g., from disk).
     *
     * Replaces current state entirely.
     *
     * @param orders Map of clientOrderId -> Order
     */
    public void loadState(Map<String, Order> orders) {
        ordersByClientId.clear();
        clientIdByOrderId.clear();

        orders.values().stream()
                .filter(Objects::nonNull)
                .forEach(this::addOrder);

        logger.info("Loaded {} orders into state", ordersByClientId.size());
    }
}
