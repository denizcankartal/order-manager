package com.ordermanager.exception;

/**
 * Exception for Binance API errors
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
        return statusCode == -1003 || statusCode == -1015;
    }

    /**
     * Check if this error is due to insufficient balance
     */
    public boolean isInsufficientBalance() {
        return statusCode == -2010;
    }

    /**
     * Check if this error is a filter violation (LOT_SIZE, PRICE_FILTER, etc.)
     */
    public boolean isFilterViolation() {
        return statusCode == -1013;
    }

    /**
     * Check if this error is due to timestamp issues
     */
    public boolean isTimestampError() {
        return statusCode == -1021;
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
