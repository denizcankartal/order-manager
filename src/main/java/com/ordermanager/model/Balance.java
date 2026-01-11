package com.ordermanager.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Account balance for a specific asset.
 * 
 * https://developers.binance.com/docs/binance-spot-api-docs/rest-api/account-endpoints
 * 
 * asset: Asset symbol (e.g., "BTC", "USDT")
 * free: Free balance available for trading
 * locked: Locked balance reserved for open orders
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Balance {

    @JsonProperty("asset")
    private String asset;

    @JsonProperty("free")
    private BigDecimal free;

    @JsonProperty("locked")
    private BigDecimal locked;

    /**
     * Default constructor for Jackson deserialization
     */
    public Balance() {
    }

    public Balance(String asset, BigDecimal free, BigDecimal locked) {
        this.asset = asset;
        this.free = free;
        this.locked = locked;
    }

    public String getAsset() {
        return asset;
    }

    public void setAsset(String asset) {
        this.asset = asset;
    }

    public BigDecimal getFree() {
        return free;
    }

    public void setFree(BigDecimal free) {
        this.free = free;
    }

    public BigDecimal getLocked() {
        return locked;
    }

    public void setLocked(BigDecimal locked) {
        this.locked = locked;
    }

    /**
     * Get total balance (free + locked)
     */
    public BigDecimal getTotal() {
        BigDecimal freeAmount = free != null ? free : BigDecimal.ZERO;
        BigDecimal lockedAmount = locked != null ? locked : BigDecimal.ZERO;
        return freeAmount.add(lockedAmount);
    }

    /**
     * Check if this balance has any funds (free or locked)
     */
    public boolean hasBalance() {
        return getTotal().compareTo(BigDecimal.ZERO) > 0;
    }

    @Override
    public String toString() {
        return "Balance{" +
                "asset='" + asset + '\'' +
                ", free=" + free +
                ", locked=" + locked +
                ", total=" + getTotal() +
                '}';
    }
}
