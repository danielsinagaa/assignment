package com.finshot.remittance.transfer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Create request. sendAmount is text so we can reject decimals and keep it exact;
 * its number rules are checked in SendAmountParser. A JSON number is also accepted and read as text.
 */
public record CreateTransferRequest(
        @NotBlank(message = "customerId is required")
        String customerId,

        @NotNull(message = "sendCurrency is required")
        @Pattern(regexp = "KRW", message = "sendCurrency must be KRW")
        String sendCurrency,

        @NotBlank(message = "sendAmount is required")
        String sendAmount,

        @NotNull(message = "receiveCurrency is required")
        @Pattern(regexp = "PHP", message = "receiveCurrency must be PHP")
        String receiveCurrency,

        @NotBlank(message = "recipientName must not be empty")
        @Size(max = 100, message = "recipientName must be at most 100 characters")
        String recipientName) {
}
