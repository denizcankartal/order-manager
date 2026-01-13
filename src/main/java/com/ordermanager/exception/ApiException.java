package com.ordermanager.exception;

/**
 * Exception for Binance API errors
 * 
 * https://developers.binance.com/docs/binance-spot-api-docs/errors
 */
public class ApiException extends OrderManagerException {

    private final int statusCode;
    private final String errorCode;
    private final boolean retriable;

    public ApiException(String message) {
        super(message);
        this.statusCode = 0;
        this.errorCode = null;
        this.retriable = false;
    }

    public ApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
        this.errorCode = null;
        this.retriable = false;
    }

    public ApiException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
        this.errorCode = null;
        this.retriable = determineRetriable(statusCode);
    }

    public ApiException(String message, int statusCode, String errorCode) {
        super(message);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
        this.retriable = determineRetriable(statusCode);
    }

    public ApiException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.errorCode = null;
        this.retriable = determineRetriable(statusCode);
    }

    public ApiException(int statusCode, String message, boolean retriable) {
        super(message);
        this.statusCode = statusCode;
        this.errorCode = null;
        this.retriable = retriable;
    }

    private boolean determineRetriable(int code) {
        // 429 (rate limit), 418 (IP ban), 5xx (server errors) are retriable
        return code == 429 || code == 418 || code >= 500;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public boolean isRetriable() {
        return retriable;
    }

    /**
     * Check if this error is due to rate limiting
     */
    public boolean isRateLimit() {
        return statusCode == -1003 || statusCode == -1015 || statusCode == 418 || statusCode == 429;
    }

    /**
     * Check if this error is due to insufficient balance
     */
    public boolean isInsufficientBalance() {
        return statusCode == -2010 && messageContains("insufficient balance");
    }

    /**
     * Check if this error indicates a duplicate order submission
     */
    public boolean isDuplicateOrder() {
        return statusCode == -2010 && messageContains("duplicate order sent");
    }

    /**
     * Check if this error indicates the exchange rejected a new order
     */
    public boolean isNewOrderRejected() {
        return statusCode == -2010;
    }

    /**
     * Check if this error indicates the exchange rejected a cancel request
     */
    public boolean isCancelRejected() {
        return statusCode == -2011;
    }

    /**
     * Check if this error indicates an unknown order
     */
    public boolean isUnknownOrder() {
        return statusCode == -2013 || messageContains("unknown order sent");
    }

    /**
     * Check if this error indicates an invalid symbol
     */
    public boolean isInvalidSymbol() {
        return statusCode == -1121;
    }

    /**
     * Check if this error indicates an invalid listen key
     */
    public boolean isInvalidListenKey() {
        return statusCode == -1125;
    }

    /**
     * Check if this error indicates an authentication/permission issue
     */
    public boolean isAuthError() {
        return statusCode == -1002 || statusCode == -2014 || statusCode == -2015;
    }

    /**
     * Check if this error indicates an invalid signature
     */
    public boolean isInvalidSignature() {
        return statusCode == -1022;
    }

    /**
     * Check if this error indicates the market is closed for the symbol
     */
    public boolean isMarketClosed() {
        return statusCode == -2010 && messageContains("market is closed");
    }

    /**
     * Check if this error indicates trading is disabled for the account
     */
    public boolean isAccountTradingDisabled() {
        return messageContains("this action is disabled on this account") ||
                messageContains("this account may not place or cancel orders") ||
                messageContains("rest api trading is not enabled") ||
                messageContains("websocket api trading is not enabled") ||
                messageContains("fix api trading is not enabled");
    }

    /**
     * Check if this error indicates a network failure
     */
    public boolean isNetworkError() {
        return statusCode == 0 && messageContains("network error");
    }

    /**
     * Check if this error is a filter violation (LOT_SIZE, PRICE_FILTER, etc.)
     */
    public boolean isFilterViolation() {
        return statusCode == -1013 && messageContains("filter failure");
    }

    /**
     * Check if this error is due to timestamp issues
     */
    public boolean isTimestampError() {
        return statusCode == -1021;
    }

    public BinanceErrorType getErrorType() {
        if (isRateLimit()) {
            return BinanceErrorType.RATE_LIMIT;
        }
        if (isTimestampError()) {
            return BinanceErrorType.TIMESTAMP_ERROR;
        }
        if (isInvalidSignature()) {
            return BinanceErrorType.INVALID_SIGNATURE;
        }
        if (isAuthError()) {
            return BinanceErrorType.AUTH_ERROR;
        }
        if (isInvalidSymbol()) {
            return BinanceErrorType.INVALID_SYMBOL;
        }
        if (isInsufficientBalance()) {
            return BinanceErrorType.INSUFFICIENT_BALANCE;
        }
        if (isDuplicateOrder()) {
            return BinanceErrorType.DUPLICATE_ORDER;
        }
        if (isFilterViolation()) {
            return BinanceErrorType.FILTER_VIOLATION;
        }
        if (isUnknownOrder()) {
            return BinanceErrorType.ORDER_NOT_FOUND;
        }
        if (isCancelRejected()) {
            return BinanceErrorType.CANCEL_REJECTED;
        }
        if (isMarketClosed()) {
            return BinanceErrorType.MARKET_CLOSED;
        }
        if (isAccountTradingDisabled()) {
            return BinanceErrorType.ACCOUNT_TRADING_DISABLED;
        }
        if (isNewOrderRejected()) {
            return BinanceErrorType.ORDER_REJECTED;
        }
        if (isNetworkError()) {
            return BinanceErrorType.NETWORK_ERROR;
        }
        return BinanceErrorType.UNKNOWN;
    }

    private boolean messageContains(String needle) {
        String message = getMessage();
        if (message == null || needle == null) {
            return false;
        }
        return message.toLowerCase().contains(needle.toLowerCase());
    }

    @Override
    public String toString() {
        return "ApiException{" +
                "statusCode=" + statusCode +
                ", errorCode='" + errorCode + '\'' +
                ", retriable=" + retriable +
                ", message='" + getMessage() + '\'' +
                '}';
    }
}
