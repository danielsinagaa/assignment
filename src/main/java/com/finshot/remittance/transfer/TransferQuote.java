package com.finshot.remittance.transfer;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * All money math for a transfer. This is the only place where rounding happens.
 * The results are stored as they are and never recalculated.
 *
 * @param sendAmount    KRW, whole number
 * @param fee           KRW, whole number: 1% of sendAmount, HALF_UP, at least 3,000
 * @param totalDebit    KRW, sendAmount + fee
 * @param fxRate        PHP per 1 KRW
 * @param receiveAmount PHP, sendAmount x fxRate, 2 decimals, HALF_UP
 */
public record TransferQuote(long sendAmount, long fee, long totalDebit, BigDecimal fxRate, BigDecimal receiveAmount) {

    public static final long MIN_SEND_AMOUNT = 10_000;
    static final BigDecimal FEE_RATE = new BigDecimal("0.01");
    static final long MIN_FEE = 3_000;
    static final BigDecimal KRW_TO_PHP = new BigDecimal("0.0412");

    public static TransferQuote of(long sendAmount) {
        BigDecimal amount = BigDecimal.valueOf(sendAmount);

        long percentFee = amount.multiply(FEE_RATE).setScale(0, RoundingMode.HALF_UP).longValueExact();
        long fee = Math.max(percentFee, MIN_FEE);
        long totalDebit = Math.addExact(sendAmount, fee);
        BigDecimal receiveAmount = amount.multiply(KRW_TO_PHP).setScale(2, RoundingMode.HALF_UP);

        return new TransferQuote(sendAmount, fee, totalDebit, KRW_TO_PHP, receiveAmount);
    }
}
