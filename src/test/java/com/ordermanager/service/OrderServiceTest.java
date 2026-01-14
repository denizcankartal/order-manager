package com.ordermanager.service;

import com.ordermanager.client.BinanceRestClient;
import com.ordermanager.model.Order;
import com.ordermanager.model.OrderSide;
import com.ordermanager.model.OrderStatus;
import com.ordermanager.model.SymbolInfo;
import com.ordermanager.model.dto.ExchangeInfoResponse;
import com.ordermanager.model.dto.OrderResponse;
import com.ordermanager.model.filter.LotSizeFilter;
import com.ordermanager.model.filter.MinNotionalFilter;
import com.ordermanager.model.filter.PercentPriceBySideFilter;
import com.ordermanager.model.filter.PriceFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderServiceTest {

        private BinanceRestClient restClient;
        private StateManager stateManager;
        private AsyncStatePersister persister;
        private OrderService service;

        @BeforeEach
        void setUp() {
                restClient = mock(BinanceRestClient.class);
                stateManager = mock(StateManager.class);
                persister = mock(AsyncStatePersister.class);
                ExchangeInfoResponse response = new ExchangeInfoResponse();
                response.setSymbols(List.of(buildSymbolInfo()));
                when(restClient.get(eq("/api/v3/exchangeInfo?symbol=BTCUSDT"), eq(ExchangeInfoResponse.class)))
                                .thenReturn(response);
                service = new OrderService(restClient, stateManager, persister, "BTC", "USDT");
        }

        @Test
        void placeOrder_propagatesWarningsAndPersists() {
                OrderResponse response = new OrderResponse();
                response.setOrderId(123L);
                response.setClientOrderId("cli-1");
                response.setStatus(OrderStatus.NEW.name());
                response.setExecutedQty("0");
                response.setTransactTime(1L);
                when(restClient.postSigned(eq("/api/v3/order"), anyMap(), eq(OrderResponse.class)))
                                .thenReturn(response);

                var result = service.placeOrder(OrderSide.BUY,
                                new BigDecimal("100.123"), // triggers price adjustment
                                new BigDecimal("0.1"), null);

                assertEquals(OrderStatus.NEW, result.getOrder().getStatus());
                assertFalse(result.getWarnings().isEmpty(), "Adjusted price should produce a warning");

                verify(stateManager, times(1)).addOrder(any(Order.class));
                verify(persister, atLeastOnce()).submitWrite(anyMap());
        }

        @Test
        void cancelOrder_terminalOrderReturnsWithoutRemoteCall() {
                Order existing = new Order("cli-1", "BTCUSDT", OrderSide.BUY,
                                new BigDecimal("100"), new BigDecimal("0.1"));
                existing.setStatus(OrderStatus.CANCELED);
                when(stateManager.getOrder("cli-1")).thenReturn(existing);

                Order result = service.cancelOrder("cli-1", "BTCUSDT");

                assertEquals(OrderStatus.CANCELED, result.getStatus());
                verify(restClient, never()).deleteSigned(anyString(), anyMap(), any());
        }

        @Test
        void cancelOrder_idempotentWhenAlreadyFilled() {
                Order existing = new Order("cli-2", "BTCUSDT", OrderSide.SELL,
                                new BigDecimal("200"), new BigDecimal("0.2"));
                existing.setStatus(OrderStatus.FILLED);
                existing.setOrderId(88L);
                when(stateManager.getOrder("cli-2")).thenReturn(existing);

                Order result = service.cancelOrder("cli-2", "BTCUSDT");

                assertEquals(OrderStatus.FILLED, result.getStatus());
                verify(restClient, never()).deleteSigned(anyString(), anyMap(), any());
        }

        @Test
        void cancelOrder_usesOrderIdWhenPresent() {
                Order existing = new Order("cli-1", "BTCUSDT", OrderSide.BUY,
                                new BigDecimal("100"), new BigDecimal("0.1"));
                existing.setStatus(OrderStatus.NEW);
                existing.setOrderId(55L);
                when(stateManager.getOrder("cli-1")).thenReturn(existing);

                OrderResponse resp = new OrderResponse();
                resp.setOrderId(55L);
                resp.setClientOrderId("cli-1");
                resp.setStatus(OrderStatus.NEW.name());
                resp.setExecutedQty("0");
                when(restClient.getSigned(eq("/api/v3/order"), anyMap(), eq(OrderResponse.class)))
                                .thenReturn(resp);
                when(restClient.deleteSigned(eq("/api/v3/order"), anyMap(), eq(OrderResponse.class)))
                                .thenReturn(resp);

                service.cancelOrder("cli-1", "BTCUSDT");

                ArgumentCaptor<Map<String, String>> params = ArgumentCaptor.forClass(Map.class);
                verify(restClient).deleteSigned(eq("/api/v3/order"), params.capture(), eq(OrderResponse.class));
                assertEquals("55", params.getValue().get("orderId"));
        }

        @Test
        void cancelOrder_remoteTerminalSkipsCancel() {
                when(stateManager.getOrder("cli-1")).thenReturn(null);

                OrderResponse resp = new OrderResponse();
                resp.setOrderId(77L);
                resp.setClientOrderId("cli-1");
                resp.setSymbol("BTCUSDT");
                resp.setSide(OrderSide.BUY.name());
                resp.setStatus(OrderStatus.FILLED.name());
                resp.setExecutedQty("0.1");
                resp.setPrice("100");
                resp.setOrigQty("0.1");
                resp.setUpdateTime(5L);

                when(restClient.getSigned(eq("/api/v3/order"), anyMap(), eq(OrderResponse.class)))
                                .thenReturn(resp);

                Order result = service.cancelOrder("cli-1", "BTCUSDT");

                assertEquals(OrderStatus.FILLED, result.getStatus());
                verify(restClient, never()).deleteSigned(anyString(), anyMap(), any());
        }

        @Test
        void syncWithExchange_removesTerminalOrderFromLocalState() {
                Order existing = new Order("cli-1", "BTCUSDT", OrderSide.BUY,
                                new BigDecimal("100"), new BigDecimal("0.1"));
                existing.setOrderId(77L);
                existing.setStatus(OrderStatus.NEW);
                when(stateManager.getOrder("77")).thenReturn(null);
                when(stateManager.getOrder("cli-1")).thenReturn(existing);

                OrderResponse resp = new OrderResponse();
                resp.setOrderId(77L);
                resp.setClientOrderId("cli-1");
                resp.setStatus(OrderStatus.FILLED.name());
                resp.setExecutedQty("0.1");
                when(restClient.getSigned(eq("/api/v3/order"), anyMap(), eq(OrderResponse.class)))
                                .thenReturn(resp);

                service.fetchAndUpdateOrder("cli-1", "BTCUSDT");

                assertEquals(OrderStatus.FILLED, existing.getStatus());
                assertEquals(new BigDecimal("0.1"), existing.getExecutedQty());
                verify(stateManager).removeOrder("cli-1");
                verify(stateManager, never()).updateOrder(any());
                verify(persister).submitWrite(anyMap());
        }

        @Test
        void syncWithExchange_updatesLocalState() {
                Order existing = new Order("cli-1", "BTCUSDT", OrderSide.BUY,
                                new BigDecimal("100"), new BigDecimal("0.1"));
                existing.setOrderId(77L);
                existing.setStatus(OrderStatus.NEW);
                when(stateManager.getOrder("77")).thenReturn(null);
                when(stateManager.getOrder("cli-1")).thenReturn(existing);

                OrderResponse resp = new OrderResponse();
                resp.setOrderId(77L);
                resp.setClientOrderId("cli-1");
                resp.setStatus(OrderStatus.PARTIALLY_FILLED.name());
                resp.setExecutedQty("0.05");
                when(restClient.getSigned(eq("/api/v3/order"), anyMap(), eq(OrderResponse.class)))
                                .thenReturn(resp);

                service.fetchAndUpdateOrder("cli-1", "BTCUSDT");

                assertEquals(OrderStatus.PARTIALLY_FILLED, existing.getStatus());
                assertEquals(new BigDecimal("0.05"), existing.getExecutedQty());
                verify(stateManager).updateOrder(existing);
                verify(persister).submitWrite(anyMap());
        }

        private SymbolInfo buildSymbolInfo() {
                SymbolInfo info = new SymbolInfo();
                info.setSymbol("BTCUSDT");
                info.setStatus("TRADING");

                PriceFilter price = new PriceFilter(new BigDecimal("0.01"), new BigDecimal("1000000"),
                                new BigDecimal("0.01"));
                LotSizeFilter lot = new LotSizeFilter(new BigDecimal("0.00001"), new BigDecimal("1000"),
                                new BigDecimal("0.00001"));
                MinNotionalFilter notional = new MinNotionalFilter(new BigDecimal("5"));
                PercentPriceBySideFilter percent = new PercentPriceBySideFilter();
                percent.setBidMultiplierDown(new BigDecimal("0.9"));
                percent.setBidMultiplierUp(new BigDecimal("1.1"));
                percent.setAskMultiplierDown(new BigDecimal("0.9"));
                percent.setAskMultiplierUp(new BigDecimal("1.1"));

                info.setFilters(java.util.List.of(price, lot, notional, percent));
                return info;
        }
}
