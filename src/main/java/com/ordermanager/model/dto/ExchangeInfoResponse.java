package com.ordermanager.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ordermanager.model.SymbolInfo;

import java.util.List;

/**
 * Endpoint: GET /api/v3/exchangeInfo
 *
 * *
 * https://developers.binance.com/docs/binance-spot-api-docs/rest-api/general-endpoints#exchange-information
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExchangeInfoResponse {

    @JsonProperty("timezone")
    private String timezone;

    @JsonProperty("serverTime")
    private long serverTime;

    @JsonProperty("symbols")
    private List<SymbolInfo> symbols;

    public ExchangeInfoResponse() {
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public long getServerTime() {
        return serverTime;
    }

    public void setServerTime(long serverTime) {
        this.serverTime = serverTime;
    }

    public List<SymbolInfo> getSymbols() {
        return symbols;
    }

    public void setSymbols(List<SymbolInfo> symbols) {
        this.symbols = symbols;
    }

    @Override
    public String toString() {
        return "ExchangeInfoResponse{" +
                "timezone='" + timezone + '\'' +
                ", serverTime=" + serverTime +
                ", symbols=" + (symbols != null ? symbols.size() + " symbols" : "null") +
                '}';
    }
}
