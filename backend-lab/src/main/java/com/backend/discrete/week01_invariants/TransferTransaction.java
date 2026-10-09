package com.backend.discrete.week01_invariants;

import java.time.Instant;
import java.util.Objects;

/**
 * Encapsulates an individual transfer transaction in the state machine.
 */
public class TransferTransaction {

    private final String transactionId;
    private final String sourceAccountId;
    private final String targetAccountId;
    private final long amountInCents;
    private final Instant createdAt;
    private volatile TransferStatus status;
    private volatile String failureReason;

    public TransferTransaction(String transactionId, String sourceAccountId, String targetAccountId, long amountInCents) {
        if (amountInCents <= 0) {
            throw new IllegalArgumentException("Transfer amount must be positive: " + amountInCents);
        }
        this.transactionId = Objects.requireNonNull(transactionId, "transactionId cannot be null");
        this.sourceAccountId = Objects.requireNonNull(sourceAccountId, "sourceAccountId cannot be null");
        this.targetAccountId = Objects.requireNonNull(targetAccountId, "targetAccountId cannot be null");
        if (sourceAccountId.equals(targetAccountId)) {
            throw new IllegalArgumentException("Cannot transfer to the same account: " + sourceAccountId);
        }
        this.amountInCents = amountInCents;
        this.createdAt = Instant.now();
        this.status = TransferStatus.INITIATED;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getSourceAccountId() {
        return sourceAccountId;
    }

    public String getTargetAccountId() {
        return targetAccountId;
    }

    public long getAmountInCents() {
        return amountInCents;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public synchronized void setStatus(TransferStatus newStatus) {
        this.status = Objects.requireNonNull(newStatus, "newStatus cannot be null");
    }

    public String getFailureReason() {
        return failureReason;
    }

    public synchronized void fail(TransferStatus failureStatus, String reason) {
        this.status = failureStatus;
        this.failureReason = reason;
    }

    @Override
    public String toString() {
        return "TransferTransaction{" +
                "txId='" + transactionId + '\'' +
                ", from='" + sourceAccountId + '\'' +
                ", to='" + targetAccountId + '\'' +
                ", amount=" + amountInCents +
                ", status=" + status +
                '}';
    }
}
