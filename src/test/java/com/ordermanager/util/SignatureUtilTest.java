package com.ordermanager.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SignatureUtilTest {

    /**
     * Source:
     * https://github.com/binance/binance-signature-examples
     */
    @Test
    void testGenerateSignature_withBinanceOfficialExample() {
        String queryString = "symbol=LTCBTC&side=BUY&type=LIMIT&timeInForce=GTC&quantity=1&price=0.1&recvWindow=5000&timestamp=1499827319559";
        String apiSecret = "NhqPtmdSJYdKjVHjA7PZj4Mge3R5YNiP1e3UZjInClVN65XAbvqqM6A7H5fATj0j";

        String signature = SignatureUtil.generateSignature(queryString, apiSecret);

        assertEquals("c8db56825ae71d6d79447849e617115f4a920fa2acdcab2b053c4b2838bd6b71", signature);
    }

    @Test
    void testGenerateSignature_consistency() {
        String queryString = "symbol=BTCUSDT&side=BUY";
        String apiSecret = "my_secret_key";

        String signature1 = SignatureUtil.generateSignature(queryString, apiSecret);
        String signature2 = SignatureUtil.generateSignature(queryString, apiSecret);

        assertEquals(signature1, signature2, "Same input should always produce same signature (determinism)");
    }

    @Test
    void testGenerateSignature_differentSecrets() {
        String queryString = "symbol=BTCUSDT&side=BUY";
        String secret1 = "secret1";
        String secret2 = "secret2";

        String signature1 = SignatureUtil.generateSignature(queryString, secret1);
        String signature2 = SignatureUtil.generateSignature(queryString, secret2);

        assertNotEquals(signature1, signature2, "Different secrets must produce different signatures");
    }

    @Test
    void testGenerateSignature_differentQueryStrings() {
        String apiSecret = "test_secret";
        String queryString1 = "symbol=BTCUSDT&side=BUY";
        String queryString2 = "symbol=ETHUSDT&side=SELL";

        String signature1 = SignatureUtil.generateSignature(queryString1, apiSecret);
        String signature2 = SignatureUtil.generateSignature(queryString2, apiSecret);

        assertNotEquals(signature1, signature2, "Different messages must produce different signatures");
    }

    @Test
    void testGenerateSignature_returnsLowercaseHexString() {
        String queryString = "test=value";
        String apiSecret = "secret";

        String signature = SignatureUtil.generateSignature(queryString, apiSecret);

        // Verify it's a valid lowercase hex string (only contains 0-9, a-f)
        assertTrue(signature.matches("[0-9a-f]{64}"), "Signature must be 64-character lowercase hex string");
        assertFalse(signature.matches(".*[A-F].*"), "Signature must not contain uppercase hex characters");
    }

    /**
     * Test null query string handling
     */
    @Test
    void testGenerateSignature_withNullQueryString() {
        String apiSecret = "secret";

        assertThrows(RuntimeException.class, () -> {
            SignatureUtil.generateSignature(null, apiSecret);
        }, "Null query string should throw RuntimeException");
    }

    /**
     * Test null API secret handling
     */
    @Test
    void testGenerateSignature_withNullSecret() {
        String queryString = "symbol=BTCUSDT";

        assertThrows(RuntimeException.class, () -> {
            SignatureUtil.generateSignature(queryString, null);
        }, "Null API secret should throw RuntimeException");
    }

    /**
     * Test parameter order sensitivity
     * HMAC is sensitive to byte order, so "a=1&b=2" != "b=2&a=1"
     */
    @Test
    void testGenerateSignature_parameterOrderMatters() {
        String apiSecret = "secret";
        String queryString1 = "symbol=BTCUSDT&side=BUY";
        String queryString2 = "side=BUY&symbol=BTCUSDT"; // Different order

        String signature1 = SignatureUtil.generateSignature(queryString1, apiSecret);
        String signature2 = SignatureUtil.generateSignature(queryString2, apiSecret);

        assertNotEquals(signature1, signature2,
                "Parameter order must affect signature (this is why BinanceRestClient sorts params)");
    }
}
