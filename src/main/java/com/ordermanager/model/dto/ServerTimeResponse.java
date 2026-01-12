package com.ordermanager.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for Binance server time response.
 * 
 * https://developers.binance.com/docs/binance-spot-api-docs/rest-api/general-endpoints
 */
public class ServerTimeResponse {

    @JsonProperty("serverTime")
    private long serverTime;

    public ServerTimeResponse() {
    }

    public long getServerTime() {
        return serverTime;
    }

    public void setServerTime(long serverTime) {
        this.serverTime = serverTime;
    }

    @Override
    public String toString() {
        return "ServerTimeResponse{" +
                "serverTime=" + serverTime +
                '}';
    }
}
