package com.ordermanager.config;

import com.ordermanager.exception.ConfigurationException;

import io.github.cdimascio.dotenv.Dotenv;

public class AppConfig {
    private static final Dotenv DOTENV = Dotenv.configure().ignoreIfMissing().load();
    private final String apiKey;
    private final String apiSecret;
    private final String baseUrl;
    private final String wsBaseUrl;
    private final long recvWindow;
    private final String baseAsset;
    private final String quoteAsset;

    private AppConfig(String apiKey, String apiSecret, String baseUrl, String wsBaseUrl, long recvWindow,
            String baseAsset, String quoteAsset) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.baseUrl = baseUrl;
        this.wsBaseUrl = wsBaseUrl;
        this.recvWindow = recvWindow;
        this.baseAsset = baseAsset;
        this.quoteAsset = quoteAsset;
    }

    public static AppConfig loadFromEnv() {
        String apiKey = getRequiredEnv("BINANCE_API_KEY");
        String apiSecret = getRequiredEnv("BINANCE_API_SECRET");
        String baseUrl = getEnv("BINANCE_BASE_URL", "https://testnet.binance.vision");
        String wsBaseUrl = getEnv("BINANCE_WS_BASE_URL", "");
        long recvWindow = Long.parseLong(getEnv("BINANCE_RECV_WINDOW", "10000"));
        String baseAsset = getEnv("BASE_ASSET", "BTC");
        String quoteAsset = getEnv("QUOTE_ASSET", "USDT");
        return new AppConfig(apiKey, apiSecret, baseUrl, wsBaseUrl, recvWindow, baseAsset, quoteAsset);
    }

    private static String getRequiredEnv(String name) {
        String value = firstNonBlank(
                System.getenv(name),
                DOTENV.get(name));

        if (value == null || value.trim().isEmpty()) {
            throw new ConfigurationException("Required environment variable not set: " + name);
        }
        return value.trim();
    }

    private static String getEnv(String name, String defaultValue) {
        String value = firstNonBlank(
                System.getenv(name),
                DOTENV.get(name));
        return (value != null && !value.trim().isEmpty()) ? value.trim() : defaultValue;
    }

    private static String firstNonBlank(String... candidates) {
        for (String c : candidates) {
            if (c != null && !c.trim().isEmpty()) {
                return c.trim();
            }
        }
        return null;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getApiSecret() {
        return apiSecret;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getWsBaseUrl() {
        return wsBaseUrl;
    }

    public long getRecvWindow() {
        return recvWindow;
    }

    public String getBaseAsset() {
        return baseAsset;
    }

    public String getQuoteAsset() {
        return quoteAsset;
    }
}
