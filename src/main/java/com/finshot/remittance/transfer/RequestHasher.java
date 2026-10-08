package com.finshot.remittance.transfer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 fingerprint of a create request. Stored next to the Idempotency-Key so a retry with
 * the same key can be checked: same fingerprint = real retry, different fingerprint = 409.
 */
final class RequestHasher {

    // Unit separator: cannot be typed into a normal text field, so "a|b" + "c" never equals "a" + "b|c".
    private static final String SEPARATOR = "\u001F";

    private RequestHasher() {
    }

    /** sendAmount is the parsed number, so "0500000" and "500000" give the same hash. */
    static String hash(String customerId, String sendCurrency, long sendAmount, String receiveCurrency,
            String recipientName) {
        String canonical = String.join(SEPARATOR,
                customerId, sendCurrency, Long.toString(sendAmount), receiveCurrency, recipientName);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available in the JDK", e);
        }
    }
}
