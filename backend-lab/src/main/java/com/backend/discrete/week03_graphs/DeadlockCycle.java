package com.backend.discrete.week03_graphs;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a directed cycle of blocked transactions in a Wait-For Graph:
 * T_0 -> T_1 -> ... -> T_{k-1} -> T_0
 *
 * @param transactions ordered list of transactions participating in the cycle
 */
public record DeadlockCycle(List<TransactionId> transactions) {

    public DeadlockCycle {
        Objects.requireNonNull(transactions, "Cycle transactions list must not be null");
        if (transactions.isEmpty()) {
            throw new IllegalArgumentException("Deadlock cycle cannot be empty");
        }
        transactions = List.copyOf(transactions);
    }

    public int size() {
        return transactions.size();
    }

    public boolean contains(TransactionId tx) {
        return transactions.contains(tx);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < transactions.size(); i++) {
            sb.append(transactions.get(i));
            sb.append(" -> ");
        }
        sb.append(transactions.getFirst());
        return sb.toString();
    }
}
