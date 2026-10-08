package com.finshot.remittance.callback;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A processed partner callback. The primary key on eventId is what makes callbacks idempotent. */
@Entity
@Table(name = "partner_callback_events")
public class CallbackEvent {

    @Id
    private String eventId;

    /** Internal transfers.id. */
    private Long transferId;

    private String status;
    private Instant receivedAt;

    protected CallbackEvent() {
        // for JPA
    }

    CallbackEvent(String eventId, Long transferId, String status, Instant receivedAt) {
        this.eventId = eventId;
        this.transferId = transferId;
        this.status = status;
        this.receivedAt = receivedAt;
    }

    public String getEventId() {
        return eventId;
    }

    public Long getTransferId() {
        return transferId;
    }

    public String getStatus() {
        return status;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
