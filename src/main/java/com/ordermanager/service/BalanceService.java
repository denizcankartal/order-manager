package com.ordermanager.service;

import com.ordermanager.client.BinanceRestClient;
import com.ordermanager.exception.ApiException;
import com.ordermanager.model.dto.AccountResponse;
import com.ordermanager.model.dto.AccountResponse.BalanceInfo;
import com.ordermanager.util.RetryUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class BalanceService {

    private static final Logger logger = LoggerFactory.getLogger(BalanceService.class);

    private final BinanceRestClient restClient;

    public BalanceService(BinanceRestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Fetch balances for relevant assets of the traded symbol.
     *
     * @param assets Asset symbols (e.g., "BTC", "USDT")
     * @return List of all balances (including zero balances)
     */
    public List<BalanceInfo> getBalances(String[] assets) {
        if (assets.length == 0) {
            return Collections.emptyList();
        }

        Set<String> requestedAssets = Arrays.stream(assets)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toUpperCase)
                .collect(Collectors.toSet());

        try {
            AccountResponse accountResponse = RetryUtils.executeWithRetry(
                    () -> restClient.getSigned("/api/v3/account", new HashMap<>(), AccountResponse.class),
                    "fetch account balances", logger);

            if (accountResponse.getBalances() == null || accountResponse.getBalances().isEmpty()) {
                return Collections.emptyList();
            }

            return accountResponse.getBalances().stream()
                    .filter(b -> b.getAsset() != null &&
                            requestedAssets.contains(b.getAsset().toUpperCase()))
                    .collect(Collectors.toList());

        } catch (ApiException e) {
            logger.error("Failed to fetch balances: error={}", e.getMessage());

            if (e.isRateLimit()) {
                throw new IllegalStateException(
                        "Rate limit exceeded. Wait 60 seconds and retry. Error: " + e.getMessage());
            }

            if (e.isTimestampError()) {
                throw new IllegalStateException(
                        "Clock drift detected. Sync system time and retry. Error: " + e.getMessage());
            }

            throw new RuntimeException(String.format(
                    "Failed to fetch account balances: %s (error code: %d)", e.getMessage(), e.getStatusCode()), e);
        }
    }
}
