package com.backend.discrete.week01_invariants;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable ledger journal entry.
 *
 * <p>In double-entry bookkeeping:
 * Every transaction generates at least one DEBIT and one CREDIT of equal value.
 * Invariant: Sum(Debits) == Sum(Credits) across the entire journal.
 */
public record LedgerEntry(
        String entryId,
        String transactionId,
        String accountId,
        EntryType type,
        long amountInCents,
        Instant timestamp
) {
    public LedgerEntry {
        Objects.requireNonNull(entryId, "entryId cannot be null");
        Objects.requireNonNull(transactionId, "transactionId cannot be null");
        Objects.requireNonNull(accountId, "accountId cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        if (amountInCents <= 0) {
            throw new IllegalArgumentException("Ledger amount must be positive: " + amountInCents);
        }
    }
}
