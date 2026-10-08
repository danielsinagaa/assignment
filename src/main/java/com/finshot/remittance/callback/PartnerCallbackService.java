package com.finshot.remittance.callback;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finshot.remittance.common.error.ConflictException;
import com.finshot.remittance.common.error.NotFoundException;
import com.finshot.remittance.transfer.Transfer;
import com.finshot.remittance.transfer.TransferRepository;
import com.finshot.remittance.transfer.TransferStatus;

@Service
public class PartnerCallbackService {

    private final TransferRepository transfers;
    private final CallbackEventRepository events;
    private final Clock clock;

    public PartnerCallbackService(TransferRepository transfers, CallbackEventRepository events, Clock clock) {
        this.transfers = transfers;
        this.events = events;
        this.clock = clock;
    }

    /**
     * The only way a transfer becomes COMPLETED. The transfer row is locked first, so two deliveries of the
     * same callback run one after the other: the second one finds the stored eventId and changes nothing.
     */
    @Transactional
    public Transfer handle(PartnerCallbackRequest callback) {
        Transfer transfer = transfers.findByPartnerRefForUpdate(callback.partnerRef())
                .orElseThrow(() -> new NotFoundException("unknown partnerRef: " + callback.partnerRef()));

        var earlier = events.findById(callback.eventId());
        if (earlier.isPresent()) {
            if (!earlier.get().getTransferId().equals(transfer.getId())) {
                throw new ConflictException("eventId was already used for a different partnerRef");
            }
            return transfer; // duplicate delivery: already applied, answer 200 without changing anything
        }

        if (transfer.getStatus() != TransferStatus.SENDING) {
            throw new ConflictException("callback is only valid for a SENDING transfer, but it is "
                    + transfer.getStatus());
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        transfers.markCompleted(transfer.getId(), now);
        events.save(new CallbackEvent(callback.eventId(), transfer.getId(), callback.status(), now));
        return transfers.findById(transfer.getId()).orElseThrow();
    }
}
