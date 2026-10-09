package com.backend.discrete.week01_invariants;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Orchestrator implementing the Saga Pattern for distributed financial transfers.
 *
 * <p>In enterprise distributed systems (e.g. microservices with separate databases),
 * two-phase commit (2PC) is often replaced by Sagas composed of local transactions
 * and compensating actions.
 *
 * <p>Key Principle:
 * Even in the event of arbitrary downstream network timeouts, crashes, or unreachability,
 * the Saga Coordinator guarantees that the systemic balance invariant:
 *   Sum(Balances) == S
 * is strictly preserved across all failure modes.
 */
public class SagaCoordinator {

    private final LedgerStateMachine stateMachine;
    private volatile double simulatedFailureRate = 0.0;

    public SagaCoordinator(LedgerStateMachine stateMachine) {
        this.stateMachine = Objects.requireNonNull(stateMachine, "stateMachine cannot be null");
    }

    /**
     * Configures a simulated downstream failure probability (0.0 to 1.0)
     * to inject chaos and network timeouts between debit and credit.
     */
    public void setSimulatedFailureRate(double failureRate) {
        if (failureRate < 0.0 || failureRate > 1.0) {
            throw new IllegalArgumentException("Failure rate must be between 0.0 and 1.0");
        }
        this.simulatedFailureRate = failureRate;
    }

    /**
     * Executes a full transfer Saga:
     * 1. Initiate state machine transition.
     * 2. Step 1: Debit source account.
     * 3. (Simulate network / RPC call to downstream target service).
     * 4. Step 2: Credit target account.
     * 5. If Step 2 fails, execute compensating transaction (refund source account).
     *
     * @return the resulting TransferTransaction with terminal status (COMMITTED or ROLLED_BACK / FAILED_INITIAL)
     */
    public TransferTransaction executeTransfer(String txId, String fromAccountId, String toAccountId, long amountInCents) {
        TransferTransaction tx = stateMachine.initiate(txId, fromAccountId, toAccountId, amountInCents);

        // Step 1: Local Debit
        boolean debited = stateMachine.executeDebit(txId);
        if (!debited) {
            // Source account had insufficient funds
            return tx;
        }

        // Step 2: Downstream RPC / Credit execution with simulated chaos
        try {
            if (simulatedFailureRate > 0.0 && ThreadLocalRandom.current().nextDouble() < simulatedFailureRate) {
                throw new NetworkTimeoutException("Downstream credit RPC timed out for txId: " + txId);
            }

            boolean credited = stateMachine.executeCredit(txId);
            if (!credited) {
                throw new DownstreamProcessingException("Target node rejected credit for txId: " + txId);
            }
        } catch (Exception e) {
            // DOWNSTREAM FAILURE DETECTED: Execute Compensating Saga Rollback!
            stateMachine.compensateRollback(txId, e.getMessage());
        }

        return tx;
    }

    public LedgerStateMachine getStateMachine() {
        return stateMachine;
    }

    public static class NetworkTimeoutException extends RuntimeException {
        public NetworkTimeoutException(String message) {
            super(message);
        }
    }

    public static class DownstreamProcessingException extends RuntimeException {
        public DownstreamProcessingException(String message) {
            super(message);
        }
    }
}
