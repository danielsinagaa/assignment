package com.finshot.remittance.transfer;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A transfer row. It has no setters: status changes go through the compare-and-set
 * update queries in {@link TransferRepository}, so two requests can never both move the same transfer.
 */
@Entity
@Table(name = "transfers")
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String transferId;
    private String customerId;
    private String idempotencyKey;
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private TransferStatus status;

    private String sendCurrency;
    private long sendAmount;
    private long fee;
    private long totalDebit;
    private String receiveCurrency;
    private BigDecimal fxRate;
    private BigDecimal receiveAmount;
    private String recipientName;
    private LocalDate businessDate;
    private String partnerRef;
    private String failureReason;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant sendingAt;
    private Instant partnerAcceptedAt;
    private Instant completedAt;

    protected Transfer() {
        // for JPA
    }

    static Transfer create(String customerId, String idempotencyKey, String requestHash, String sendCurrency,
            String receiveCurrency, String recipientName, TransferQuote quote, LocalDate businessDate, Instant now) {
        Transfer t = new Transfer();
        t.transferId = UUID.randomUUID().toString();
        t.customerId = customerId;
        t.idempotencyKey = idempotencyKey;
        t.requestHash = requestHash;
        t.status = TransferStatus.REQUESTED;
        t.sendCurrency = sendCurrency;
        t.sendAmount = quote.sendAmount();
        t.fee = quote.fee();
        t.totalDebit = quote.totalDebit();
        t.receiveCurrency = receiveCurrency;
        t.fxRate = quote.fxRate();
        t.receiveAmount = quote.receiveAmount();
        t.recipientName = recipientName;
        t.businessDate = businessDate;
        t.createdAt = now;
        t.updatedAt = now;
        return t;
    }

    /** partnerRef is derived from the internal id, e.g. id 10 -> "PR0000000010". Deterministic on a fresh database. */
    String newPartnerRef() {
        return "PR%010d".formatted(id);
    }

    public Long getId() {
        return id;
    }

    public String getTransferId() {
        return transferId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public String getSendCurrency() {
        return sendCurrency;
    }

    public long getSendAmount() {
        return sendAmount;
    }

    public long getFee() {
        return fee;
    }

    public long getTotalDebit() {
        return totalDebit;
    }

    public String getReceiveCurrency() {
        return receiveCurrency;
    }

    public BigDecimal getFxRate() {
        return fxRate;
    }

    public BigDecimal getReceiveAmount() {
        return receiveAmount;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public String getPartnerRef() {
        return partnerRef;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getSendingAt() {
        return sendingAt;
    }

    public Instant getPartnerAcceptedAt() {
        return partnerAcceptedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
