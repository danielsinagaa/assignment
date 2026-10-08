package com.finshot.remittance.transfer;

import java.util.Set;

/**
 * <pre>
 * REQUESTED ──► SENDING ──► COMPLETED
 *     │             └─────► FAILED
 *     └──► CANCELLED
 * </pre>
 * Any transition not listed here is rejected with 409.
 */
public enum TransferStatus {
    REQUESTED,
    SENDING,
    COMPLETED,
    FAILED,
    CANCELLED;

    public boolean canTransitionTo(TransferStatus next) {
        return allowedNext().contains(next);
    }

    private Set<TransferStatus> allowedNext() {
        return switch (this) {
            case REQUESTED -> Set.of(SENDING, CANCELLED);
            case SENDING -> Set.of(COMPLETED, FAILED);
            case COMPLETED, FAILED, CANCELLED -> Set.of();
        };
    }
}
