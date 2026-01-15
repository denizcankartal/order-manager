package com.ordermanager.repository;

import com.ordermanager.model.Order;

import java.util.List;
import java.util.Optional;

public interface OrdersRepository {
    void save(Order order);

    Optional<Order> findByClientOrderId(String clientOrderId);

    Optional<Order> findByOrderId(Long orderId);

    List<Order> findOpenOrders(String symbol);
}
