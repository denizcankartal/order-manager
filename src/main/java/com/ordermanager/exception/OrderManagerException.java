package com.ordermanager.exception;

public class OrderManagerException extends RuntimeException {

    public OrderManagerException(String message) {
        super(message);
    }

    public OrderManagerException(String message, Throwable cause) {
        super(message, cause);
    }
}
