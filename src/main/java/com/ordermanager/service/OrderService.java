package com.ordermanager.service;

import com.ordermanager.client.BinanceRestClient;
import com.ordermanager.exception.ApiException;
import com.ordermanager.exception.BinanceErrorType;
import com.ordermanager.model.Order;
import com.ordermanager.model.OrderSide;
import com.ordermanager.model.OrderStatus;
import com.ordermanager.model.PlaceOrderResult;
import com.ordermanager.model.SymbolInfo;
import com.ordermanager.model.dto.ExchangeInfoResponse;
import com.ordermanager.model.dto.OrderResponse;
import com.ordermanager.model.dto.TickerPriceResponse;
import com.ordermanager.model.filter.LotSizeFilter;
import com.ordermanager.model.filter.MinNotionalFilter;
import com.ordermanager.model.filter.PercentPriceBySideFilter;
import com.ordermanager.model.filter.PriceFilter;
import com.ordermanager.util.RetryUtils;
import com.ordermanager.validator.OrderValidator;
import com.ordermanager.validator.OrderValidator.OrderValidationResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
    private final String baseAsset;
    private final String quoteAsset;
    private SymbolInfo symbolInfo;
    private final String symbol;

    public OrderService(BinanceRestClient restClient,
            StateManager stateManager,
            AsyncStatePersister persister,
            String baseAsset,
            String quoteAsset) {
        this.restClient = restClient;
        this.stateManager = stateManager;
        this.persister = persister;
        this.baseAsset = baseAsset;
        this.quoteAsset = quoteAsset;
        this.symbol = baseAsset + quoteAsset;
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
    public PlaceOrderResult placeOrder(OrderSide side, BigDecimal price, BigDecimal quantity,
            String userProvidedClientId) {

        if (userProvidedClientId != null && !userProvidedClientId.isEmpty()
                && stateManager.getOrderByClientId(userProvidedClientId) != null) {
            throw new IllegalStateException(String.format(
                    "Duplicate order sent (clientId=%s). Use a new --client-id.",
                    userProvidedClientId));
        }

        BigDecimal originalPrice = price;
        BigDecimal originalQuantity = quantity;
        String clientOrderId = (userProvidedClientId != null && !userProvidedClientId.isEmpty())
                ? userProvidedClientId
                : generateClientOrderId();

        BigDecimal referencePrice = null;
        try {
            referencePrice = getCurrentPrice(symbol);
        } catch (Exception e) {
            logger.warn("Could not fetch reference price for {}: {}. Skipping PERCENT_PRICE_BY_SIDE validation.",
                    symbol, e.getMessage());
        }

        if (symbolInfo == null) {
            loadSymbolInfo();
        }

        OrderValidationResult validation = OrderValidator.validate(symbol, side, quantity, price, symbolInfo,
                referencePrice);

        if (!validation.isValid()) {
            String errors = String.join("; ", validation.getErrors());
            throw new IllegalArgumentException("Order validation failed: " + errors);
        }

        BigDecimal validatedPrice = validation.getAdjustedPrice();
        BigDecimal validatedQuantity = validation.getAdjustedQuantity();

        Order order = new Order(clientOrderId, symbol, side, validatedPrice, validatedQuantity);
        order.setStatus(OrderStatus.PENDING_NEW);
        order.setOrderId(null); // No exchange ID yet

        stateManager.addOrder(order);
        persister.submitWrite(stateManager.getStateSnapshot());

        Map<String, String> params = new HashMap<>();
        params.put("symbol", symbol);
        params.put("side", side.name());
        params.put("type", "LIMIT");
        params.put("timeInForce", "GTC");
        params.put("quantity", validatedQuantity.toPlainString());
        params.put("price", validatedPrice.toPlainString());
        params.put("newClientOrderId", clientOrderId);

        try {
            OrderResponse response = RetryUtils.executeWithRetry(
                    () -> restClient.postSigned("/api/v3/order", params, OrderResponse.class), "place order", logger);

            order.setOrderId(response.getOrderId());
            order.setStatus(OrderStatus.valueOf(response.getStatus()));
            order.setExecutedQty(response.getExecutedQtyAsBigDecimal());
            order.setTime(response.getTransactTime());
            order.setUpdateTime(System.currentTimeMillis());

            stateManager.updateOrder(order);
            persister.submitWrite(stateManager.getStateSnapshot());

            return new PlaceOrderResult(order, validation.getWarnings());

        } catch (ApiException e) {
            if (order.isLocal()) {
                // remove just added order from state
                stateManager.removeOrder(order.getClientOrderId());
                persister.submitWrite(stateManager.getStateSnapshot());
            }
            BinanceErrorType type = e.getErrorType();
            switch (type) {
                case DUPLICATE_ORDER:
                    throw new IllegalStateException(String.format(
                            "Duplicate order sent (clientId=%s). Use a new --client-id.",
                            clientOrderId));
                case INSUFFICIENT_BALANCE:
                    String asset = side == OrderSide.BUY ? quoteAsset : baseAsset;
                    throw new IllegalStateException(String.format(
                            "Insufficient %s balance. Reduce --qty or --price and try again.", asset));
                case FILTER_VIOLATION:
                    throw new IllegalArgumentException(String.format(
                            "Filter violation for %s: %s. %s%s",
                            symbol,
                            e.getMessage(),
                            formatSymbolFilters(symbolInfo),
                            formatValidationAdjustments(validation, originalPrice, originalQuantity)));
                default:
                    throw new RuntimeException(String.format(
                            "Failed to place order: %s (error code: %d)", e.getMessage(), e.getStatusCode()), e);
            }
        } catch (RuntimeException e) {
            if (order.isLocal()) {
                // remove just added order from state
                stateManager.removeOrder(order.getClientOrderId());
                persister.submitWrite(stateManager.getStateSnapshot());
            }
            throw e;
        }
    }

    private BigDecimal getCurrentPrice(String symbol) {
        String endpoint = String.format("/api/v3/ticker/price?symbol=%s", symbol);
        TickerPriceResponse response = RetryUtils.executeWithRetry(
                () -> restClient.get(endpoint, TickerPriceResponse.class),
                "fetch ticker price",
                logger);

        return response.getPriceAsBigDecimal();
    }

    private void loadSymbolInfo() {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol must be configured to load symbol info");
        }
        if (symbolInfo != null) {
            return;
        }

        try {
            ExchangeInfoResponse response = RetryUtils.executeWithRetry(
                    () -> restClient.get("/api/v3/exchangeInfo?symbol=" + symbol, ExchangeInfoResponse.class),
                    "load exchange info", logger);

            if (response == null || response.getSymbols() == null) {
                logger.warn("Exchange info response contains no symbols");
                return;
            }

            for (SymbolInfo symbolInfo : response.getSymbols()) {
                if (symbolInfo.getSymbol() != null && symbolInfo.getSymbol().equals(this.symbol)) {
                    this.symbolInfo = symbolInfo;
                    return;
                }
            }
            logger.warn("Exchange info did not include symbol {}", symbol);
        } catch (ApiException e) {
            throw new RuntimeException(String.format(
                    "Failed to load symbol info: %s (error code: %d)", e.getMessage(), e.getStatusCode()), e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize exchange info cache", e);
        }
    }

    /**
     * Cancel an existing order.
     *
     * @param id     Order ID (exchange orderId or clientOrderId)
     * @param symbol
     * @return Canceled order
     */
    public Order cancelOrder(String id, String symbol) {
        Order order = stateManager.getOrder(id);
        if (order != null && order.isTerminal()) {
            logger.info("Order already in terminal state: {}, status={}", id, order.getStatus());
            logger.error("Terminal order should not be in internal state: id={}, symbol={}", id, order.getSymbol());
            return order;
        }

        order = fetchAndUpdateOrder(id, symbol);

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
            OrderResponse response = RetryUtils.executeWithRetry(
                    () -> restClient.deleteSigned("/api/v3/order", params, OrderResponse.class),
                    "cancel order", logger);

            order.setStatus(OrderStatus.valueOf(response.getStatus()));
            order.setExecutedQty(response.getExecutedQtyAsBigDecimal());
            order.setUpdateTime(System.currentTimeMillis());

            System.out.println("CANCEL ORDER: " + order);
            if (order.isTerminal()) {
                stateManager.removeOrder(order.getClientOrderId());
            } else {
                stateManager.updateOrder(order);
            }
            persister.submitWrite(stateManager.getStateSnapshot());

            return order;

        } catch (ApiException e) {
            logger.error("Failed to cancel order: id={}, symbol={}, error={}",
                    id, order.getSymbol(), e.getMessage());

            BinanceErrorType type = e.getErrorType();
            switch (type) {
                case ORDER_NOT_FOUND:
                    throw new IllegalStateException(String.format(
                            "Order not found for id=%s; it may already be closed or never existed.", id));
                default:
                    throw new RuntimeException(String.format(
                            "Failed to cancel order %s: %s (error code: %d)", id, e.getMessage(),
                            e.getStatusCode()), e);
            }
        }
    }

    /**
     * List open orders from local state.
     * 
     * For fresh data, use refreshOpenOrders first.
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
     * Refresh open orders from the exchange and reconcile local state.
     */
    public void refreshOpenOrders() {
        OrderResponse[] orderResponses;
        try {
            orderResponses = RetryUtils.executeWithRetry(
                    () -> restClient.getSigned("/api/v3/openOrders", new HashMap<>(), OrderResponse[].class),
                    "refresh open orders", logger);
        } catch (ApiException e) {
            throw new RuntimeException(String.format(
                    "Failed to refresh open orders: %s (error code: %d)", e.getMessage(), e.getStatusCode()), e);
        }

        Set<String> openOrderClientIds = new HashSet<>();

        boolean anyChanged = false;

        for (OrderResponse response : orderResponses) {
            Order order = stateManager.getOrder(response.getClientOrderId());
            boolean orderChanged = false;
            if (order == null) {
                order = new Order(response.getClientOrderId(),
                        response.getSymbol(),
                        OrderSide.valueOf(response.getSide()),
                        response.getPriceAsBigDecimal(),
                        response.getOrigQtyAsBigDecimal());
                orderChanged = true;
            } else {
                Long localUpdateTime = order.getUpdateTime();
                Long remoteUpdateTime = response.getUpdateTime();

                if (!Objects.equals(localUpdateTime, remoteUpdateTime)) {
                    orderChanged = true;
                }
            }

            if (orderChanged) {
                order.setOrderId(response.getOrderId());
                order.setStatus(OrderStatus.valueOf(response.getStatus()));
                order.setExecutedQty(response.getExecutedQtyAsBigDecimal());
                order.setUpdateTime(
                        response.getUpdateTime() != null ? response.getUpdateTime() : System.currentTimeMillis());
                stateManager.updateOrder(order);
                anyChanged = true;
            }
            openOrderClientIds.add(order.getClientOrderId());
        }

        // Any locally active orders not returned by the exchange are now terminal
        // including new pending orders
        List<Order> active = stateManager.getOpenOrders();

        for (Order o : active) {
            if (!openOrderClientIds.contains(o.getClientOrderId())) {
                stateManager.removeOrder(o.getClientOrderId());
                anyChanged = true;
            }
        }

        int pruned = stateManager.pruneTerminalOrders();
        if (pruned > 0) {
            anyChanged = true;
        }

        if (anyChanged) {
            persister.submitWrite(stateManager.getStateSnapshot());
        }
    }

    /**
     * Fetch an order from the exchange, update local state, and return it.
     *
     * @param id     orderId or clientOrderId
     * @param symbol
     */
    public Order fetchAndUpdateOrder(String id, String symbol) {
        Order localOrder = stateManager.getOrder(id);

        Map<String, String> params = new HashMap<>();
        params.put("symbol", symbol);

        if (localOrder != null) {
            if (localOrder.getOrderId() != null) {
                params.put("orderId", String.valueOf(localOrder.getOrderId()));
            }
            if (localOrder.getClientOrderId() != null && !localOrder.getClientOrderId().isEmpty()) {
                params.put("origClientOrderId", localOrder.getClientOrderId());
            }
        } else {
            // Order is not in local
            try {
                Long orderId = Long.parseLong(id);
                params.put("orderId", String.valueOf(orderId));
            } catch (NumberFormatException e) {
                params.put("origClientOrderId", id);
            }
        }

        try {
            OrderResponse response = RetryUtils.executeWithRetry(
                    () -> restClient.getSigned("/api/v3/order", params, OrderResponse.class),
                    "sync order", logger);

            Long responseUpdateTime = response.getUpdateTime();
            if (localOrder != null && localOrder.getUpdateTime() != null
                    && Objects.equals(responseUpdateTime, localOrder.getUpdateTime())) {
                logger.debug(
                        "fetchAndUpdateOrder - Order {} unchanged (updateTime {}), skipping state update and persistence",
                        id, responseUpdateTime);
                return localOrder;
            }
            Order order = localOrder != null ? localOrder
                    : new Order(
                            response.getClientOrderId(),
                            response.getSymbol(),
                            OrderSide.valueOf(response.getSide()),
                            response.getPriceAsBigDecimal(),
                            response.getOrigQtyAsBigDecimal());

            order.setOrderId(response.getOrderId());
            order.setStatus(OrderStatus.valueOf(response.getStatus()));
            order.setExecutedQty(response.getExecutedQtyAsBigDecimal());
            order.setUpdateTime(
                    response.getUpdateTime() != null ? response.getUpdateTime() : System.currentTimeMillis());
            order.setTime(response.getTransactTime());

            if (order.isTerminal()) {
                // Order became terminal
                stateManager.removeOrder(order.getClientOrderId());
                logger.info("Removed terminal order from state: clientOrderId={}, orderId={}, status={}",
                        order.getClientOrderId(), order.getOrderId(), order.getStatus());
            } else {
                stateManager.updateOrder(order);
            }

            persister.submitWrite(stateManager.getStateSnapshot());

            return order;

        } catch (ApiException e) {
            BinanceErrorType type = e.getErrorType();
            if (type == BinanceErrorType.ORDER_NOT_FOUND) {
                if (localOrder != null && localOrder.isTerminal()) {
                    stateManager.removeOrder(localOrder.getClientOrderId());
                    persister.submitWrite(stateManager.getStateSnapshot());
                    logger.error("Terminal order should not be in internal state: id={}, symbol={}", id,
                            localOrder.getSymbol());
                    return localOrder;
                }
                throw new IllegalStateException(String.format(
                        "Order not found for id=%s; it may already be closed or never existed.", id));
            }
            throw new RuntimeException(String.format(
                    "Failed to sync order %s: %s (error code: %d)", id, e.getMessage(), e.getStatusCode()), e);
        }
    }

    private String generateClientOrderId() {
        long timestamp = System.currentTimeMillis();
        return String.format("cli-%d", timestamp);
    }

    private String formatSymbolFilters(SymbolInfo symbolInfo) {
        if (symbolInfo == null) {
            return "Filters unavailable (no exchange info).";
        }

        PriceFilter price = symbolInfo.getPriceFilter();
        LotSizeFilter lot = symbolInfo.getLotSizeFilter();
        MinNotionalFilter notional = symbolInfo.getMinNotionalFilter();
        PercentPriceBySideFilter percent = symbolInfo.getPercentPriceBySideFilter();

        StringBuilder sb = new StringBuilder("Current filters: ");
        if (price != null) {
            sb.append(String.format("PRICE_FILTER(min=%s, max=%s, tickSize=%s); ",
                    safeDecimal(price.getMinPrice()),
                    safeDecimal(price.getMaxPrice()),
                    safeDecimal(price.getTickSize())));
        }
        if (lot != null) {
            sb.append(String.format("LOT_SIZE(min=%s, max=%s, stepSize=%s); ",
                    safeDecimal(lot.getMinQty()),
                    safeDecimal(lot.getMaxQty()),
                    safeDecimal(lot.getStepSize())));
        }
        if (notional != null) {
            sb.append(String.format("MIN_NOTIONAL(min=%s); ", safeDecimal(notional.getMinNotional())));
        }
        if (percent != null) {
            sb.append(String.format("PERCENT_PRICE_BY_SIDE(bidDown=%s, bidUp=%s, askDown=%s, askUp=%s); ",
                    safeDecimal(percent.getBidMultiplierDown()),
                    safeDecimal(percent.getBidMultiplierUp()),
                    safeDecimal(percent.getAskMultiplierDown()),
                    safeDecimal(percent.getAskMultiplierUp())));
        }

        return sb.toString().trim();
    }

    private String formatValidationAdjustments(OrderValidationResult validation, BigDecimal originalPrice,
            BigDecimal originalQuantity) {
        if (validation == null) {
            return "";
        }

        BigDecimal adjustedPrice = validation.getAdjustedPrice();
        BigDecimal adjustedQty = validation.getAdjustedQuantity();

        boolean priceAdjusted = originalPrice != null && adjustedPrice != null
                && originalPrice.compareTo(adjustedPrice) != 0;
        boolean qtyAdjusted = originalQuantity != null && adjustedQty != null
                && originalQuantity.compareTo(adjustedQty) != 0;

        if (!priceAdjusted && !qtyAdjusted) {
            return "";
        }

        StringBuilder sb = new StringBuilder(" Adjusted values: ");
        if (priceAdjusted) {
            sb.append(String.format("price %s -> %s", originalPrice.toPlainString(), adjustedPrice.toPlainString()));
        }
        if (qtyAdjusted) {
            if (priceAdjusted) {
                sb.append(", ");
            }
            sb.append(String.format("qty %s -> %s", originalQuantity.toPlainString(), adjustedQty.toPlainString()));
        }
        sb.append(".");

        return sb.toString();
    }

    private String safeDecimal(BigDecimal value) {
        return value != null ? value.toPlainString() : "n/a";
    }
}
