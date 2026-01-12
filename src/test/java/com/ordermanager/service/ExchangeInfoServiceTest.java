package com.ordermanager.service;

import com.ordermanager.client.BinanceRestClient;
import com.ordermanager.model.SymbolInfo;
import com.ordermanager.model.dto.ExchangeInfoResponse;
import com.ordermanager.model.filter.PriceFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExchangeInfoServiceTest {

    @Mock
    private BinanceRestClient mockRestClient;

    private ExchangeInfoService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ExchangeInfoService(mockRestClient);
    }

    @Test
    void testGetSymbolInfo_FirstCall_InitializesCache() {
        // Arrange
        ExchangeInfoResponse mockResponse = createMockResponse(
                createSymbolInfo("BTCUSDT", "TRADING"),
                createSymbolInfo("ETHUSDT", "TRADING"));
        when(mockRestClient.get(eq("/api/v3/exchangeInfo"), eq(ExchangeInfoResponse.class))).thenReturn(mockResponse);

        // Act
        SymbolInfo result = service.getSymbolInfo("BTCUSDT");

        // Assert
        assertNotNull(result);
        assertEquals("BTCUSDT", result.getSymbol());
        verify(mockRestClient, times(1)).get(eq("/api/v3/exchangeInfo"), eq(ExchangeInfoResponse.class));
    }

    @Test
    void testGetSymbolInfo_SecondCall_UsesCachedData() {
        // Arrange
        ExchangeInfoResponse mockResponse = createMockResponse(
                createSymbolInfo("BTCUSDT", "TRADING"),
                createSymbolInfo("ETHUSDT", "TRADING"));
        when(mockRestClient.get(eq("/api/v3/exchangeInfo"), eq(ExchangeInfoResponse.class))).thenReturn(mockResponse);

        // Act
        service.getSymbolInfo("BTCUSDT"); // First call - initializes
        SymbolInfo result = service.getSymbolInfo("ETHUSDT"); // Second call - cached

        // Assert
        assertNotNull(result);
        assertEquals("ETHUSDT", result.getSymbol());
        verify(mockRestClient, times(1)).get(eq("/api/v3/exchangeInfo"), eq(ExchangeInfoResponse.class)); // Called only
                                                                                                          // once
    }

    @Test
    void testGetSymbolInfo_SymbolNotFound_ReturnsNull() {
        // Arrange
        ExchangeInfoResponse mockResponse = createMockResponse(
                createSymbolInfo("BTCUSDT", "TRADING"));
        when(mockRestClient.get(eq("/api/v3/exchangeInfo"), eq(ExchangeInfoResponse.class))).thenReturn(mockResponse);

        // Act
        SymbolInfo result = service.getSymbolInfo("XYZUSDT");

        // Assert
        assertNull(result);
        verify(mockRestClient, times(1)).get(eq("/api/v3/exchangeInfo"), eq(ExchangeInfoResponse.class));
    }

    @Test
    void testLazyInitialization_ThreadSafe() throws InterruptedException {
        // Arrange
        ExchangeInfoResponse mockResponse = createMockResponse(
                createSymbolInfo("BTCUSDT", "TRADING"));
        when(mockRestClient.get(eq("/api/v3/exchangeInfo"), eq(ExchangeInfoResponse.class))).thenReturn(mockResponse);

        // Act - Multiple threads accessing simultaneously
        Thread thread1 = new Thread(() -> service.getSymbolInfo("BTCUSDT"));
        Thread thread2 = new Thread(() -> service.getSymbolInfo("BTCUSDT"));
        Thread thread3 = new Thread(() -> service.getSymbolInfo("BTCUSDT"));

        thread1.start();
        thread2.start();
        thread3.start();

        thread1.join();
        thread2.join();
        thread3.join();

        // Assert - Should only call API once despite concurrent access
        verify(mockRestClient, times(1)).get(eq("/api/v3/exchangeInfo"), eq(ExchangeInfoResponse.class));
    }

    private ExchangeInfoResponse createMockResponse(SymbolInfo... symbols) {
        ExchangeInfoResponse response = new ExchangeInfoResponse();
        response.setTimezone("UTC");
        response.setServerTime(System.currentTimeMillis());
        response.setSymbols(Arrays.asList(symbols));
        return response;
    }

    private SymbolInfo createSymbolInfo(String symbol, String status) {
        SymbolInfo info = new SymbolInfo();
        info.setSymbol(symbol);
        info.setStatus(status);

        // Add a sample price filter
        PriceFilter priceFilter = new PriceFilter(
                new BigDecimal("0.01"),
                new BigDecimal("1000000"),
                new BigDecimal("0.01"));
        info.setFilters(List.of(priceFilter));

        return info;
    }
}
