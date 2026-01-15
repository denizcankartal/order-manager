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
import java.util.List;
import java.util.Map;

public class OrderService {

    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);

    private final BinanceRestClient restClient;
    private final StateManager stateManager;
    private final String baseAsset;
    private final String quoteAsset;
    private SymbolInfo symbolInfo;
    private final String symbol;

    public OrderService(BinanceRestClient restClient,
            StateManager stateManager,
            String baseAsset,
            String quoteAsset) {
        this.restClient = restClient;
        this.stateManager = stateManager;
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
        order.setOrderId(null); // no exchange ID yet

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

            stateManager.addOrder(order);

            return new PlaceOrderResult(order, validation.getWarnings());

        } catch (ApiException e) {
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
            throw e;
        }
    }

    public BigDecimal getCurrentPrice(String symbol) {
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
        if (order == null) {
            throw new IllegalStateException(String.format(
                    "Order not found for id=%s; it may already be closed or never existed.", id));
        }

        if (!order.isActive()) {
            throw new IllegalStateException(
                    String.format("Order cannot be CANCELLED because it is %s.", order.getStatus().toString()));
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

            stateManager.updateOrder(order);

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
     * @param symbol Trading pair (null for all symbols)
     * @return List of open orders
     */
    public List<Order> listOpenOrders(String symbol) {
        return stateManager.getOpenOrders(symbol);
    }

    public Order getOrder(String id) {
        Order localOrder = stateManager.getOrder(id);
        if (localOrder == null) {
            throw new IllegalStateException(String.format(
                    "Order not found for id=%s; it may already be closed or never existed.", id));
        }
        return localOrder;
    }

    private String generateClientOrderId() {
        long timestamp = System.currentTimeMillis();
        return String.format("cli-%d-%s", timestamp, java.util.UUID.randomUUID().toString().substring(0, 8));
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
