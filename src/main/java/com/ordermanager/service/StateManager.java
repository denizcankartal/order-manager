package com.ordermanager.service;

import com.ordermanager.model.Order;
import com.ordermanager.repository.OrdersRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class StateManager {

    private static final Logger logger = LoggerFactory.getLogger(StateManager.class);

    private final ConcurrentHashMap<String, Order> ordersByClientId;
    private final ConcurrentHashMap<Long, String> clientIdByOrderId;

    private final OrdersRepository ordersRepository;

    public StateManager(OrdersRepository ordersRepository) {
        this.ordersRepository = ordersRepository;
        this.ordersByClientId = new ConcurrentHashMap<>();
        this.clientIdByOrderId = new ConcurrentHashMap<>();
    }

    public void addOrder(Order order) {
        if (order == null || order.getClientOrderId() == null) {
            throw new IllegalArgumentException("Order and clientOrderId must not be null");
        }

        ordersByClientId.put(order.getClientOrderId(), order);
        if (order.getOrderId() != null) {
            clientIdByOrderId.put(order.getOrderId(), order.getClientOrderId());
        }

        ordersRepository.save(order);

        logger.info("Added order: clientOrderId={}, orderId={}, status={}", order.getClientOrderId(),
                order.getOrderId(), order.getStatus());
    }

    public void updateOrder(Order order) {
        if (order == null || order.getClientOrderId() == null) {
            throw new IllegalArgumentException("Order and clientOrderId must not be null");
        }

        ordersByClientId.put(order.getClientOrderId(), order);
        if (order.getOrderId() != null) {
            clientIdByOrderId.put(order.getOrderId(), order.getClientOrderId());
        }

        ordersRepository.save(order);

        logger.info("Updated order: clientOrderId={}, orderId={}, status={}", order.getClientOrderId(),
                order.getOrderId(), order.getStatus());
    }

    public Order getOrderByClientId(String clientOrderId) {
        return ordersByClientId.get(clientOrderId);
    }

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
        // try as orderId first
        try {
            Long orderId = Long.parseLong(id);
            Order order = getOrderByOrderId(orderId);
            if (order != null) {
                return order;
            }
        } catch (NumberFormatException e) {
            // not a number, try as clientOrderId
        }
        return getOrderByClientId(id);
    }

    public List<Order> getOpenOrders() {
        return new ArrayList<>(ordersByClientId.values());
    }

    public void loadStateFromRepository(String symbol) {
        ordersByClientId.clear();
        clientIdByOrderId.clear();

        for (Order order : ordersRepository.findOpenOrders(symbol)) {
            addOrder(order);
        }
        logger.info("Loaded {} orders into state", ordersByClientId.size());
    }
}
