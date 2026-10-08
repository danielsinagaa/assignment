package com.finshot.remittance.transfer;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.finshot.remittance.common.error.ConflictException;
import com.finshot.remittance.common.error.LimitExceededException;
import com.finshot.remittance.common.error.NotFoundException;
import com.finshot.remittance.common.error.ValidationException;
import com.finshot.remittance.customer.CustomerRepository;
import com.finshot.remittance.partner.PartnerOutcome;
import com.finshot.remittance.partner.PartnerSender;
import com.finshot.remittance.transfer.dto.CreateTransferRequest;

/**
 * Transaction boundaries are written out with TransactionTemplate (not @Transactional)
 * so it is visible which steps run inside a transaction and which do not (the partner call).
 */
@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    static final long DAILY_LIMIT_KRW = 3_000_000;
    static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");
    private static final List<TransferStatus> COUNTS_FOR_LIMIT =
            List.of(TransferStatus.REQUESTED, TransferStatus.SENDING, TransferStatus.COMPLETED);

    private final TransferRepository transfers;
    private final CustomerRepository customers;
    private final PartnerSender partnerSender;
    private final TransactionTemplate tx;
    private final Clock clock;

    public TransferService(TransferRepository transfers, CustomerRepository customers, PartnerSender partnerSender,
            PlatformTransactionManager transactionManager, Clock clock) {
        this.transfers = transfers;
        this.customers = customers;
        this.partnerSender = partnerSender;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** Result of a create: the transfer, and whether it was an earlier transfer returned for a retry. */
    public record CreateResult(Transfer transfer, boolean replayed) {
    }

    public CreateResult create(String idempotencyKey, CreateTransferRequest request) {
        if (!IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw new ValidationException("Idempotency-Key must be 1-64 characters: letters, digits, '-' or '_'");
        }
        long sendAmount = SendAmountParser.parse(request.sendAmount());
        if (!customers.existsById(request.customerId())) {
            throw new ValidationException("unknown customerId: " + request.customerId());
        }
        String requestHash = RequestHasher.hash(request.customerId(), request.sendCurrency(), sendAmount,
                request.receiveCurrency(), request.recipientName());

        try {
            return tx.execute(status -> createInTransaction(idempotencyKey, requestHash, sendAmount, request));
        } catch (DataIntegrityViolationException e) {
            // Lost a race on the UNIQUE idempotency_key: another request inserted it first. Its transaction has
            // committed, so we can read it now and answer like a normal retry.
            Transfer existing = transfers.findByIdempotencyKey(idempotencyKey).orElseThrow(() -> e);
            return replayOrConflict(existing, requestHash);
        }
    }

    private CreateResult createInTransaction(String idempotencyKey, String requestHash, long sendAmount,
            CreateTransferRequest request) {
        // 1. Lock the customer row. Creates for this customer now run one at a time.
        customers.findByIdForUpdate(request.customerId())
                .orElseThrow(() -> new ValidationException("unknown customerId: " + request.customerId()));

        // 2. Is this a retry? Checked after the lock, so a parallel identical request is already committed and visible.
        var existing = transfers.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return replayOrConflict(existing.get(), requestHash);
        }

        // 3. Daily limit, also under the lock, so two parallel requests cannot both pass.
        Instant now = now();
        LocalDate businessDate = LocalDate.ofInstant(now, BUSINESS_ZONE);
        long usedToday = transfers.sumSendAmount(request.customerId(), businessDate, COUNTS_FOR_LIMIT);
        if (usedToday + sendAmount > DAILY_LIMIT_KRW) {
            throw new LimitExceededException("Daily limit of %d KRW exceeded: already used %d, requested %d"
                    .formatted(DAILY_LIMIT_KRW, usedToday, sendAmount));
        }

        // 4. Insert. saveAndFlush runs the INSERT now, so a UNIQUE violation is thrown here and not at commit.
        Transfer transfer = Transfer.create(request.customerId(), idempotencyKey, requestHash, request.sendCurrency(),
                request.receiveCurrency(), request.recipientName(), TransferQuote.of(sendAmount), businessDate, now);
        return new CreateResult(transfers.saveAndFlush(transfer), false);
    }

    private static CreateResult replayOrConflict(Transfer existing, String requestHash) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new ConflictException("Idempotency-Key was already used with a different request");
        }
        return new CreateResult(existing, true);
    }

    public Transfer get(String transferId) {
        return transfers.findByTransferId(transferId)
                .orElseThrow(() -> new NotFoundException("transfer not found: " + transferId));
    }

    public Transfer cancel(String transferId) {
        return tx.execute(status -> {
            Transfer transfer = get(transferId);
            requireTransition(transfer, TransferStatus.CANCELLED);
            int updated = transfers.changeStatus(transfer.getId(), TransferStatus.REQUESTED, TransferStatus.CANCELLED,
                    now());
            if (updated == 0) {
                throw new ConflictException("transfer status changed concurrently; cannot cancel");
            }
            return get(transferId);
        });
    }

    /**
     * Moves a transfer to SENDING and calls the partner.
     * Step 1 commits SENDING before the partner call, so a callback that arrives early finds SENDING.
     * Step 3 only changes SENDING rows, so it never overwrites a COMPLETED set by that callback.
     */
    public Transfer send(String transferId) {
        // Step 1 (transaction): REQUESTED -> SENDING, store partnerRef.
        Transfer sending = tx.execute(status -> {
            Transfer transfer = get(transferId);
            requireTransition(transfer, TransferStatus.SENDING);
            if (transfers.markSending(transfer.getId(), transfer.newPartnerRef(), now()) == 0) {
                throw new ConflictException("transfer status changed concurrently; cannot send");
            }
            return get(transferId);
        });

        // Step 2 (no transaction): call the partner, up to 3 attempts.
        PartnerOutcome outcome = partnerSender.sendWithRetry(sending.getPartnerRef());

        // Step 3 (transaction): save the result. ACCEPTED keeps SENDING until the callback.
        tx.executeWithoutResult(status -> {
            if (outcome == PartnerOutcome.ACCEPTED) {
                transfers.markPartnerAccepted(sending.getId(), now());
            } else if (transfers.markFailed(sending.getId(), "Partner rejected the request "
                    + "(3 of 3 attempts failed)", now()) == 0) {
                log.warn("transfer {} is no longer SENDING; partner failure not applied", transferId);
            }
        });
        return get(transferId);
    }

    private static void requireTransition(Transfer transfer, TransferStatus next) {
        if (!transfer.getStatus().canTransitionTo(next)) {
            throw new ConflictException("cannot change transfer from %s to %s".formatted(transfer.getStatus(), next));
        }
    }

    /** Millisecond precision, the same as the database column, so a replayed response is byte-for-byte equal. */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}
