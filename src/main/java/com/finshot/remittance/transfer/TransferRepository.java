package com.finshot.remittance.transfer;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * State changes are compare-and-set: "UPDATE ... WHERE id = ? AND status = ?".
 * They return the number of updated rows; 0 means the status was not what we expected.
 */
public interface TransferRepository extends JpaRepository<Transfer, Long> {

    Optional<Transfer> findByTransferId(String transferId);

    Optional<Transfer> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Transfer t where t.partnerRef = :partnerRef")
    Optional<Transfer> findByPartnerRefForUpdate(@Param("partnerRef") String partnerRef);

    @Query("""
            select coalesce(sum(t.sendAmount), 0) from Transfer t
            where t.customerId = :customerId and t.businessDate = :businessDate and t.status in :statuses""")
    long sumSendAmount(@Param("customerId") String customerId, @Param("businessDate") LocalDate businessDate,
            @Param("statuses") Collection<TransferStatus> statuses);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Transfer t set t.status = :to, t.updatedAt = :now
            where t.id = :id and t.status = :from""")
    int changeStatus(@Param("id") long id, @Param("from") TransferStatus from, @Param("to") TransferStatus to,
            @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Transfer t set t.status = 'SENDING', t.partnerRef = :partnerRef, t.sendingAt = :now, t.updatedAt = :now
            where t.id = :id and t.status = 'REQUESTED'""")
    int markSending(@Param("id") long id, @Param("partnerRef") String partnerRef, @Param("now") Instant now);

    /** No status condition: the partner did accept, even if a fast callback already completed the transfer. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Transfer t set t.partnerAcceptedAt = :now, t.updatedAt = :now where t.id = :id")
    int markPartnerAccepted(@Param("id") long id, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Transfer t set t.status = 'FAILED', t.failureReason = :reason, t.updatedAt = :now
            where t.id = :id and t.status = 'SENDING'""")
    int markFailed(@Param("id") long id, @Param("reason") String reason, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Transfer t set t.status = 'COMPLETED', t.completedAt = :now, t.updatedAt = :now
            where t.id = :id and t.status = 'SENDING'""")
    int markCompleted(@Param("id") long id, @Param("now") Instant now);
}
