package com.ordermanager.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordermanager.client.BinanceRestClient;
import com.ordermanager.exception.ApiException;
import com.ordermanager.model.Order;
import com.ordermanager.model.OrderSide;
import com.ordermanager.model.OrderStatus;
import com.ordermanager.model.dto.ListenKeyResponse;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class UserDataStreamService {

    private static final Logger logger = LoggerFactory.getLogger(UserDataStreamService.class);
    private static final String USER_DATA_STREAM_ENDPOINT = "/api/v3/userDataStream";

    private final BinanceRestClient restClient;
    private final StateManager stateManager;
    private final AsyncStatePersister persister;
    private final String wsBaseUrl;
    private final int keepAliveMinutes;
    private final OkHttpClient wsClient;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService keepAliveScheduler;
    private final ScheduledExecutorService reconnectScheduler;
    private final Object reconnectLock;

    private WebSocket webSocket;
    private String listenKey;
    private String currentWsUrl;
    private boolean shuttingDown;
    private boolean reconnecting;
    private int reconnectAttempts;

    public UserDataStreamService(BinanceRestClient restClient,
            StateManager stateManager,
            AsyncStatePersister persister,
            String wsBaseUrl,
            int keepAliveMinutes) {
        this.restClient = restClient;
        this.stateManager = stateManager;
        this.persister = persister;
        this.wsBaseUrl = wsBaseUrl;
        this.keepAliveMinutes = keepAliveMinutes;
        this.wsClient = new OkHttpClient.Builder().build();
        this.objectMapper = new ObjectMapper();
        this.keepAliveScheduler = Executors.newSingleThreadScheduledExecutor();
        this.reconnectScheduler = Executors.newSingleThreadScheduledExecutor();
        this.reconnectLock = new Object();
    }

    public void start() {
        ensureListenKey();
        connectWebSocket();
        scheduleKeepAlive();
    }

    public void stop() {
        shuttingDown = true;
        if (webSocket != null) {
            webSocket.close(1000, "shutdown");
        }
        keepAliveScheduler.shutdownNow();
        reconnectScheduler.shutdownNow();
        if (listenKey != null) {
            Map<String, String> params = new HashMap<>();
            params.put("listenKey", listenKey);
            try {
                restClient.deleteApiKey(USER_DATA_STREAM_ENDPOINT, params, Object.class);
            } catch (Exception e) {
                logger.warn("Failed to close user data stream: {}", e.getMessage());
            }
        }
    }

    private void connectWebSocket() {
        ensureListenKey();
        String base = wsBaseUrl.endsWith("/") ? wsBaseUrl : wsBaseUrl + "/";
        currentWsUrl = base + listenKey;
        Request request = new Request.Builder().url(currentWsUrl).build();
        webSocket = wsClient.newWebSocket(request, new UserDataWebSocketListener());
    }

    private void scheduleKeepAlive() {
        keepAliveScheduler.scheduleAtFixedRate(() -> {
            Map<String, String> params = new HashMap<>();
            params.put("listenKey", listenKey);
            try {
                restClient.putApiKey(USER_DATA_STREAM_ENDPOINT, params, Object.class);
                logger.debug("User data stream keepalive sent");
            } catch (ApiException e) {
                logger.warn("User data stream keepalive failed: {}", e.getMessage());
                if (e.isInvalidListenKey()) {
                    restartStream("listenKey invalid");
                }
            } catch (Exception e) {
                logger.warn("User data stream keepalive failed: {}", e.getMessage());
                scheduleReconnect("keepalive failure");
            }
        }, keepAliveMinutes, keepAliveMinutes, TimeUnit.MINUTES);
    }

    private class UserDataWebSocketListener extends WebSocketListener {
        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            logger.info("User data stream connected: {}", currentWsUrl);
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            handleMessage(text);
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            logger.warn("User data stream closed (code={}, reason={})", code, reason);
            scheduleReconnect("closed");
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            String code = response != null ? String.valueOf(response.code()) : "n/a";
            logger.error("User data stream error (code={}): {}", code, t.getMessage());
            if (response != null && response.code() == 404) {
                logger.error("WebSocket endpoint not found. Check BINANCE_WS_BASE_URL.");
                stop();
                return;
            }
            scheduleReconnect("failure");
        }
    }

    private void handleMessage(String text) {
        try {
            JsonNode node = objectMapper.readTree(text);
            String eventType = node.path("e").asText(null);

            if ("executionReport".equals(eventType)) {
                handleExecutionReport(node);
            } else if ("outboundAccountPosition".equals(eventType)) {
                logger.debug("Received account update event");
            }
        } catch (Exception e) {
            logger.warn("Failed to parse user data stream message: {}", e.getMessage());
        }
    }

    private void handleExecutionReport(JsonNode node) {
        String symbol = node.path("s").asText(null);
        String clientOrderId = node.path("c").asText(null);
        long orderId = node.path("i").asLong(0);

        Order order = null;
        if (clientOrderId != null) {
            order = stateManager.getOrderByClientId(clientOrderId);
        }
        if (order == null && orderId > 0) {
            order = stateManager.getOrderByOrderId(orderId);
        }

        if (order == null) {
            if (clientOrderId == null || symbol == null) {
                return;
            }
            OrderSide side = OrderSide.valueOf(node.path("S").asText("BUY"));
            BigDecimal price = decimalFromNode(node, "p");
            BigDecimal origQty = decimalFromNode(node, "q");
            order = new Order(clientOrderId, symbol, side, price, origQty);
        }

        order.setOrderId(orderId > 0 ? orderId : order.getOrderId());
        order.setStatus(OrderStatus.valueOf(node.path("X").asText("NEW")));
        order.setExecutedQty(decimalFromNode(node, "z"));

        long updateTime = node.path("T").asLong(0);
        if (updateTime == 0) {
            updateTime = node.path("E").asLong(0);
        }
        if (updateTime == 0) {
            updateTime = System.currentTimeMillis();
        }
        order.setUpdateTime(updateTime);

        long orderTime = node.path("O").asLong(0);
        if (orderTime > 0) {
            order.setTime(orderTime);
        }

        stateManager.updateOrder(order);
        persister.submitWrite(stateManager.getStateSnapshot());
    }

    private BigDecimal decimalFromNode(JsonNode node, String field) {
        String value = node.path(field).asText("0");
        return new BigDecimal(value);
    }

    private void ensureListenKey() {
        if (listenKey != null && !listenKey.isEmpty()) {
            return;
        }
        ListenKeyResponse response = restClient.postApiKey(USER_DATA_STREAM_ENDPOINT, Collections.emptyMap(),
                ListenKeyResponse.class);
        listenKey = response.getListenKey();
        if (listenKey == null || listenKey.isEmpty()) {
            throw new IllegalStateException("Failed to start user data stream: listenKey missing");
        }
    }

    private void restartStream(String reason) {
        logger.warn("Restarting user data stream ({})", reason);
        listenKey = null;
        if (webSocket != null) {
            webSocket.close(1000, "restart");
        }
        scheduleReconnect("restart");
    }

    private void scheduleReconnect(String reason) {
        synchronized (reconnectLock) {
            if (shuttingDown || reconnecting) {
                return;
            }
            reconnecting = true;
            int attempt = reconnectAttempts++;
            long delayMs = Math.min(30000L, 1000L * (1L << Math.min(attempt, 5)));
            logger.warn("Scheduling user data stream reconnect in {}ms (reason={})", delayMs, reason);
            reconnectScheduler.schedule(() -> {
                synchronized (reconnectLock) {
                    if (shuttingDown) {
                        reconnecting = false;
                        return;
                    }
                }
                try {
                    connectWebSocket();
                    synchronized (reconnectLock) {
                        reconnecting = false;
                        reconnectAttempts = 0;
                    }
                } catch (Exception e) {
                    logger.warn("User data stream reconnect failed: {}", e.getMessage());
                    synchronized (reconnectLock) {
                        reconnecting = false;
                    }
                    scheduleReconnect("reconnect failed");
                }
            }, delayMs, TimeUnit.MILLISECONDS);
        }
    }
}
