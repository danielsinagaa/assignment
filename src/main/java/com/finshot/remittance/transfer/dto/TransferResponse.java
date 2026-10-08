package com.finshot.remittance.transfer.dto;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.finshot.remittance.transfer.Transfer;

/**
 * Money is sent as strings so clients never parse it as a floating point number
 * and receiveAmount keeps its two decimals ("20600.00").
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransferResponse(
        String transferId,
        String customerId,
        String status,
        String sendCurrency,
        String sendAmount,
        String fee,
        String totalDebit,
        String receiveCurrency,
        String receiveAmount,
        String fxRate,
        String recipientName,
        String partnerRef,
        String failureReason,
        String createdAt,
        String updatedAt) {

    // Always millisecond precision, e.g. 2026-09-30T01:23:45.100Z (Instant.toString() drops zero fractions).
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZone(ZoneOffset.UTC);

    public static TransferResponse from(Transfer t) {
        return new TransferResponse(
                t.getTransferId(),
                t.getCustomerId(),
                t.getStatus().name(),
                t.getSendCurrency(),
                Long.toString(t.getSendAmount()),
                Long.toString(t.getFee()),
                Long.toString(t.getTotalDebit()),
                t.getReceiveCurrency(),
                t.getReceiveAmount().setScale(2).toPlainString(),
                t.getFxRate().stripTrailingZeros().toPlainString(),
                t.getRecipientName(),
                t.getPartnerRef(),
                t.getFailureReason(),
                format(t.getCreatedAt()),
                format(t.getUpdatedAt()));
    }

    private static String format(Instant instant) {
        return TIME.format(instant);
    }
}
