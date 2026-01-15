package com.ordermanager.repository;

import com.ordermanager.model.Order;
import com.ordermanager.model.OrderStatus;
import com.ordermanager.model.OrderSide;
import org.jdbi.v3.core.Jdbi;

import java.util.List;
import java.util.Optional;

public class JdbcOrdersRepository implements OrdersRepository {

    private final Jdbi jdbi;

    public JdbcOrdersRepository(Jdbi jdbi) {
        this.jdbi = jdbi;

        this.jdbi.registerRowMapper(Order.class, (rs, ctx) -> {
            Order o = new Order();
            o.setClientOrderId(rs.getString("client_order_id"));
            Long orderId = rs.getLong("order_id");
            if (!rs.wasNull()) {
                o.setOrderId(orderId);
            }
            o.setSymbol(rs.getString("symbol"));
            o.setSide(OrderSide.valueOf(rs.getString("side")));
            o.setPrice(rs.getBigDecimal("price"));
            o.setOrigQty(rs.getBigDecimal("orig_qty"));
            o.setExecutedQty(rs.getBigDecimal("executed_qty"));
            o.setStatus(OrderStatus.valueOf(rs.getString("status")));
            o.setTime(rs.getLong("time"));
            o.setUpdateTime(rs.getLong("update_time"));
            return o;
        });
    }

    @Override
    public void save(Order order) {
        jdbi.useHandle(h -> h.createUpdate("""
                INSERT INTO orders (
                    client_order_id, order_id, symbol, side,
                    price, orig_qty, executed_qty,
                    status, time, update_time
                ) VALUES (
                    :clientOrderId, :orderId, :symbol, :side,
                    :price, :origQty, :executedQty,
                    :status, :time, :updateTime
                )
                ON CONFLICT (client_order_id) DO UPDATE SET
                    order_id     = EXCLUDED.order_id,
                    symbol       = EXCLUDED.symbol,
                    side         = EXCLUDED.side,
                    price        = EXCLUDED.price,
                    orig_qty     = EXCLUDED.orig_qty,
                    executed_qty = EXCLUDED.executed_qty,
                    status       = EXCLUDED.status,
                    time         = EXCLUDED.time,
                    update_time  = EXCLUDED.update_time,
                    last_modified = NOW()
                """)
                .bind("clientOrderId", order.getClientOrderId())
                .bind("orderId", order.getOrderId())
                .bind("symbol", order.getSymbol())
                .bind("side", order.getSide().name())
                .bind("price", order.getPrice())
                .bind("origQty", order.getOrigQty())
                .bind("executedQty", order.getExecutedQty())
                .bind("status", order.getStatus().name())
                .bind("time", order.getTime())
                .bind("updateTime", order.getUpdateTime())
                .execute());
    }

    @Override
    public Optional<Order> findByClientOrderId(String clientOrderId) {
        return jdbi.withHandle(h -> h.createQuery("SELECT * FROM orders WHERE client_order_id = :id")
                .bind("id", clientOrderId)
                .mapTo(Order.class)
                .findOne());
    }

    @Override
    public Optional<Order> findByOrderId(Long orderId) {
        return jdbi.withHandle(h -> h.createQuery("SELECT * FROM orders WHERE order_id = :id")
                .bind("id", orderId)
                .mapTo(Order.class)
                .findOne());
    }

    @Override
    public List<Order> findOpenOrders(String symbol) {
        return jdbi.withHandle(h -> h.createQuery("""
                SELECT * FROM orders
                WHERE symbol = :symbol
                  AND (status = 'NEW' OR status = 'PARTIALLY_FILLED')
                """)
                .bind("symbol", symbol)
                .mapTo(Order.class)
                .list());
    }
}
