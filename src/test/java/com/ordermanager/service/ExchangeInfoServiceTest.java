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
        verify(mockRestClient, times(1)).get(eq("/api/v3/exchangeInfo"), eq(ExchangeInfoResponse.class)); // Called only once
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
    void testSymbolExists_SymbolInCache_ReturnsTrue() {
        // Arrange
        ExchangeInfoResponse mockResponse = createMockResponse(
                createSymbolInfo("BTCUSDT", "TRADING"));
        when(mockRestClient.get(eq("/api/v3/exchangeInfo"), eq(ExchangeInfoResponse.class))).thenReturn(mockResponse);

        // Act
        boolean exists = service.symbolExists("BTCUSDT");

        // Assert
        assertTrue(exists);
    }

    @Test
    void testSymbolExists_SymbolNotInCache_ReturnsFalse() {
        // Arrange
        ExchangeInfoResponse mockResponse = createMockResponse(
                createSymbolInfo("BTCUSDT", "TRADING"));
        when(mockRestClient.get(eq("/api/v3/exchangeInfo"), eq(ExchangeInfoResponse.class))).thenReturn(mockResponse);

        // Act
        boolean exists = service.symbolExists("XYZUSDT");

        // Assert
        assertFalse(exists);
    }

    @Test
    void testGetAllSymbols_ReturnsAllCachedSymbols() {
        // Arrange
        ExchangeInfoResponse mockResponse = createMockResponse(
                createSymbolInfo("BTCUSDT", "TRADING"),
                createSymbolInfo("ETHUSDT", "TRADING"),
                createSymbolInfo("BNBUSDT", "TRADING"));
        when(mockRestClient.get(eq("/api/v3/exchangeInfo"), eq(ExchangeInfoResponse.class))).thenReturn(mockResponse);

        // Act
        service.getSymbolInfo("BTCUSDT"); // Initialize cache
        var symbols = service.getAllSymbols();

        // Assert
        assertEquals(3, symbols.size());
        assertTrue(symbols.contains("BTCUSDT"));
        assertTrue(symbols.contains("ETHUSDT"));
        assertTrue(symbols.contains("BNBUSDT"));
    }

    @Test
    void testRefresh_ClearsAndReloadsCache() {
        // Arrange
        ExchangeInfoResponse firstResponse = createMockResponse(
                createSymbolInfo("BTCUSDT", "TRADING"));
        ExchangeInfoResponse secondResponse = createMockResponse(
                createSymbolInfo("ETHUSDT", "TRADING"));
        when(mockRestClient.get(eq("/api/v3/exchangeInfo"), eq(ExchangeInfoResponse.class)))
                .thenReturn(firstResponse)
                .thenReturn(secondResponse);

        // Act
        service.getSymbolInfo("BTCUSDT"); // Initialize with BTCUSDT
        service.refresh(); // Reload with ETHUSDT
        SymbolInfo result = service.getSymbolInfo("ETHUSDT");

        // Assert
        assertNotNull(result);
        assertEquals("ETHUSDT", result.getSymbol());
        assertNull(service.getSymbolInfo("BTCUSDT")); // Old data cleared
        verify(mockRestClient, times(2)).get(eq("/api/v3/exchangeInfo"), eq(ExchangeInfoResponse.class));
    }

    @Test
    void testRefresh_ApiFailure_ThrowsException() {
        // Arrange
        when(mockRestClient.get(eq("/api/v3/exchangeInfo"), eq(ExchangeInfoResponse.class))).thenThrow(new RuntimeException("API error"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> service.refresh());
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

    @Test
    void testGetSymbolInfo_NullSymbolList_HandlesGracefully() {
        // Arrange
        ExchangeInfoResponse mockResponse = new ExchangeInfoResponse();
        mockResponse.setSymbols(null); // No symbols
        when(mockRestClient.get(eq("/api/v3/exchangeInfo"), eq(ExchangeInfoResponse.class))).thenReturn(mockResponse);

        // Act
        SymbolInfo result = service.getSymbolInfo("BTCUSDT");

        // Assert
        assertNull(result);
        assertEquals(0, service.getAllSymbols().size());
    }

    @Test
    void testGetSymbolInfo_SymbolWithoutName_SkippedInCache() {
        // Arrange
        SymbolInfo validSymbol = createSymbolInfo("BTCUSDT", "TRADING");
        SymbolInfo invalidSymbol = new SymbolInfo(); // No symbol name
        invalidSymbol.setStatus("TRADING");

        ExchangeInfoResponse mockResponse = new ExchangeInfoResponse();
        mockResponse.setSymbols(Arrays.asList(validSymbol, invalidSymbol));
        when(mockRestClient.get(eq("/api/v3/exchangeInfo"), eq(ExchangeInfoResponse.class))).thenReturn(mockResponse);

        // Act
        service.getSymbolInfo("BTCUSDT");

        // Assert
        assertEquals(1, service.getAllSymbols().size()); // Only valid symbol cached
        assertTrue(service.symbolExists("BTCUSDT"));
    }

    // ==================== Helper Methods ====================

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
