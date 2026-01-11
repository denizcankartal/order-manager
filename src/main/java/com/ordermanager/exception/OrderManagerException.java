package com.ordermanager.exception;

/**
 * Base exception for all order manager errors
 */
public class OrderManagerException extends RuntimeException {

    public OrderManagerException(String message) {
        super(message);
    }

    public OrderManagerException(String message, Throwable cause) {
        super(message, cause);
    }
}
