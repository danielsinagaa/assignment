package com.finshot.remittance.customer;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<Customer, String> {

    /**
     * SELECT ... FOR UPDATE on the customer row. Creates for the same customer run one at a time,
     * so the idempotency lookup and the daily-limit sum always see each other's committed rows.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Customer c where c.customerId = :customerId")
    Optional<Customer> findByIdForUpdate(@Param("customerId") String customerId);
}
