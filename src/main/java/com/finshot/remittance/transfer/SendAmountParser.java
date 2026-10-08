package com.finshot.remittance.transfer;

import java.math.BigDecimal;
import java.util.regex.Pattern;

import com.finshot.remittance.common.error.ValidationException;

/** Turns the sendAmount text from the request into a whole KRW amount, or rejects it with 400. */
final class SendAmountParser {

    private static final Pattern NUMBER = Pattern.compile("-?\\d+(\\.\\d+)?");
    // Guard against huge numbers: 15 digits fits in BIGINT with room for the fee.
    private static final int MAX_DIGITS = 15;

    private SendAmountParser() {
    }

    static long parse(String raw) {
        if (raw == null || !NUMBER.matcher(raw).matches()) {
            throw new ValidationException("sendAmount must be a number");
        }
        if (raw.contains(".")) {
            // "500000.00" is rejected too: KRW has no decimals, and the brief says "has decimals" is invalid.
            throw new ValidationException("sendAmount must be a whole number (no decimals)");
        }
        BigDecimal amount = new BigDecimal(raw);
        if (amount.signum() <= 0) {
            throw new ValidationException("sendAmount must be greater than 0");
        }
        if (amount.precision() > MAX_DIGITS) {
            throw new ValidationException("sendAmount is too large");
        }
        if (amount.longValueExact() < TransferQuote.MIN_SEND_AMOUNT) {
            throw new ValidationException("sendAmount must be at least " + TransferQuote.MIN_SEND_AMOUNT);
        }
        return amount.longValueExact();
    }
}
