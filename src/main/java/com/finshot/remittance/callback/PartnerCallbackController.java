package com.finshot.remittance.callback;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.finshot.remittance.transfer.dto.TransferResponse;

@RestController
public class PartnerCallbackController {

    private final PartnerCallbackService callbackService;

    public PartnerCallbackController(PartnerCallbackService callbackService) {
        this.callbackService = callbackService;
    }

    /** 200 with the transfer, for the first delivery and for repeated deliveries of the same eventId. */
    @PostMapping("/api/callbacks/partner")
    public TransferResponse callback(@Valid @RequestBody PartnerCallbackRequest request) {
        return TransferResponse.from(callbackService.handle(request));
    }
}
