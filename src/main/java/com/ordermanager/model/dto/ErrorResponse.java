package com.ordermanager.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Binance API error response DTO.
 *
 * https://developers.binance.com/docs/binance-spot-api-docs/errors
 */
public class ErrorResponse {

    @JsonProperty("code")
    private int code;

    @JsonProperty("msg")
    private String msg;

    public ErrorResponse() {
    }

    public ErrorResponse(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    @Override
    public String toString() {
        return "ErrorResponse{" +
                "code=" + code +
                ", msg='" + msg + '\'' +
                '}';
    }
}
