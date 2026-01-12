package com.ordermanager.service;

import com.ordermanager.client.BinanceRestClient;
import com.ordermanager.model.dto.AccountResponse;
import com.ordermanager.model.dto.AccountResponse.BalanceInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BalanceServiceTest {

    private BinanceRestClient restClient;
    private BalanceService service;

    @BeforeEach
    void setUp() {
        restClient = mock(BinanceRestClient.class);
        service = new BalanceService(restClient);
    }

    @Test
    void getNonZeroBalances_filtersZeroBalances() {
        AccountResponse response = new AccountResponse();
        AccountResponse.BalanceInfo btc = new AccountResponse.BalanceInfo();
        btc.setAsset("BTC");
        btc.setFree("0.5");
        btc.setLocked("1");
        AccountResponse.BalanceInfo eth = new AccountResponse.BalanceInfo();
        eth.setAsset("ETH");
        eth.setFree("0.0");
        eth.setLocked("0.0");
        AccountResponse.BalanceInfo sol = new AccountResponse.BalanceInfo();
        eth.setAsset("SOL");
        eth.setFree("0.0");
        eth.setLocked("0.0");
        response.setBalances(List.of(btc, eth, sol));

        when(restClient.getSigned(eq("/api/v3/account"), any(HashMap.class), eq(AccountResponse.class)))
                .thenReturn(response);

        String[] assets = { "BTC" };
        List<BalanceInfo> balances = service.getBalances(assets);

        assertEquals(1, balances.size());
        assertEquals("BTC", balances.get(0).getAsset());
        assertEquals(btc.getFree(), balances.get(0).getFree());
        assertEquals(btc.getLocked(), balances.get(0).getLocked());
    }
}
