package com.ordermanager.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Utility for generating HMAC-SHA256 signatures for Binance API requests.
 *
 * HMAC (Hash-based Message Authentication Code) is a cryptographic algorithm
 * that combines:
 * 1. Hash function (in this case SHA-256)
 * 2. Secret key (the API secret key)
 * 3. Message (the query string)
 * 
 * Binance uses HMAC-SHA256 to prevent unauthorized API access such that the
 * request can only come from someone who knows the secret key.
 * 
 * In order to prevent replay attacks, a timestamp is included in the signed
 * data.
 * 
 * Algorithm:
 * 1. Build query string: "symbol=BTCUSDT&side=BUY&timestamp=123456789"
 * 2. Sign with secret: HMAC-SHA256(queryString, apiSecret)
 * 3. Convert to hex string
 * 4. Append to request: queryString + "&signature=" + signature
 * 
 * references:
 * https://github.com/binance/binance-signature-examples/tree/master
 * https://github.com/binance/binance-connector-java/blob/master/clients/common/src/main/java/com/binance/connector/client/common/sign/HmacSignatureGenerator.java
 */
public class SignatureUtil {

    private static final String HMAC_SHA256 = "HmacSHA256";

    /**
     * Generate HMAC-SHA256 signature for the given query string.
     *
     * @param queryString The query string to sign
     *                    "symbol=BTCUSDT&side=BUY&type=LIMIT"
     * @param apiSecret   The API secret key
     * @return Hex-encoded signature string
     */
    public static String generateSignature(String queryString, String apiSecret) {
        try {
            Mac hmacSha256 = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    apiSecret.getBytes(StandardCharsets.UTF_8),
                    HMAC_SHA256);
            hmacSha256.init(secretKeySpec);

            byte[] hash = hmacSha256.doFinal(queryString.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate HMAC-SHA256 signature: " + e.getMessage(), e);
        }
    }

    /**
     * Convert byte array to hex string using direct array indexing.
     * 
     * Each byte is split into two 4-bit nibbles and converted to hex characters.
     *
     * @param bytes Byte array (typically 32 bytes from HMAC-SHA256)
     * @return Hex string (lowercase, 64 characters for SHA256)
     */
    private static String bytesToHex(byte[] bytes) {
        final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();
        final char[] hexChars = new char[bytes.length << 1]; // bytes.length * 2
        for (int i = 0, j = 0; i < bytes.length; i++) {
            hexChars[j++] = HEX_DIGITS[(bytes[i] & 0xF0) >>> 4]; // High nibble
            hexChars[j++] = HEX_DIGITS[bytes[i] & 0x0F]; // Low nibble
        }
        return new String(hexChars);
    }
}
