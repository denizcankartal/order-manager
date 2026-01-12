package com.ordermanager.service;

import com.ordermanager.client.BinanceRestClient;
import com.ordermanager.model.Balance;
import com.ordermanager.model.dto.AccountResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        btc.setFree("0.1");
        btc.setLocked("0.0");
        AccountResponse.BalanceInfo zero = new AccountResponse.BalanceInfo();
        zero.setAsset("ETH");
        zero.setFree("0.0");
        zero.setLocked("0.0");
        response.setBalances(List.of(btc, zero));

        when(restClient.getSigned(eq("/api/v3/account"), any(HashMap.class), eq(AccountResponse.class)))
                .thenReturn(response);

        List<Balance> balances = service.getNonZeroBalances();

        assertEquals(1, balances.size());
        assertEquals("BTC", balances.getFirst().getAsset());
        assertTrue(balances.getFirst().getTotal().compareTo(balances.getFirst().getFree()) == 0);
    }

}
