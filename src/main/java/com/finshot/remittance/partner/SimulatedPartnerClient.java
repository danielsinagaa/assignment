package com.finshot.remittance.partner;

import java.time.Duration;

import org.springframework.stereotype.Component;

/**
 * Fake partner. A pure function of partnerRef (no random numbers), so every run gives the same result.
 * <p>
 * The brief gives two rules that disagree: {@code abs(partnerRef.hashCode()) % 10 < 3} and
 * "fails when the last digit of partnerRef is 0, 1, or 2". We use the last-digit rule: it is the bold sentence,
 * and scenario S9 ("choose a partnerRef that ends in 0-2") only works with it. See README, Assumptions.
 */
@Component
public class SimulatedPartnerClient implements PartnerClient {

    static final Duration LATENCY = Duration.ofMillis(200);

    @Override
    public PartnerOutcome submit(String partnerRef) {
        sleep(LATENCY); // every attempt takes at least 200 ms
        return failsFor(partnerRef) ? PartnerOutcome.REJECTED : PartnerOutcome.ACCEPTED;
    }

    static boolean failsFor(String partnerRef) {
        char last = partnerRef.charAt(partnerRef.length() - 1);
        return last == '0' || last == '1' || last == '2';
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling partner", e);
        }
    }
}
