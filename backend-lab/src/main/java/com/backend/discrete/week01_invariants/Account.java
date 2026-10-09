package com.backend.discrete.week01_invariants;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents a financial account in the ledger state machine.
 * All monetary amounts are modeled in fixed-point integers (cents)
 * to eliminate IEEE 754 floating-point inaccuracies.
 *
 * <p>Invariant property: For any account, balanceInCents >= 0 (no unbacked overdraft).
 */
public class Account {

    private final String accountId;
    private final AtomicLong balanceInCents;
    private final AtomicLong version;

    public Account(String accountId, long initialBalanceInCents) {
        if (initialBalanceInCents < 0) {
            throw new IllegalArgumentException("Initial balance cannot be negative: " + initialBalanceInCents);
        }
        this.accountId = Objects.requireNonNull(accountId, "accountId cannot be null");
        this.balanceInCents = new AtomicLong(initialBalanceInCents);
        this.version = new AtomicLong(0);
    }

    public String getAccountId() {
        return accountId;
    }

    public long getBalanceInCents() {
        return balanceInCents.get();
    }

    public long getVersion() {
        return version.get();
    }

    /**
     * Atomically debits the account if sufficient funds are available.
     *
     * @param amountInCents the amount to deduct (must be positive)
     * @return true if debit succeeded; false if insufficient funds
     */
    public synchronized boolean debit(long amountInCents) {
        if (amountInCents <= 0) {
            throw new IllegalArgumentException("Debit amount must be positive: " + amountInCents);
        }
        long current = balanceInCents.get();
        if (current < amountInCents) {
            return false;
        }
        balanceInCents.set(current - amountInCents);
        version.incrementAndGet();
        return true;
    }

    /**
     * Atomically credits the account with the specified amount.
     *
     * @param amountInCents the amount to add (must be positive)
     */
    public synchronized void credit(long amountInCents) {
        if (amountInCents <= 0) {
            throw new IllegalArgumentException("Credit amount must be positive: " + amountInCents);
        }
        balanceInCents.addAndGet(amountInCents);
        version.incrementAndGet();
    }

    @Override
    public String toString() {
        return "Account{" +
                "id='" + accountId + '\'' +
                ", balance=" + balanceInCents.get() +
                ", ver=" + version.get() +
                '}';
    }
}
