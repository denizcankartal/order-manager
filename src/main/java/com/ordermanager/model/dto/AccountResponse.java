package com.ordermanager.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * DTO for Binance account information response.
 *
 * Example response from GET /api/v3/account:
 * {
 * "makerCommission": 15,
 * "takerCommission": 15,
 * "buyerCommission": 0,
 * "sellerCommission": 0,
 * "canTrade": true,
 * "canWithdraw": true,
 * "canDeposit": true,
 * "updateTime": 123456789,
 * "accountType": "SPOT",
 * "balances": [
 * {
 * "asset": "BTC",
 * "free": "4723846.89208129",
 * "locked": "0.00000000"
 * },
 * ...
 * ],
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AccountResponse {

    @JsonProperty("makerCommission")
    private int makerCommission;

    @JsonProperty("takerCommission")
    private int takerCommission;

    @JsonProperty("buyerCommission")
    private int buyerCommission;

    @JsonProperty("sellerCommission")
    private int sellerCommission;

    @JsonProperty("canTrade")
    private boolean canTrade;

    @JsonProperty("canWithdraw")
    private boolean canWithdraw;

    @JsonProperty("canDeposit")
    private boolean canDeposit;

    @JsonProperty("updateTime")
    private long updateTime;

    @JsonProperty("accountType")
    private String accountType;

    @JsonProperty("balances")
    private List<BalanceInfo> balances;

    @JsonProperty("permissions")
    private List<String> permissions;

    public AccountResponse() {
    }

    public int getMakerCommission() {
        return makerCommission;
    }

    public void setMakerCommission(int makerCommission) {
        this.makerCommission = makerCommission;
    }

    public int getTakerCommission() {
        return takerCommission;
    }

    public void setTakerCommission(int takerCommission) {
        this.takerCommission = takerCommission;
    }

    public int getBuyerCommission() {
        return buyerCommission;
    }

    public void setBuyerCommission(int buyerCommission) {
        this.buyerCommission = buyerCommission;
    }

    public int getSellerCommission() {
        return sellerCommission;
    }

    public void setSellerCommission(int sellerCommission) {
        this.sellerCommission = sellerCommission;
    }

    public boolean isCanTrade() {
        return canTrade;
    }

    public void setCanTrade(boolean canTrade) {
        this.canTrade = canTrade;
    }

    public boolean isCanWithdraw() {
        return canWithdraw;
    }

    public void setCanWithdraw(boolean canWithdraw) {
        this.canWithdraw = canWithdraw;
    }

    public boolean isCanDeposit() {
        return canDeposit;
    }

    public void setCanDeposit(boolean canDeposit) {
        this.canDeposit = canDeposit;
    }

    public long getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(long updateTime) {
        this.updateTime = updateTime;
    }

    public String getAccountType() {
        return accountType;
    }

    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }

    public List<BalanceInfo> getBalances() {
        return balances;
    }

    public void setBalances(List<BalanceInfo> balances) {
        this.balances = balances;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
    }

    @Override
    public String toString() {
        return "AccountResponse{" +
                "makerCommission=" + makerCommission +
                ", takerCommission=" + takerCommission +
                ", buyerCommission=" + buyerCommission +
                ", sellerCommission=" + sellerCommission +
                ", canTrade=" + canTrade +
                ", canWithdraw=" + canWithdraw +
                ", canDeposit=" + canDeposit +
                ", updateTime=" + updateTime +
                ", accountType='" + accountType + '\'' +
                ", balances=" + balances +
                ", permissions=" + permissions +
                '}';
    }

    /**
     * Nested DTO for balance information within account response.
     */
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
