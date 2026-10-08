package com.finshot.remittance.transfer;

import java.net.URI;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.finshot.remittance.transfer.dto.CreateTransferRequest;
import com.finshot.remittance.transfer.dto.TransferResponse;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String IDEMPOTENT_REPLAYED = "Idempotent-Replayed";

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    /** A retry gets the same 201 and the same body as the first call, plus "Idempotent-Replayed: true". */
    @PostMapping
    public ResponseEntity<TransferResponse> create(@RequestHeader(IDEMPOTENCY_KEY) String idempotencyKey,
            @Valid @RequestBody CreateTransferRequest request) {
        TransferService.CreateResult result = transferService.create(idempotencyKey, request);
        TransferResponse body = TransferResponse.from(result.transfer());
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/transfers/" + body.transferId()))
                .header(IDEMPOTENT_REPLAYED, Boolean.toString(result.replayed()))
                .body(body);
    }

    @GetMapping("/{transferId}")
    public TransferResponse get(@PathVariable String transferId) {
        return TransferResponse.from(transferService.get(transferId));
    }

    @PostMapping("/{transferId}/cancel")
    public TransferResponse cancel(@PathVariable String transferId) {
        return TransferResponse.from(transferService.cancel(transferId));
    }

    /** Not in the brief: the brief does not say how a transfer enters SENDING. See README, Assumptions. */
    @PostMapping("/{transferId}/send")
    public TransferResponse send(@PathVariable String transferId) {
        return TransferResponse.from(transferService.send(transferId));
    }
}
