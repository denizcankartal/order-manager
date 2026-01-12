package com.ordermanager.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordermanager.config.AppConfig;
import com.ordermanager.exception.ApiException;
import com.ordermanager.model.dto.ErrorResponse;
import com.ordermanager.service.TimeSync;
import com.ordermanager.util.SignatureUtil;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * REST client for Binance API.
 *
 * Handles HTTP communication (GET, POST, DELETE, PUT), Request signing
 * (HMAC-SHA256), Error parsing and exception handling and
 * Connection pooling and timeouts
 */
public class BinanceRestClient {

    private static final Logger logger = LoggerFactory.getLogger(BinanceRestClient.class);

    private final OkHttpClient httpClient;
    private final AppConfig config;
    private final TimeSync timeSync;
    private final ObjectMapper objectMapper;

    public BinanceRestClient(AppConfig config, TimeSync timeSync) {
        this.config = config;
        this.timeSync = timeSync;
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(5, 30, TimeUnit.SECONDS))
                .build();
    }

    /**
     * Execute an unsigned GET request (public endpoints)
     *
     * @param endpoint     API endpoint (e.g., "/api/v3/time")
     * @param responseType Response class type
     * @return Deserialized response
     */
    public <T> T get(String endpoint, Class<T> responseType) {
        Request request = new Request.Builder()
                .url(config.getBaseUrl() + endpoint)
                .get()
                .build();

        return executeRequest(request, responseType);
    }

    /**
     * Execute a signed GET request (requires authentication)
     *
     * @param endpoint     API endpoint
     * @param params       Query parameters
     * @param responseType Response class type
     * @return Deserialized response
     */
    public <T> T getSigned(String endpoint, Map<String, String> params, Class<T> responseType) {
        String queryString = buildSignedQueryString(params);
        String url = config.getBaseUrl() + endpoint + "?" + queryString;

        Request request = new Request.Builder()
                .url(url)
                .header("X-MBX-APIKEY", config.getApiKey())
                .get()
                .build();

        return executeRequest(request, responseType);
    }

    /**
     * Execute a signed POST request (requires authentication).
     *
     * @param endpoint     API endpoint
     * @param params       Request parameters
     * @param responseType Response class type
     * @return Deserialized response
     */
    public <T> T postSigned(String endpoint, Map<String, String> params, Class<T> responseType) {
        String queryString = buildSignedQueryString(params);
        String url = config.getBaseUrl() + endpoint + "?" + queryString;

        RequestBody emptyBody = RequestBody.create(new byte[0]);

        Request request = new Request.Builder()
                .url(url)
                .header("X-MBX-APIKEY", config.getApiKey())
                .post(emptyBody)
                .build();

        return executeRequest(request, responseType);
    }

    /**
     * Execute a signed DELETE request (requires authentication).
     *
     * @param endpoint     API endpoint
     * @param params       Request parameters
     * @param responseType Response class type
     * @return Deserialized response
     */
    public <T> T deleteSigned(String endpoint, Map<String, String> params, Class<T> responseType) {
        String queryString = buildSignedQueryString(params);
        String url = config.getBaseUrl() + endpoint + "?" + queryString;

        Request request = new Request.Builder()
                .url(url)
                .header("X-MBX-APIKEY", config.getApiKey())
                .delete()
                .build();

        return executeRequest(request, responseType);
    }

    /**
     * Execute a signed PUT request (requires authentication).
     *
     * @param endpoint     API endpoint
     * @param params       Request parameters
     * @param responseType Response class type
     * @return Deserialized response
     */
    public <T> T putSigned(String endpoint, Map<String, String> params, Class<T> responseType) {
        String queryString = buildSignedQueryString(params);
        String url = config.getBaseUrl() + endpoint + "?" + queryString;

        RequestBody emptyBody = RequestBody.create(new byte[0]);

        Request request = new Request.Builder()
                .url(url)
                .header("X-MBX-APIKEY", config.getApiKey())
                .put(emptyBody)
                .build();

        return executeRequest(request, responseType);
    }

    /**
     * Execute HTTP request and parse JSON response.
     *
     * @param request      HTTP request
     * @param responseType Expected response type
     * @return Deserialized response
     * @throws ApiException if API returns error or network error occurs
     */
    private <T> T executeRequest(Request request, Class<T> responseType) {
        logger.debug("Request: {} {}", request.method(), sanitizeUrl(request.url().toString()));

        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                ApiException ex = parseErrorResponse(response.code(), body);
                if (ex.isTimestampError()) {
                    try {
                        timeSync.sync();
                    } catch (Exception syncError) {
                        logger.warn("Failed to resync time after timestamp error: {}", syncError.getMessage());
                    }
                }
                throw ex;
            }

            // Deserialize response to specified type
            T parsedResponse = objectMapper.readValue(body, responseType);
            String logBody = body.length() > 500 ? body.substring(0, 500) + "...(truncated)" : body;
            logger.debug("Response: {} (type: {})", logBody, responseType.getSimpleName());

            return parsedResponse;

        } catch (IOException e) {
            throw new ApiException("Network error: " + e.getMessage(), e);
        }
    }

    /**
     * Parse error response from Binance API.
     *
     * @param httpCode HTTP status code
     * @param body     Response body
     * @return ApiException with parsed error details
     */
    private ApiException parseErrorResponse(int httpCode, String body) {
        try {
            ErrorResponse errorResponse = objectMapper.readValue(body, ErrorResponse.class);
            logger.error("Binance API error: code={}, msg={}", errorResponse.getCode(), errorResponse.getMsg());

            return new ApiException(errorResponse.getCode(), errorResponse.getMsg(),
                    isRetriable(errorResponse.getCode()));

        } catch (Exception e) {
            // If we can't parse error response, throw generic exception
            logger.error("Failed to parse error response: {}", body);
            return new ApiException(httpCode, "HTTP " + httpCode + ": " + body, isRetriable(httpCode));
        }
    }

    /**
     * Determine if an error code is retriable
     */
    private boolean isRetriable(int code) {
        // Retriable HTTP codes: 429 (rate limit), 500-504 (server errors)
        if (code == 418 || code == 429 || (code >= 500 && code <= 504)) {
            return true;
        }

        // Retriable Binance error codes
        // -1021: Timestamp out of recvWindow
        // -1003: Too many requests (rate limit)
        return code == -1021 || code == -1003;
    }

    /**
     * Build signed query string with timestamp, recvWindow, and signature.
     *
     * This method:
     * 1. Builds query string from params (sorted, URL-encoded)
     * 2. Adds timestamp and recvWindow
     * 3. Generates HMAC-SHA256 signature
     * 4. Appends signature to query string
     *
     * @param params Request parameters (can be null or empty)
     * @return Complete signed query string ready for use in URL
     */
    private String buildSignedQueryString(Map<String, String> params) {
        String queryString = buildQueryString(params);
        queryString = addTimestampAndRecvWindow(queryString);
        String signature = SignatureUtil.generateSignature(queryString, config.getApiSecret());
        return queryString + "&signature=" + signature;
    }

    /**
     * Build query string from parameters with URL encoding.
     * Parameters are sorted alphabetically for consistency.
     *
     * @param params Request parameters
     * @return URL-encoded query string (or empty string if params is null/empty)
     */
    private String buildQueryString(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }

        return params.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()) // Sort for consistency
                .map(e -> urlEncode(e.getKey()) + "=" + urlEncode(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    /**
     * URL-encode a string parameter
     */
    private String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("UTF-8 encoding not supported", e);
        }
    }

    /**
     * Add timestamp and recvWindow parameters
     */
    private String addTimestampAndRecvWindow(String queryString) {
        long timestamp = timeSync.getServerTime();
        String timestampParam = "timestamp=" + timestamp;
        String recvWindowParam = "recvWindow=" + config.getRecvWindow();

        if (queryString.isEmpty()) {
            return recvWindowParam + "&" + timestampParam;
        } else {
            return queryString + "&" + recvWindowParam + "&" + timestampParam;
        }
    }

    /**
     * Sanitize URL for logging (remove signature)
     */
    private String sanitizeUrl(String url) {
        return url.replaceAll("signature=[^&]+", "signature=***");
    }

    /**
     * Shutdown the HTTP client and release resources.
     * Should be called when the application is shutting down.
     */
    public void shutdown() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
        logger.info("BinanceRestClient shut down");
    }
}
