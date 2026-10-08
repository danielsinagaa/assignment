package com.finshot.remittance.common.error;

/** The one error shape for every non-2xx response. */
public record ErrorResponse(String error, String message) {
}
