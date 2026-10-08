package com.finshot.remittance.callback;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PartnerCallbackRequest(
        @NotBlank(message = "partnerRef is required")
        String partnerRef,

        @NotBlank(message = "eventId is required")
        @Size(max = 64, message = "eventId must be at most 64 characters")
        String eventId,

        // The brief says the partner only sends COMPLETED.
        @NotNull(message = "status is required")
        @Pattern(regexp = "COMPLETED", message = "status must be COMPLETED")
        String status) {
}
