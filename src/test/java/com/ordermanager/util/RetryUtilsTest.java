package com.ordermanager.util;

import com.ordermanager.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class RetryUtilsTest {

    private static final Logger log = LoggerFactory.getLogger(RetryUtilsTest.class);

    @Test
    void retriesRetriableErrorsThenSucceeds() {
        AtomicInteger attempts = new AtomicInteger(0);
        Supplier<String> supplier = () -> {
            int attempt = attempts.incrementAndGet();
            if (attempt == 1) {
                throw new ApiException(429, "rate limit", true);
            }
            return "ok";
        };

        String result = RetryUtils.executeWithRetry(supplier, "test-op", log);

        assertEquals("ok", result);
        assertEquals(2, attempts.get(), "Should retry once then succeed");
    }

    @Test
    void stopsImmediatelyOnNonRetriableError() {
        AtomicInteger attempts = new AtomicInteger(0);
        Supplier<String> supplier = () -> {
            attempts.incrementAndGet();
            throw new ApiException("bad request", 400);
        };

        ApiException ex = assertThrows(ApiException.class,
                () -> RetryUtils.executeWithRetry(supplier, "test-op", log));

        assertEquals(1, attempts.get(), "Non-retriable error should not retry");
        assertEquals(400, ex.getStatusCode());
    }

    @Test
    void retriesOn418() {
        AtomicInteger attempts = new AtomicInteger(0);
        Supplier<String> supplier = () -> {
            int attempt = attempts.incrementAndGet();
            if (attempt < 3) {
                throw new ApiException(418, "i am a teapot", true);
            }
            return "ok";
        };

        String result = RetryUtils.executeWithRetry(supplier, "teapot-op", log);

        assertEquals("ok", result);
        assertEquals(3, attempts.get());
    }

    @Test
    void retriesOnTimestampError() {
        AtomicInteger attempts = new AtomicInteger(0);
        Supplier<String> supplier = () -> {
            int attempt = attempts.incrementAndGet();
            if (attempt < 2) {
                throw new ApiException(-1021, "timestamp", true);
            }
            return "ok";
        };

        String result = RetryUtils.executeWithRetry(supplier, "ts-op", log);

        assertEquals("ok", result);
        assertEquals(2, attempts.get());
    }
}
