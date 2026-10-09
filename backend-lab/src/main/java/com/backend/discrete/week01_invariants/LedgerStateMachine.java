package com.backend.discrete.week01_invariants;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Formal Discrete State Machine: M = (Q, q0, delta).
 *
 * <p>State Q = (Accounts, ActiveTransactions, LedgerJournal).
 * Initial State q0: Total initial balance across all accounts = S, ActiveTransactions = {}, Journal = {}.
 *
 * <p>Theorem (MIT 6.042J Invariant Principle):
 * Let P(q) be the proposition:
 * "The systemic monetary balance across all accounts and in-flight transaction escrows equals S."
 *
 * 1. Base Case: In initial state q0, P(q0) holds because sum(Balances) == S and inFlight == 0.
 * 2. Inductive Step: For every valid transition q -> q' in delta:
 *    - INITIATE: No balances change -> sum is preserved.
 *    - DEBIT: source balance decreases by T, in-flight escrow increases by T -> sum is preserved.
 *    - CREDIT: in-flight escrow decreases by T, target balance increases by T -> sum is preserved.
 *    - COMPENSATE: in-flight escrow decreases by T, source balance refunded by T -> sum is preserved.
 *
 * Therefore, for all reachable states q, P(q) is TRUE.
 */
public class LedgerStateMachine {

    private final Map<String, Account> accounts = new ConcurrentHashMap<>();
    private final Map<String, TransferTransaction> activeTransactions = new ConcurrentHashMap<>();
    private final List<LedgerEntry> ledgerJournal = new CopyOnWriteArrayList<>();
    private final AtomicLong entrySequence = new AtomicLong(0);
    private final long initialSystemicTotal;

    public LedgerStateMachine(List<Account> initialAccounts) {
        Objects.requireNonNull(initialAccounts, "initialAccounts cannot be null");
        if (initialAccounts.isEmpty()) {
            throw new IllegalArgumentException("Must provide at least one account");
        }
        long sum = 0;
        for (Account a : initialAccounts) {
            this.accounts.put(a.getAccountId(), a);
            sum += a.getBalanceInCents();
        }
        this.initialSystemicTotal = sum;
    }

    public Account getAccount(String accountId) {
        Account account = accounts.get(accountId);
        if (account == null) {
            throw new IllegalArgumentException("Account not found: " + accountId);
        }
        return account;
    }

    public Collection<Account> getAllAccounts() {
        return Collections.unmodifiableCollection(accounts.values());
    }

    public List<LedgerEntry> getLedgerJournal() {
        return Collections.unmodifiableList(ledgerJournal);
    }

    public TransferTransaction getTransaction(String txId) {
        return activeTransactions.get(txId);
    }

    /**
     * Transition 1: INITIATE
     * Creates transaction in INITIATED state. Systemic balance invariant is unaffected.
     */
    public synchronized TransferTransaction initiate(String txId, String sourceId, String targetId, long amountInCents) {
        if (activeTransactions.containsKey(txId)) {
            throw new IllegalStateException("Transaction already exists: " + txId);
        }
        getAccount(sourceId);
        getAccount(targetId);

        TransferTransaction tx = new TransferTransaction(txId, sourceId, targetId, amountInCents);
        activeTransactions.put(txId, tx);
        return tx;
    }

    /**
     * Transition 2: DEBIT
     * Decreases source account by T, and transaction enters DEBITED state (escrowing T).
     * System balance: (Sum(accounts) - T) + (Escrow + T) == Sum(accounts) + Escrow. Preserved!
     */
    public synchronized boolean executeDebit(String txId) {
        TransferTransaction tx = activeTransactions.get(txId);
        if (tx == null || tx.getStatus() != TransferStatus.INITIATED) {
            return false;
        }

        Account source = getAccount(tx.getSourceAccountId());
        boolean debited = source.debit(tx.getAmountInCents());
        if (!debited) {
            tx.fail(TransferStatus.FAILED_INITIAL, "Insufficient funds in source account: " + source.getAccountId());
            return false;
        }

        tx.setStatus(TransferStatus.DEBITED);
        recordLedgerEntry(txId, source.getAccountId(), EntryType.DEBIT, tx.getAmountInCents());
        return true;
    }

    /**
     * Transition 3: CREDIT
     * Target account balance increases by T, transaction enters COMMITTED state (escrow cleared).
     * System balance: (Sum(accounts) + T) + (Escrow - T) == Sum(accounts) + Escrow. Preserved!
     */
    public synchronized boolean executeCredit(String txId) {
        TransferTransaction tx = activeTransactions.get(txId);
        if (tx == null || tx.getStatus() != TransferStatus.DEBITED) {
            return false;
        }

        Account target = getAccount(tx.getTargetAccountId());
        target.credit(tx.getAmountInCents());

        tx.setStatus(TransferStatus.COMMITTED);
        recordLedgerEntry(txId, target.getAccountId(), EntryType.CREDIT, tx.getAmountInCents());
        return true;
    }

    /**
     * Transition 4: COMPENSATE (ROLLBACK)
     * If downstream credit fails, this transition refunds the debited amount T back to the source account.
     * System balance: (Sum(accounts) + T) + (Escrow - T) == Sum(accounts) + Escrow. Preserved!
     */
    public synchronized void compensateRollback(String txId, String reason) {
        TransferTransaction tx = activeTransactions.get(txId);
        if (tx == null) {
            return;
        }

        if (tx.getStatus() == TransferStatus.INITIATED) {
            tx.fail(TransferStatus.FAILED_INITIAL, reason);
            return;
        }

        if (tx.getStatus() == TransferStatus.DEBITED) {
            tx.setStatus(TransferStatus.COMPENSATING);

            Account source = getAccount(tx.getSourceAccountId());
            source.credit(tx.getAmountInCents());

            tx.fail(TransferStatus.ROLLED_BACK, reason);
            // Record compensating CREDIT to restore double-entry journal equilibrium
            recordLedgerEntry(txId, source.getAccountId(), EntryType.CREDIT, tx.getAmountInCents());
        }
    }

    private void recordLedgerEntry(String txId, String accountId, EntryType type, long amount) {
        String entryId = "ENTRY-" + entrySequence.incrementAndGet();
        LedgerEntry entry = new LedgerEntry(entryId, txId, accountId, type, amount, Instant.now());
        ledgerJournal.add(entry);
    }

    /**
     * Calculates the systemic total:
     * sum of all account balances + all in-flight escrowed debits.
     */
    public synchronized long calculateSystemicTotal() {
        long accountsSum = 0;
        for (Account a : accounts.values()) {
            accountsSum += a.getBalanceInCents();
        }

        long inFlightEscrow = 0;
        for (TransferTransaction tx : activeTransactions.values()) {
            if (tx.getStatus() == TransferStatus.DEBITED || tx.getStatus() == TransferStatus.COMPENSATING) {
                inFlightEscrow += tx.getAmountInCents();
            }
        }

        return accountsSum + inFlightEscrow;
    }

    /**
     * Verifies the formal invariant:
     * 1. Systemic total strictly equals S.
     * 2. Double-entry total debits == total credits for all completed/rolled-back operations.
     */
    public synchronized void assertSystemicInvariant() {
        long currentTotal = calculateSystemicTotal();
        if (currentTotal != initialSystemicTotal) {
            throw new AssertionError(String.format(
                    "INVARIANT VIOLATION: Expected systemic total %d cents, but found %d cents! Discrepancy: %d cents",
                    initialSystemicTotal, currentTotal, (currentTotal - initialSystemicTotal)
            ));
        }

        long totalDebits = 0;
        long totalCredits = 0;
        for (LedgerEntry entry : ledgerJournal) {
            if (entry.type() == EntryType.DEBIT) {
                totalDebits += entry.amountInCents();
            } else if (entry.type() == EntryType.CREDIT) {
                totalCredits += entry.amountInCents();
            }
        }

        // In double entry, all settled transactions have equal debits and credits
        long settledDebits = 0;
        long settledCredits = 0;
        for (TransferTransaction tx : activeTransactions.values()) {
            if (tx.getStatus() == TransferStatus.COMMITTED || tx.getStatus() == TransferStatus.ROLLED_BACK) {
                settledDebits += tx.getAmountInCents();
                settledCredits += tx.getAmountInCents();
            }
        }

        if (totalDebits - totalCredits != settledDebits - settledCredits) {
            throw new AssertionError("DOUBLE-ENTRY LEDGER SKEW: Journal debits != credits!");
        }
    }

    public long getInitialSystemicTotal() {
        return initialSystemicTotal;
    }
}
