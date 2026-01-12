package com.ordermanager.service;

import com.ordermanager.client.BinanceRestClient;
import com.ordermanager.exception.ApiException;
import com.ordermanager.model.Order;
import com.ordermanager.model.OrderSide;
import com.ordermanager.model.OrderStatus;
import com.ordermanager.model.SymbolInfo;
import com.ordermanager.model.dto.OrderResponse;
import com.ordermanager.util.RetryUtils;
import com.ordermanager.validator.OrderValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for order management operations.
 *
 * Responsibilities:
 * - Place LIMIT orders (validate -> add to state -> send to exchange)
 * - Cancel orders (idempotent)
 * - Query order status
 * - List open orders
 * - Sync local state with exchange
 */
public class OrderService {

    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);

    private final BinanceRestClient restClient;
    private final StateManager stateManager;
    private final AsyncStatePersister persister;
    private final ExchangeInfoService exchangeInfoService;

    public OrderService(BinanceRestClient restClient,
            StateManager stateManager,
            AsyncStatePersister persister,
            ExchangeInfoService exchangeInfoService) {
        this.restClient = restClient;
        this.stateManager = stateManager;
        this.persister = persister;
        this.exchangeInfoService = exchangeInfoService;
    }

    /**
     * Place a new LIMIT order.
     *
     * @param symbol               Trading pair (e.g., "BTCUSDT")
     * @param side                 BUY or SELL
     * @param price                Limit price
     * @param quantity             Order quantity
     * @param userProvidedClientId Optional client order ID (null for auto-generate)
     * @return Order with exchange orderId and status
     */
    public PlaceOrderResult placeOrder(String symbol, OrderSide side, BigDecimal price,
            BigDecimal quantity, String userProvidedClientId) {

        String clientOrderId = (userProvidedClientId != null && !userProvidedClientId.isEmpty())
                ? userProvidedClientId
                : generateClientOrderId();

        logger.info("Placing order: {} {} {} @ {}, clientOrderId={}",
                side, quantity, symbol, price, clientOrderId);

        SymbolInfo symbolInfo = exchangeInfoService.getSymbolInfo(symbol);
        BigDecimal referencePrice = null;
        try {
            referencePrice = exchangeInfoService.getCurrentPrice(symbol);
        } catch (Exception e) {
            logger.warn("Could not fetch reference price for {}: {}. Skipping PERCENT_PRICE_BY_SIDE validation.",
                    symbol, e.getMessage());
        }

        OrderValidator.OrderValidationResult validation = OrderValidator.validate(
                symbol, side, quantity, price, symbolInfo, referencePrice);

        if (!validation.isValid()) {
            String errors = String.join("; ", validation.getErrors());
            logger.error("Order validation failed: {}", errors);
            throw new IllegalArgumentException("Order validation failed: " + errors);
        }

        if (validation.hasWarnings()) {
            validation.getWarnings().forEach(logger::warn);
        }

        BigDecimal validatedPrice = validation.getAdjustedPrice();
        BigDecimal validatedQuantity = validation.getAdjustedQuantity();

        Order order = new Order(clientOrderId, symbol, side, validatedPrice, validatedQuantity);
        order.setStatus(OrderStatus.PENDING_NEW);
        order.setOrderId(null); // No exchange ID yet

        stateManager.addOrder(order);
        persister.submitWrite(stateManager.getStateSnapshot());

        logger.debug("Order added to local state: {}", clientOrderId);

        Map<String, String> params = new HashMap<>();
        params.put("symbol", symbol);
        params.put("side", side.name());
        params.put("type", "LIMIT");
        params.put("timeInForce", "GTC");
        params.put("quantity", validatedQuantity.toPlainString());
        params.put("price", validatedPrice.toPlainString());

        if (clientOrderId != null && !clientOrderId.isEmpty()) {
            params.put("newClientOrderId", clientOrderId);
        }

        try {
            OrderResponse response = RetryUtils.executeWithRetry(() ->
                restClient.postSigned("/api/v3/order", params, OrderResponse.class),
                "place order", logger);

            order.setOrderId(response.getOrderId());
            order.setStatus(OrderStatus.valueOf(response.getStatus()));
            order.setExecutedQty(response.getExecutedQtyAsBigDecimal());
            order.setTime(response.getTransactTime());
            order.setUpdateTime(System.currentTimeMillis());

            stateManager.updateOrder(order);
            persister.submitWrite(stateManager.getStateSnapshot());

            logger.info("Order placed successfully: orderId={}, status={}",
                    order.getOrderId(), order.getStatus());

            return new PlaceOrderResult(order, validation.getWarnings());

        } catch (ApiException e) {
            logger.error("Failed to place order: symbol={}, side={}, price={}, qty={}, error={}",
                    symbol, side, validatedPrice, validatedQuantity, e.getMessage());

            if (e.isInsufficientBalance()) {
                String asset = side == OrderSide.BUY ? symbol.substring(symbol.length() - 4) : symbol.substring(0, symbol.length() - 4);
                throw new IllegalStateException(String.format(
                        "Insufficient %s balance. Reduce --qty or --price and try again.", asset));
            }

            if (e.isFilterViolation()) {
                throw new IllegalArgumentException(String.format(
                        "Filter violation for %s: %s", symbol, e.getMessage()));
            }

            if (e.isTimestampError()) {
                throw new IllegalStateException(
                        "Clock drift detected. Sync system time and retry. Error: " + e.getMessage());
            }

            if (e.isRateLimit()) {
                throw new IllegalStateException(
                        "Rate limit exceeded. Wait 60 seconds and retry. Error: " + e.getMessage());
            }

            throw new RuntimeException(String.format(
                    "Failed to place order: %s (error code: %d)", e.getMessage(), e.getStatusCode()), e);
        }
    }

    /**
     * Cancel an existing order.
     *
     * Idempotent: Canceling an already-canceled order returns success.
     *
     * @param id Order ID (exchange orderId or clientOrderId)
     * @return Canceled order
     */
    public Order cancelOrder(String id) {
        logger.info("Canceling order: id={}", id);

        Order order = stateManager.getOrder(id);

        if (order == null) {
            throw new IllegalArgumentException("Order not found: " + id);
        }

        if (order.isTerminal()) {
            logger.info("Order already in terminal state: {}, status={}", id, order.getStatus());
            return order;
        }

        Map<String, String> params = new HashMap<>();
        params.put("symbol", order.getSymbol());

        if (order.getOrderId() != null) {
            params.put("orderId", String.valueOf(order.getOrderId()));
        }

        if (order.getClientOrderId() != null && !order.getClientOrderId().isEmpty()) {
            params.put("origClientOrderId", order.getClientOrderId());
        }

        try {
            OrderResponse response = RetryUtils.executeWithRetry(() ->
                restClient.deleteSigned("/api/v3/order", params, OrderResponse.class),
                "cancel order", logger);

            order.setStatus(OrderStatus.valueOf(response.getStatus()));
            order.setExecutedQty(response.getExecutedQtyAsBigDecimal());
            order.setUpdateTime(System.currentTimeMillis());

            stateManager.updateOrder(order);
            persister.submitWrite(stateManager.getStateSnapshot());

            logger.info("Order canceled: orderId={}, status={}, executedQty={}", order.getOrderId(), order.getStatus(),
                    order.getExecutedQty());

            return order;

        } catch (ApiException e) {
            logger.error("Failed to cancel order: id={}, symbol={}, error={}",
                    id, order.getSymbol(), e.getMessage());

            if (e.isRateLimit()) {
                throw new IllegalStateException(
                        "Rate limit exceeded. Wait 60 seconds and retry. Error: " + e.getMessage());
            }

            throw new RuntimeException(String.format(
                    "Failed to cancel order %s: %s (error code: %d)", id, e.getMessage(), e.getStatusCode()), e);
        }
    }

    /**
     * Get order status (query from local state).
     *
     * For fresh data from exchange, use syncWithExchange() first.
     *
     * @param id exchange orderId or clientOrderId
     * @return order from local state
     */
    public Order getOrder(String id) {
        Order order = stateManager.getOrder(id);

        if (order == null) {
            throw new IllegalArgumentException("Order not found: " + id);
        }

        return order;
    }

    /**
     * List open orders from local state.
     *
     * @param symbol Trading pair (null for all symbols)
     * @return List of open orders
     */
    public List<Order> listOpenOrders(String symbol) {
        if (symbol != null && !symbol.isEmpty()) {
            return stateManager.getOpenOrders(symbol);
        } else {
            return stateManager.getOpenOrders();
        }
    }

    /**
     * Sync local state with exchange (reconciliation).
     *
     * Fetches current order status from exchange and updates local state.
     * Use this before show/list commands for fresh data.
     *
     * @param symbol Trading pair
     * @param id     Order ID (exchange orderId or clientOrderId)
     */
    public void syncWithExchange(String symbol, String id) {
        logger.debug("Syncing order with exchange: symbol={}, id={}", symbol, id);

        Order localOrder = stateManager.getOrder(id);

        if (localOrder == null) {
            throw new IllegalArgumentException("Order not found: " + id);
        }

        Map<String, String> params = new HashMap<>();
        params.put("symbol", symbol);

        if (localOrder.getOrderId() != null) {
            params.put("orderId", String.valueOf(localOrder.getOrderId()));
        }

        if (localOrder.getClientOrderId() != null && !localOrder.getClientOrderId().isEmpty()) {
            params.put("origClientOrderId", localOrder.getClientOrderId());
        }

        try {
            OrderResponse response = RetryUtils.executeWithRetry(() ->
                restClient.getSigned("/api/v3/order", params, OrderResponse.class),
                "sync order", logger);

            localOrder.setStatus(OrderStatus.valueOf(response.getStatus()));
            localOrder.setExecutedQty(response.getExecutedQtyAsBigDecimal());
            localOrder.setUpdateTime(System.currentTimeMillis());
            stateManager.updateOrder(localOrder);
            persister.submitWrite(stateManager.getStateSnapshot());

            logger.debug("Order synced from exchange: id={}, status={}", id, localOrder.getStatus());

        } catch (ApiException e) {
            logger.error("Failed to sync order with exchange: symbol={}, id={}, error={}",
                    symbol, id, e.getMessage());

            if (e.isRateLimit()) {
                throw new IllegalStateException(
                        "Rate limit exceeded. Wait 60 seconds and retry. Error: " + e.getMessage());
            }

            throw new RuntimeException(String.format(
                    "Failed to sync order %s: %s (error code: %d)", id, e.getMessage(), e.getStatusCode()), e);
        }
    }

    private String generateClientOrderId() {
        long timestamp = System.currentTimeMillis();
        return String.format("cli-%d", timestamp);
    }
}
