package com.ordermanager.util;

import com.ordermanager.exception.ApiException;
import org.slf4j.Logger;

import java.util.function.Supplier;

/**
 * Utility class for executing API calls with retry logic and exponential
 * backoff.
 */
public class RetryUtils {

    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final long INITIAL_BACKOFF_MS = 1000; // 1 second

    private RetryUtils() {
    }

    /**
     * Execute an API call with retry logic and exponential backoff.
     *
     * Retries up to MAX_RETRY_ATTEMPTS times for retriable errors with exponential
     * backoff:
     * - 1st retry: 1 second delay
     * - 2nd retry: 2 seconds delay
     * - 3rd retry: 4 seconds delay
     * - 4th retry: 8 seconds delay
     * - 5th retry: 16 seconds delay
     *
     * @param apiCall   The API call to execute
     * @param operation Description of the operation (for logging)
     * @param logger    Logger instance for the calling class
     * @param <T>       Return type of the API call
     * @return Result of the API call
     * @throws ApiException if all retry attempts fail
     */
    public static <T> T executeWithRetry(Supplier<T> apiCall, String operation, Logger logger) {
        ApiException lastException = null;

        for (int attempt = 0; attempt < MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                return apiCall.get();

            } catch (ApiException e) {
                lastException = e;

                if (!e.isRetriable() || attempt >= MAX_RETRY_ATTEMPTS - 1) {
                    break;
                }

                long backoffMs = INITIAL_BACKOFF_MS * (1L << attempt); // Exponential: 1s, 2s, 4s
                logger.warn("Retriable error on attempt {}/{} for {}: {}. Retrying in {}ms...", attempt + 1,
                        MAX_RETRY_ATTEMPTS, operation, e.getMessage(), backoffMs);

                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Retry interrupted", ie);
                }
            }
        }

        throw lastException;
    }
}
