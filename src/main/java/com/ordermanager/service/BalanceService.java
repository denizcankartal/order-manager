package com.ordermanager.service;

import com.ordermanager.model.Balance;
import com.ordermanager.model.dto.AccountResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for managing account balances.
 *
 * Responsibilities:
 * - Fetch account balances from Binance API
 * - Convert BalanceInfo DTOs to Balance domain models
 * - Filter balances (non-zero, by asset)
 * - Provide convenient query methods
 */
public class BalanceService {

    private static final Logger logger = LoggerFactory.getLogger(BalanceService.class);

    private final BinanceApiService apiService;

    public BalanceService(BinanceApiService apiService) {
        this.apiService = apiService;
    }

    /**
     * Fetch all balances from Binance API.
     *
     * @return List of all balances (including zero balances)
     */
    public List<Balance> getAllBalances() {
        logger.debug("Fetching all balances");

        AccountResponse accountResponse = apiService.getAccount();

        List<Balance> balances = accountResponse.getBalances().stream()
            .map(this::convertToBalance)
            .collect(Collectors.toList());

        logger.info("Fetched {} balances", balances.size());

        return balances;
    }

    /**
     * Fetch all non-zero balances from Binance API.
     *
     * Filters out assets with zero free and locked amounts.
     *
     * @return List of balances with non-zero amounts
     */
    public List<Balance> getNonZeroBalances() {
        logger.debug("Fetching non-zero balances");

        List<Balance> allBalances = getAllBalances();
        List<Balance> nonZeroBalances = allBalances.stream()
            .filter(Balance::hasBalance)
            .collect(Collectors.toList());

        logger.info("Fetched {} non-zero balances (from {} total)", nonZeroBalances.size(), allBalances.size());

        return nonZeroBalances;
    }

    /**
     * Get balance for a specific asset.
     *
     * @param asset Asset symbol (e.g., "BTC", "USDT")
     * @return Balance for the asset, or empty if not found
     */
    public Optional<Balance> getBalance(String asset) {
        logger.debug("Fetching balance for asset: {}", asset);

        List<Balance> allBalances = getAllBalances();
        Optional<Balance> balance = allBalances.stream()
            .filter(b -> b.getAsset().equalsIgnoreCase(asset))
            .findFirst();

        if (balance.isPresent()) {
            logger.info("Balance for {}: free={}, locked={}, total={}",
                asset, balance.get().getFree(), balance.get().getLocked(), balance.get().getTotal());
        } else {
            logger.warn("Balance not found for asset: {}", asset);
        }

        return balance;
    }

    /**
     * Get balances as a map indexed by asset symbol.
     *
     * Useful for quick lookups.
     *
     * @return Map of asset symbol -> Balance
     */
    public Map<String, Balance> getBalancesMap() {
        logger.debug("Fetching balances as map");

        List<Balance> balances = getAllBalances();
        Map<String, Balance> balanceMap = balances.stream()
            .collect(Collectors.toMap(Balance::getAsset, b -> b));

        logger.info("Created balance map with {} entries", balanceMap.size());

        return balanceMap;
    }

    /**
     * Check if account has sufficient free balance for an asset.
     *
     * @param asset Asset symbol (e.g., "BTC", "USDT")
     * @param requiredAmount Required amount
     * @return true if sufficient balance exists, false otherwise
     */
    public boolean hasSufficientBalance(String asset, BigDecimal requiredAmount) {
        Optional<Balance> balance = getBalance(asset);

        if (balance.isEmpty()) {
            logger.warn("Asset {} not found in account balances", asset);
            return false;
        }

        BigDecimal free = balance.get().getFree();
        boolean sufficient = free.compareTo(requiredAmount) >= 0;

        logger.debug("Balance check for {}: required={}, free={}, sufficient={}",
            asset, requiredAmount, free, sufficient);

        return sufficient;
    }

    /**
     * Convert BalanceInfo DTO to Balance domain model.
     *
     * @param balanceInfo BalanceInfo from API response
     * @return Balance domain model
     */
    private Balance convertToBalance(AccountResponse.BalanceInfo balanceInfo) {
        return new Balance(
            balanceInfo.getAsset(),
            new BigDecimal(balanceInfo.getFree()),
            new BigDecimal(balanceInfo.getLocked())
        );
    }
}
