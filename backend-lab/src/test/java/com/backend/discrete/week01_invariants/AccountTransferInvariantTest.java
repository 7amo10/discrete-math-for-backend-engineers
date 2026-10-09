package com.backend.discrete.week01_invariants;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Week 1: State Machine Invariants & Distributed Saga Verification")
class AccountTransferInvariantTest {

    @Test
    @DisplayName("Base Case & Sequential Transfers: Invariant A + B == S strictly preserved")
    void testBaseCaseAndSequentialTransfers() {
        Account a = new Account("ACC-A", 100_000); // $1,000.00
        Account b = new Account("ACC-B", 50_000);  // $500.00
        long initialTotal = 150_000;

        LedgerStateMachine stateMachine = new LedgerStateMachine(List.of(a, b));
        SagaCoordinator coordinator = new SagaCoordinator(stateMachine);

        // Verify Base Case
        stateMachine.assertSystemicInvariant();
        assertThat(stateMachine.calculateSystemicTotal()).isEqualTo(initialTotal);

        // Step 1: Transfer $200 from A to B
        TransferTransaction tx1 = coordinator.executeTransfer("TX-001", "ACC-A", "ACC-B", 20_000);
        assertThat(tx1.getStatus()).isEqualTo(TransferStatus.COMMITTED);
        assertThat(a.getBalanceInCents()).isEqualTo(80_000);
        assertThat(b.getBalanceInCents()).isEqualTo(70_000);
        stateMachine.assertSystemicInvariant();

        // Step 2: Transfer $450 from B to A
        TransferTransaction tx2 = coordinator.executeTransfer("TX-002", "ACC-B", "ACC-A", 45_000);
        assertThat(tx2.getStatus()).isEqualTo(TransferStatus.COMMITTED);
        assertThat(a.getBalanceInCents()).isEqualTo(125_000);
        assertThat(b.getBalanceInCents()).isEqualTo(25_000);
        stateMachine.assertSystemicInvariant();

        assertThat(stateMachine.calculateSystemicTotal()).isEqualTo(initialTotal);
    }

    @Test
    @DisplayName("Downstream Failure & Compensating Rollback: Preserves Invariant")
    void testSimulatedNetworkFailureAndCompensatingRollback() {
        Account a = new Account("ACC-A", 100_000);
        Account b = new Account("ACC-B", 50_000);
        long initialTotal = 150_000;

        LedgerStateMachine stateMachine = new LedgerStateMachine(List.of(a, b));
        SagaCoordinator coordinator = new SagaCoordinator(stateMachine);

        // Inject 100% downstream failure to simulate network timeout on credit
        coordinator.setSimulatedFailureRate(1.0);

        TransferTransaction tx = coordinator.executeTransfer("TX-FAIL-01", "ACC-A", "ACC-B", 30_000);

        // Transaction must be safely rolled back
        assertThat(tx.getStatus()).isEqualTo(TransferStatus.ROLLED_BACK);
        assertThat(tx.getFailureReason()).contains("timed out");

        // Compensating credit must have refunded account A
        assertThat(a.getBalanceInCents()).isEqualTo(100_000);
        assertThat(b.getBalanceInCents()).isEqualTo(50_000);

        // Invariant strictly preserved
        stateMachine.assertSystemicInvariant();
        assertThat(stateMachine.calculateSystemicTotal()).isEqualTo(initialTotal);
    }

    @Test
    @DisplayName("High Concurrency Chaos Test (Java 21 Virtual Threads): 5,000 Transfers with 30% Chaos")
    void testHighConcurrencyChaosTransfersWithVirtualThreads() throws InterruptedException {
        int numAccounts = 10;
        long initialBalancePerAccount = 100_000; // $1,000.00 each
        long expectedSystemicTotal = numAccounts * initialBalancePerAccount; // $10,000.00 total

        List<Account> accounts = new ArrayList<>();
        for (int i = 0; i < numAccounts; i++) {
            accounts.add(new Account("ACC-" + i, initialBalancePerAccount));
        }

        LedgerStateMachine stateMachine = new LedgerStateMachine(accounts);
        SagaCoordinator coordinator = new SagaCoordinator(stateMachine);
        // Inject 30% random network timeouts
        coordinator.setSimulatedFailureRate(0.30);

        int totalTransfers = 5_000;
        CountDownLatch latch = new CountDownLatch(totalTransfers);
        AtomicInteger committedCount = new AtomicInteger(0);
        AtomicInteger rolledBackCount = new AtomicInteger(0);
        AtomicInteger failedInitialCount = new AtomicInteger(0);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < totalTransfers; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        int fromIdx = ThreadLocalRandom.current().nextInt(numAccounts);
                        int toIdx;
                        do {
                            toIdx = ThreadLocalRandom.current().nextInt(numAccounts);
                        } while (toIdx == fromIdx);

                        long amount = ThreadLocalRandom.current().nextLong(100, 2_000); // $1 to $20
                        String txId = "CONC-TX-" + index;

                        TransferTransaction tx = coordinator.executeTransfer(
                                txId,
                                "ACC-" + fromIdx,
                                "ACC-" + toIdx,
                                amount
                        );

                        if (tx.getStatus() == TransferStatus.COMMITTED) {
                            committedCount.incrementAndGet();
                        } else if (tx.getStatus() == TransferStatus.ROLLED_BACK) {
                            rolledBackCount.incrementAndGet();
                        } else if (tx.getStatus() == TransferStatus.FAILED_INITIAL) {
                            failedInitialCount.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            boolean completed = latch.await(15, TimeUnit.SECONDS);
            assertThat(completed).isTrue();
        }

        System.out.printf("[INFO] Chaos Test Results: Committed=%d, RolledBack=%d, FailedInitial=%d%n",
                committedCount.get(), rolledBackCount.get(), failedInitialCount.get());

        // THE GOLD STANDARD PROOF:
        // Regardless of how many transactions succeeded or rolled back,
        // the systemic balance MUST be strictly invariant!
        stateMachine.assertSystemicInvariant();
        assertThat(stateMachine.calculateSystemicTotal()).isEqualTo(expectedSystemicTotal);
    }

    @Test
    @DisplayName("Double-Entry Equilibrium: Sum(Debits) == Sum(Credits) across all settled entries")
    void testDoubleEntryJournalEquilibrium() {
        Account a = new Account("ACC-A", 500_000);
        Account b = new Account("ACC-B", 500_000);

        LedgerStateMachine stateMachine = new LedgerStateMachine(List.of(a, b));
        SagaCoordinator coordinator = new SagaCoordinator(stateMachine);

        // Mix of successful and failing transfers
        coordinator.setSimulatedFailureRate(0.5);

        for (int i = 0; i < 50; i++) {
            coordinator.executeTransfer("TX-EQUIL-" + i, "ACC-A", "ACC-B", 1_000);
        }

        // Verify Double-Entry Accounting Invariant
        long sumDebits = stateMachine.getLedgerJournal().stream()
                .filter(e -> e.type() == EntryType.DEBIT)
                .mapToLong(LedgerEntry::amountInCents)
                .sum();

        long sumCredits = stateMachine.getLedgerJournal().stream()
                .filter(e -> e.type() == EntryType.CREDIT)
                .mapToLong(LedgerEntry::amountInCents)
                .sum();

        assertThat(sumDebits).isEqualTo(sumCredits);
        stateMachine.assertSystemicInvariant();
    }

    @Test
    @DisplayName("Insufficient Funds: Safe Rejection without Balance Leakage")
    void testInsufficientFundsDoesNotAlterInvariant() {
        Account a = new Account("ACC-A", 1_000); // $10.00
        Account b = new Account("ACC-B", 5_000); // $50.00

        LedgerStateMachine stateMachine = new LedgerStateMachine(List.of(a, b));
        SagaCoordinator coordinator = new SagaCoordinator(stateMachine);

        // Attempt transfer exceeding balance
        TransferTransaction tx = coordinator.executeTransfer("TX-OVERDRAFT", "ACC-A", "ACC-B", 100_000);

        assertThat(tx.getStatus()).isEqualTo(TransferStatus.FAILED_INITIAL);
        assertThat(a.getBalanceInCents()).isEqualTo(1_000);
        assertThat(b.getBalanceInCents()).isEqualTo(5_000);
        stateMachine.assertSystemicInvariant();
    }
}
