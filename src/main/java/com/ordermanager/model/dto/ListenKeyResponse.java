package com.ordermanager.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * https://developers.binance.com/docs/binance-spot-api-docs/user-data-stream
 */
public class ListenKeyResponse {

    @JsonProperty("listenKey")
    private String listenKey;

    public ListenKeyResponse() {
    }

    public ListenKeyResponse(String listenKey) {
        this.listenKey = listenKey;
    }

    public String getListenKey() {
        return listenKey;
    }

    public void setListenKey(String listenKey) {
        this.listenKey = listenKey;
    }
}
