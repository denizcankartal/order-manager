package com.ordermanager.integration;

import com.ordermanager.client.BinanceRestClient;
import com.ordermanager.config.AppConfig;
import com.ordermanager.model.dto.AccountResponse;
import com.ordermanager.model.dto.ExchangeInfoResponse;
import com.ordermanager.model.dto.ServerTimeResponse;
import com.ordermanager.service.BinanceApiService;
import com.ordermanager.service.TimeSync;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for BinanceRestClient.
 *
 * These tests require:
 * - BINANCE_API_KEY environment variable
 * - BINANCE_API_SECRET environment variable
 * - Active internet connection to Binance Testnet
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class BinanceRestClientIntegrationTest {

    private AppConfig config;
    private TimeSync timeSync;
    private BinanceRestClient restClient;
    private BinanceApiService apiService;

    @BeforeAll
    public void setUp() {
        config = AppConfig.loadFromEnv();

        assertNotNull(config.getApiKey(), "API_KEY must be set in environment");
        assertNotNull(config.getApiSecret(), "API_SECRET must be set in environment");
        assertFalse(config.getApiKey().isEmpty(), "API_KEY cannot be empty");
        assertFalse(config.getApiSecret().isEmpty(), "API_SECRET cannot be empty");

        timeSync = new TimeSync(new okhttp3.OkHttpClient(), config.getBaseUrl());
        timeSync.sync();

        restClient = new BinanceRestClient(config, timeSync);
        apiService = new BinanceApiService(restClient);

        System.out.println("=================================================");
        System.out.println("Running integration tests against Binance Testnet");
        System.out.println("Base URL: " + config.getBaseUrl());
        System.out.println("=================================================");
    }

    @AfterAll
    public void tearDown() {
        if (apiService != null) {
            apiService.shutdown();
        }
        System.out.println("Integration tests completed - REST client shut down");
    }

    @Test
    public void testGetServerTime() {
        ServerTimeResponse response = restClient.get("/api/v3/time", ServerTimeResponse.class);

        assertNotNull(response, "Server time response should not be null");
        assertTrue(response.getServerTime() > 0, "Server time should be positive");

        long currentTime = System.currentTimeMillis();
        long timeDiff = Math.abs(currentTime - response.getServerTime());

        System.out.println("Server time: " + response.getServerTime());
        System.out.println("Local time: " + currentTime);
        System.out.println("Difference: " + timeDiff + "ms");

        assertTrue(timeDiff < config.getRecvWindow(), "Time difference should be less than 10 seconds");
    }

    @Test
    public void testGetAccount() {
        AccountResponse response = apiService.getAccount();

        assertNotNull(response, "Account response should not be null");
        assertNotNull(response.getAccountType(), "Account type should not be null");
        assertNotNull(response.getBalances(), "Balances should not be null");

        System.out.println("Account Type: " + response.getAccountType());
        System.out.println("Can Trade: " + response.isCanTrade());
        System.out.println("Can Withdraw: " + response.isCanWithdraw());
        System.out.println("Can Deposit: " + response.isCanDeposit());
        System.out.println("Maker Commission: " + response.getMakerCommission());
        System.out.println("Taker Commission: " + response.getTakerCommission());
        System.out.println("Total Balances: " + response.getBalances().size());
    }

    @Test
    @Order(3)
    public void testGetExchangeInfo() {
        ExchangeInfoResponse response = apiService.getExchangeInfo();

        assertNotNull(response, "Exchange info response should not be null");
        assertNotNull(response.getTimezone(), "Timezone should not be null");
        assertNotNull(response.getSymbols(), "Symbols should not be null");
        assertTrue(response.getSymbols().size() > 0, "Should have at least one symbol");

        System.out.println("Timezone: " + response.getTimezone());
        System.out.println("Server Time: " + response.getServerTime());
        System.out.println("Total Symbols: " + response.getSymbols().size());

        // Find BTCUSDT symbol (should always exist on testnet)
        ExchangeInfoResponse.SymbolInfo btcusdt = response.getSymbols().stream()
                .filter(s -> "BTCUSDT".equals(s.getSymbol()))
                .findFirst()
                .orElse(null);

        assertNotNull(btcusdt, "BTCUSDT should exist on testnet");
    }

    @Test
    public void testTimeSync_caching() {
        // First call should hit the API
        long time1 = timeSync.getServerTime();
        assertTrue(time1 > 0, "First timestamp should be positive");

        // Second call should immediately use cached data
        long time2 = timeSync.getServerTime();
        assertTrue(time2 > 0, "Second timestamp should be positive");

        // Timestamps should be close (within 100ms)
        long diff = Math.abs(time2 - time1);
        System.out.println("Time 1: " + time1);
        System.out.println("Time 2: " + time2);
        System.out.println("Diff: " + diff + "ms");

        assertTrue(diff < 100, "Timestamps should be within 100ms due to caching.");
    }
}
