package com.ordermanager.service;

import com.ordermanager.exception.ApiException;
import com.ordermanager.model.dto.ServerTimeResponse;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Binance requires request timestamps to be within a certain window
 * (recvWindow) of the server time.
 *
 * This service fetches the server time and calculates the time offset between
 * local and server time and synchronizes local time with binance server time to
 * prevent clock drift errors.
 */
public class TimeSync {

    private static final Logger logger = LoggerFactory.getLogger(TimeSync.class);

    private final OkHttpClient httpClient;
    private final String baseUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile long timeOffset = 0; // milliseconds to add to local time to get server time

    public TimeSync(OkHttpClient httpClient, String baseUrl) {
        this.httpClient = httpClient;
        this.baseUrl = baseUrl;
    }

    /**
     * Synchronize time with Binance server.
     * Should be called on startup and periodically for long running processes
     */
    public void sync() {
        Request request = new Request.Builder()
                .url(baseUrl + "/api/v3/time")
                .get()
                .build();
        long localTimeBefore = System.currentTimeMillis();

        try (Response response = httpClient.newCall(request).execute()) {
            long localTimeAfter = System.currentTimeMillis();

            if (!response.isSuccessful()) {
                throw new ApiException("Failed to sync time: HTTP " + response.code());
            }

            String body = response.body() != null ? response.body().string() : "";
            ServerTimeResponse serverTimeResponse = objectMapper.readValue(body, ServerTimeResponse.class);
            long serverTime = serverTimeResponse.getServerTime();

            long localTimeAvg = (localTimeBefore + localTimeAfter) / 2L;

            timeOffset = serverTime - localTimeAvg;

            logger.info("Time synchronized. Offset: {}ms, Server time: {}, Local time: {}",
                    timeOffset, serverTime, localTimeAvg);
        } catch (IOException e) {
            throw new ApiException("Failed to sync time: " + e.getMessage(), e);
        }
    }

    /**
     * Get current server time in milliseconds.
     * Returns local time adjusted by the calculated offset.
     */
    public long getServerTime() {
        return System.currentTimeMillis() + timeOffset;
    }
}
