package com.backend.discrete.week03_graphs;

import java.util.Objects;

/**
 * Unique identifier for a database transaction.
 *
 * @param value the unique transaction string (e.g. "tx-101")
 */
public record TransactionId(String value) implements Comparable<TransactionId> {

    public TransactionId {
        Objects.requireNonNull(value, "TransactionId value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("TransactionId value must not be blank");
        }
    }

    public static TransactionId of(String value) {
        return new TransactionId(value);
    }

    @Override
    public int compareTo(TransactionId other) {
        return this.value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
