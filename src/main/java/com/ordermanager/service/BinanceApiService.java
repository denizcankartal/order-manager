package com.ordermanager.service;

import com.ordermanager.client.BinanceRestClient;
import com.ordermanager.model.dto.AccountResponse;
import com.ordermanager.model.dto.ExchangeInfoResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;

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
     * Shutdown the underlying REST client.
     * Should be called when the service is no longer needed.
     */
    public void shutdown() {
        restClient.shutdown();
        logger.info("BinanceApiService shut down");
    }
}
