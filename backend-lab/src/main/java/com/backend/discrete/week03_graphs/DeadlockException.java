package com.backend.discrete.week03_graphs;

/**
 * Exception thrown when a transaction is aborted to resolve a circular Wait-For Graph deadlock.
 * Equivalent to PostgreSQL error code 40P01 (deadlock_detected).
 */
public class DeadlockException extends RuntimeException {

    private final TransactionId victimId;
    private final DeadlockCycle cycle;

    public DeadlockException(TransactionId victimId, DeadlockCycle cycle) {
        super(String.format("Deadlock detected: transaction '%s' was selected as victim to break cycle [%s]",
                victimId, cycle));
        this.victimId = victimId;
        this.cycle = cycle;
    }

    public TransactionId getVictimId() {
        return victimId;
    }

    public DeadlockCycle getCycle() {
        return cycle;
    }
}
