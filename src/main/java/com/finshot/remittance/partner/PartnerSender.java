package com.finshot.remittance.partner;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Calls the partner with retries: 3 attempts in total, waiting 200 ms and then 400 ms between them. */
@Component
public class PartnerSender {

    private static final Logger log = LoggerFactory.getLogger(PartnerSender.class);

    static final int MAX_ATTEMPTS = 3;
    static final Duration FIRST_BACKOFF = Duration.ofMillis(200);

    private final PartnerClient partnerClient;

    public PartnerSender(PartnerClient partnerClient) {
        this.partnerClient = partnerClient;
    }

    /** Never called inside a database transaction: this can take more than a second. */
    public PartnerOutcome sendWithRetry(String partnerRef) {
        Duration backoff = FIRST_BACKOFF;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            PartnerOutcome outcome = callOnce(partnerRef, attempt);
            if (outcome == PartnerOutcome.ACCEPTED) {
                return PartnerOutcome.ACCEPTED;
            }
            if (attempt < MAX_ATTEMPTS) {
                sleep(backoff);
                backoff = backoff.multipliedBy(2);
            }
        }
        return PartnerOutcome.REJECTED;
    }

    private PartnerOutcome callOnce(String partnerRef, int attempt) {
        try {
            PartnerOutcome outcome = partnerClient.submit(partnerRef);
            log.info("partner attempt={}/{} partnerRef={} outcome={}", attempt, MAX_ATTEMPTS, partnerRef, outcome);
            return outcome;
        } catch (RuntimeException e) {
            log.warn("partner attempt={}/{} partnerRef={} outcome=ERROR ({})",
                    attempt, MAX_ATTEMPTS, partnerRef, e.getMessage());
            return PartnerOutcome.REJECTED;
        }
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during partner retry backoff", e);
        }
    }
}
