package com.ordermanager.exception;

/**
 * Exception for configuration errors (missing env vars, invalid config)
 */
public class ConfigurationException extends OrderManagerException {

    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
