package com.ordermanager.config;

import com.ordermanager.exception.ConfigurationException;

/**
 * Application configuration loaded from environment variables.
 *
 * Required environment variables:
 * - BINANCE_API_KEY: Your Binance testnet API key
 * - BINANCE_API_SECRET: Your Binance testnet API secret
 * Optional environment variables
 * - BINANCE_BASE_URL: Binance API base URL, default:
 * https://testnet.binance.vision
 * - BINANCE_RECV_WINDOW: Request validity window in milliseconds
 * (default: 10000)
 */
public class AppConfig {

    private final String apiKey;
    private final String apiSecret;
    private final String baseUrl;
    private final long recvWindow;

    private AppConfig(String apiKey, String apiSecret, String baseUrl, long recvWindow) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.baseUrl = baseUrl;
        this.recvWindow = recvWindow;
    }

    /**
     * Load configuration from environment variables
     */
    public static AppConfig loadFromEnv() {
        String apiKey = getRequiredEnv("BINANCE_API_KEY");
        String apiSecret = getRequiredEnv("BINANCE_API_SECRET");
        String baseUrl = getEnv("BINANCE_BASE_URL", "https://testnet.binance.vision");
        long recvWindow = Long.parseLong(getEnv("BINANCE_RECV_WINDOW", "10000"));

        return new AppConfig(apiKey, apiSecret, baseUrl, recvWindow);
    }

    private static String getRequiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) {
            throw new ConfigurationException("Required environment variable not set: " + name);
        }
        return value.trim();
    }

    private static String getEnv(String name, String defaultValue) {
        String value = System.getenv(name);
        return (value != null && !value.trim().isEmpty()) ? value.trim() : defaultValue;
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

    public long getRecvWindow() {
        return recvWindow;
    }

    @Override
    public String toString() {
        return "AppConfig{" +
                "apiKey='***'" +
                ", apiSecret='***'" +
                ", baseUrl='" + baseUrl + '\'' +
                ", recvWindow=" + recvWindow +
                '}';
    }
}
