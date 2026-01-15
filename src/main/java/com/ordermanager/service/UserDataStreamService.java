package com.ordermanager.service;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordermanager.model.Order;
import com.ordermanager.model.OrderStatus;
import com.ordermanager.util.SignatureUtil;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * https://developers.binance.com/docs/binance-spot-api-docs/user-data-stream
 */
public class UserDataStreamService {

    private static final Logger logger = LoggerFactory.getLogger(UserDataStreamService.class);
    private final StateManager stateManager;
    private final String wsBaseUrl;
    private final String apiKey;
    private final String apiSecret;
    private final long recvWindow;
    private final OkHttpClient wsClient;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService reconnectScheduler;
    private final Object reconnectLock;

    private WebSocket webSocket;
    private String currentWsUrl;
    private boolean shuttingDown;
    private boolean reconnecting;
    private int reconnectAttempts;
    private Integer subscriptionId;
    private String clientOrderId;

    private volatile Runnable onTrackingCompleted;

    public UserDataStreamService(StateManager stateManager,
            String wsBaseUrl,
            String apiKey,
            String apiSecret,
            long recvWindow) {
        this.stateManager = stateManager;
        this.wsBaseUrl = wsBaseUrl;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.recvWindow = recvWindow;
        this.wsClient = new OkHttpClient.Builder().pingInterval(0, TimeUnit.SECONDS).readTimeout(0, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
        this.reconnectScheduler = Executors.newSingleThreadScheduledExecutor();
        this.reconnectLock = new Object();
    }

    /**
     * set a callback that will be invoked when the currently tracked order becomes
     * terminal.
     */
    public void setOnTrackingCompleted(Runnable onTrackingCompleted) {
        this.onTrackingCompleted = onTrackingCompleted;
    }

    public void startTracking(String clientOrderId) {
        connectWebSocket();
        this.clientOrderId = clientOrderId;
    }

    public void stop() {
        shuttingDown = true;
        sendUnsubscribe();
        if (webSocket != null) {
            webSocket.close(1000, "shutdown");
        }
        reconnectScheduler.shutdownNow();
    }

    private void connectWebSocket() {
        currentWsUrl = wsBaseUrl;
        Request request = new Request.Builder()
                .url(currentWsUrl)
                .build();
        webSocket = wsClient.newWebSocket(request, new UserDataWebSocketListener());
    }

    private void sendSubscribeSignature() {
        if (apiKey == null || apiSecret == null) {
            logger.error("API credentials missing; cannot subscribe to WebSocket user data stream");
            return;
        }
        long timestamp = System.currentTimeMillis();
        String payload = buildCanonicalPayload(timestamp);
        String signature = SignatureUtil.generateSignature(payload, apiSecret);
        String requestId = UUID.randomUUID().toString();
        StringBuilder paramsBuilder = new StringBuilder();
        paramsBuilder.append(String.format(
                "\"apiKey\":\"%s\",\"timestamp\":%d",
                apiKey,
                timestamp));
        if (recvWindow > 0) {
            paramsBuilder.append(String.format(",\"recvWindow\":%d", recvWindow));
        }
        paramsBuilder.append(String.format(",\"signature\":\"%s\"", signature));
        String message = String.format(
                "{\"id\":\"%s\",\"method\":\"userDataStream.subscribe.signature\",\"params\":{%s}}",
                requestId,
                paramsBuilder);
        boolean sent = webSocket.send(message);
        logger.info("Sent userDataStream.subscribe.signature (sent={})", sent);
    }

    private void sendUnsubscribe() {
        if (webSocket == null || subscriptionId == null) {
            return;
        }
        String requestId = UUID.randomUUID().toString();
        String message = String.format(
                "{\"id\":\"%s\",\"method\":\"userDataStream.unsubscribe\",\"params\":{\"subscriptionId\":%d}}",
                requestId, subscriptionId);
        boolean sent = webSocket.send(message);
        logger.info("Sent userDataStream.unsubscribe (sent={})", sent);
        subscriptionId = null;
    }

    private class UserDataWebSocketListener extends WebSocketListener {
        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            logger.info("User data stream connected: {}", currentWsUrl);
            sendSubscribeSignature();
            synchronized (reconnectLock) {
                reconnectAttempts = 0;
                reconnecting = false;
            }
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
        logger.debug("WS raw message: {}", text);
        try {
            JsonNode node = objectMapper.readTree(text);
            if (node.has("result") && node.path("result").has("subscriptionId")) {
                subscriptionId = node.path("result").path("subscriptionId").asInt();
                logger.info("User data stream subscriptionId={}", subscriptionId);
                return;
            }

            JsonNode eventNode = node.has("event") ? node.path("event") : node;
            String eventType = eventNode.path("e").asText(null);

            if ("executionReport".equals(eventType)) {
                handleExecutionReport(eventNode);
            } else if ("outboundAccountPosition".equals(eventType)) {
                logger.debug("Received account update event");
            }
        } catch (Exception e) {
            logger.warn("Failed to parse user data stream message: {}", e.getMessage());
        }
    }

    private String buildCanonicalPayload(long timestamp) {
        StringBuilder builder = new StringBuilder();
        builder.append("apiKey=").append(apiKey);
        if (recvWindow > 0) {
            builder.append("&recvWindow=").append(recvWindow);
        }
        builder.append("&timestamp=").append(timestamp);
        return builder.toString();
    }

    private void handleExecutionReport(JsonNode node) {
        if (this.clientOrderId == null || this.clientOrderId.isEmpty()) {
            return;
        }

        String c = node.path("c").asText(""); // Client order ID
        String C = node.path("C").asText(""); // Original client order ID; This is the ID of the order being canceled

        String clientOrderId = null;
        if (Objects.equals(this.clientOrderId, c)) {
            clientOrderId = c;
        } else if (Objects.equals(this.clientOrderId, C)) {
            clientOrderId = C;
        } else {
            return;
        }

        Order order = stateManager.getOrderByClientId(clientOrderId);

        if (order == null) {
            return;
        }

        String orderStatus = node.path("X").asText(null);
        if (orderStatus != null) {
            order.setStatus(OrderStatus.valueOf(orderStatus));
        }

        String value = node.path("z").asText("0");
        order.setExecutedQty(new BigDecimal(value));

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

        System.out.println("{");
        System.out.printf("  \"orderId\": %d,%n", order.getOrderId());
        System.out.printf("  \"clientOrderId\": \"%s\",%n", order.getClientOrderId());
        System.out.printf("  \"symbol\": \"%s\",%n", order.getSymbol());
        System.out.printf("  \"side\": \"%s\",%n", order.getSide());
        System.out.printf("  \"price\": \"%s\",%n", order.getPrice());
        System.out.printf("  \"origQty\": \"%s\",%n", order.getOrigQty());
        System.out.printf("  \"executedQty\": \"%s\",%n", order.getExecutedQty());
        System.out.printf("  \"status\": \"%s\",%n", order.getStatus());
        System.out.printf("  \"updateTime\": %d%n", order.getUpdateTime());
        System.out.println("}");

        if (stateManager.getOrderByClientId(clientOrderId) != null) {
            stateManager.updateOrder(order);
            if (order.isTerminal()) {
                logger.debug("Archived terminal order: {}", order.getClientOrderId());
                stop();

                Runnable callback = onTrackingCompleted;
                if (callback != null) {
                    try {
                        callback.run();
                    } catch (Exception e) {
                        logger.error("Error in onTrackingCompleted callback: {}", e.getMessage());
                    }
                }
            }
        }
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
