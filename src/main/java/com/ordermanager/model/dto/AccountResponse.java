package com.ordermanager.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * DTO for Binance account information response.
 *
 * https://developers.binance.com/docs/binance-spot-api-docs/rest-api/account-endpoints
 * 
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AccountResponse {

    @JsonProperty("balances")
    private List<BalanceInfo> balances;

    public AccountResponse() {
    }

    public List<BalanceInfo> getBalances() {
        return balances;
    }

    public void setBalances(List<BalanceInfo> balances) {
        this.balances = balances;
    }

    @Override
    public String toString() {
        return "AccountResponse{" +
                ", balances=" + balances +
                '}';
    }

    public static class BalanceInfo {

        @JsonProperty("asset")
        private String asset;

        @JsonProperty("free")
        private String free;

        @JsonProperty("locked")
        private String locked;

        public BalanceInfo() {
        }

        public String getAsset() {
            return asset;
        }

        public void setAsset(String asset) {
            this.asset = asset;
        }

        public String getFree() {
            return free;
        }

        public void setFree(String free) {
            this.free = free;
        }

        public String getLocked() {
            return locked;
        }

        public void setLocked(String locked) {
            this.locked = locked;
        }

        @Override
        public String toString() {
            return "BalanceInfo{" +
                    "asset='" + asset + '\'' +
                    ", free='" + free + '\'' +
                    ", locked='" + locked + '\'' +
                    '}';
        }
    }
}
