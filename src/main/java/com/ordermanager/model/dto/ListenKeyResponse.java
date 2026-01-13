package com.ordermanager.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

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
