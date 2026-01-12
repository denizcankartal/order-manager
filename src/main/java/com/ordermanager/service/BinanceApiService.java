package com.ordermanager.service;

import com.ordermanager.client.BinanceRestClient;
import com.ordermanager.model.dto.AccountResponse;
import com.ordermanager.model.dto.ExchangeInfoResponse;
import com.ordermanager.model.dto.OrderResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public class BinanceApiService {

    private static final Logger logger = LoggerFactory.getLogger(BinanceApiService.class);

    private final BinanceRestClient restClient;

    public BinanceApiService(BinanceRestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Get account information including balances.
     * 
     * Endpoint: GET /api/v3/account (SIGNED)
     * 
     * @return Account information with balances
     */
    public AccountResponse getAccount() {
        logger.debug("Fetching account information");

        AccountResponse response = restClient.getSigned("/api/v3/account", new HashMap<>(), AccountResponse.class);

        logger.info("Account fetched: accountType={}, balances={}, canTrade={}",
                response.getAccountType(),
                response.getBalances() != null ? response.getBalances().size() : 0,
                response.isCanTrade());

        return response;
    }

    /**
     * Get exchange information including symbol filters.
     * 
     * Endpoint: GET /api/v3/exchangeInfo (PUBLIC)
     * 
     * @return Exchange information with symbol details
     */
    public ExchangeInfoResponse getExchangeInfo() {
        logger.debug("Fetching exchange information");

        ExchangeInfoResponse response = restClient.get("/api/v3/exchangeInfo", ExchangeInfoResponse.class);

        logger.info("Exchange info fetched: timezone={}, symbols={}, serverTime={}",
                response.getTimezone(),
                response.getSymbols() != null ? response.getSymbols().size() : 0,
                response.getServerTime());

        return response;
    }

    /**
     * Place a new LIMIT order.
     *
     * Endpoint: POST /api/v3/order (SIGNED)
     *
     * @param symbol        Trading pair (e.g., "BTCUSDT")
     * @param side          Order side ("BUY" or "SELL")
     * @param price         Order price
     * @param quantity      Order quantity
     * @param clientOrderId Client order ID
     * @return Order response with orderId and status
     */
    public OrderResponse placeOrder(String symbol, String side, BigDecimal price, BigDecimal quantity,
            String clientOrderId) {
        logger.debug("Placing order: {} {} {} @ {}, clientOrderId={}", side, quantity, symbol, price, clientOrderId);

        Map<String, String> params = new HashMap<>();
        params.put("symbol", symbol);
        params.put("side", side);
        params.put("type", "LIMIT");
        params.put("timeInForce", "GTC");
        params.put("quantity", quantity.toPlainString());
        params.put("price", price.toPlainString());

        if (clientOrderId != null && !clientOrderId.isEmpty()) {
            params.put("newClientOrderId", clientOrderId);
        }

        OrderResponse response = restClient.postSigned("/api/v3/order", params, OrderResponse.class);

        logger.info("Order placed: orderId={}, clientOrderId={}, status={}", response.getOrderId(),
                response.getClientOrderId(), response.getStatus());

        return response;
    }

    /**
     * Cancel an existing order.
     *
     * Endpoint: DELETE /api/v3/order (SIGNED)
     *
     * @param symbol        Trading pair
     * @param orderId       Exchange order ID (optional if clientOrderId provided)
     * @param clientOrderId Client order ID (optional if orderId provided)
     * @return Order response with cancellation status
     */
    public OrderResponse cancelOrder(String symbol, Long orderId, String clientOrderId) {
        logger.debug("Canceling order: symbol={}, orderId={}, clientOrderId={}", symbol, orderId, clientOrderId);

        Map<String, String> params = new HashMap<>();
        params.put("symbol", symbol);

        if (orderId != null) {
            params.put("orderId", String.valueOf(orderId));
        }

        if (clientOrderId != null && !clientOrderId.isEmpty()) {
            params.put("origClientOrderId", clientOrderId);
        }

        OrderResponse response = restClient.deleteSigned("/api/v3/order", params, OrderResponse.class);

        logger.info("Order canceled: orderId={}, clientOrderId={}, status={}",
                response.getOrderId(), response.getClientOrderId(), response.getStatus());

        return response;
    }

    /**
     * Query order status.
     *
     * Endpoint: GET /api/v3/order (SIGNED)
     *
     * @param symbol        Trading pair
     * @param orderId       Exchange order ID (optional if clientOrderId provided)
     * @param clientOrderId Client order ID (optional if orderId provided)
     * @return Order response with current status
     */
    public OrderResponse getOrder(String symbol, Long orderId, String clientOrderId) {
        logger.debug("Querying order: symbol={}, orderId={}, clientOrderId={}", symbol, orderId, clientOrderId);

        Map<String, String> params = new HashMap<>();
        params.put("symbol", symbol);

        if (orderId != null) {
            params.put("orderId", String.valueOf(orderId));
        }

        if (clientOrderId != null && !clientOrderId.isEmpty()) {
            params.put("origClientOrderId", clientOrderId);
        }

        OrderResponse response = restClient.getSigned("/api/v3/order", params, OrderResponse.class);

        logger.debug("Order status: orderId={}, status={}, executedQty={}",
                response.getOrderId(), response.getStatus(), response.getExecutedQty());

        return response;
    }

    /**
     * Shutdown the underlying REST client.
     * Should be called when the service is no longer needed.
     */
    public void shutdown() {
        restClient.shutdown();
        logger.info("BinanceApiService shut down");
    }
}
