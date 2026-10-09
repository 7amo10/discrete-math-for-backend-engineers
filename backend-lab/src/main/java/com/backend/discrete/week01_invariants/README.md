# Week 1: State Machine Invariants & Distributed Saga Rollbacks

## Theoretical Anchor: MIT 6.042J (Fall 2010)
- **Primary Text:** Chapters 1, 2, and 3 (Propositions, Patterns of Proof, Induction & State Machines).
- **Lectures:** Lecture 1 (Introduction & Proofs), Lecture 2 (Induction), Lecture 3 (Strong Induction & State Machines).
- **Core Theorem (Section 3.3):** The Invariant Principle.

---

## 1. Formal Mathematical Formulation

A discrete state machine is defined as a 3-tuple:

$$M = (Q, q_0, \delta)$$

Where:
- $Q$ is the set of all reachable states: $Q = (\mathbf{A}, \mathbf{T}_{active}, \mathbf{J})$ where $\mathbf{A}$ is the map of account balances, $\mathbf{T}_{active}$ is the set of in-flight transactions, and $\mathbf{J}$ is the append-only ledger journal.
- $q_0 \in Q$ is the initial state with initial balances satisfying $\sum_{a \in \mathbf{A}} B_a(t_0) = S$, $\mathbf{T}_{active} = \emptyset$, and $\mathbf{J} = \emptyset$.
- $\delta: Q \times \Sigma \to Q$ is the state transition function.

### The Invariant Predicate $P(q)$

Let $P(q)$ be the predicate:

$$P(q) \iff \sum_{a \in \mathbf{A}} \text{balance}(a) + \sum_{tx \in \mathbf{T}_{active}} \text{escrow}(tx) \equiv S$$

Where $\text{escrow}(tx) = T$ if $tx.\text{status} \in \{\text{DEBITED}, \text{COMPENSATING}\}$, and $0$ otherwise.

### Proof by the Invariant Principle

1. **Base Case:** In state $q_0$, no transactions are active ($\text{escrow} = 0$). By definition of $q_0$, $\sum B_a = S$. Hence $P(q_0)$ is **true**.
2. **Inductive Step:** For any state $q$ where $P(q)$ holds, and any valid transition $q \xrightarrow{\delta} q'$:
   - **Transition $\text{INITIATE}(tx, A, B, T)$:** Creates record in $\text{INITIATED}$ status. Account balances and escrow are unchanged: $\Delta = 0$.
   - **Transition $\text{DEBIT}(tx, A, T)$:** Deducts $T$ from Account $A$; transaction enters $\text{DEBITED}$ status holding $T$ in escrow. System balance: $(B_A - T) + (\text{Escrow} + T) = B_A + \text{Escrow}$. Net change: $\Delta = -T + T = 0$.
   - **Transition $\text{CREDIT}(tx, B, T)$:** Adds $T$ to Account $B$; transaction enters terminal $\text{COMMITTED}$ status, releasing escrow. System balance: $(B_B + T) + (\text{Escrow} - T) = B_B + \text{Escrow}$. Net change: $\Delta = +T - T = 0$.
   - **Transition $\text{COMPENSATE}(tx, A, T)$:** Downstream network timeout or target node failure triggers a compensating refund. Adds $T$ back to source Account $A$; transaction enters terminal $\text{ROLLED\_BACK}$ status, releasing escrow. Net change: $\Delta = +T - T = 0$.

By the **Invariant Principle (MIT 6.042J Theorem 3.3.1)**, $P(q)$ is true for all reachable states $q \in Q$.

---

## 2. Java 21 Production Architecture

This package translates the formal discrete state machine into high-throughput Java 21 backend code:

- `Account.java`: Fixed-point cent balance representation (`long`) with atomic optimistic version control.
- `LedgerEntry.java` & `EntryType.java`: Immutable double-entry bookkeeping journal entries. Guarantees $\sum \text{Debits} \equiv \sum \text{Credits}$.
- `TransferStatus.java`: Discrete state machine lifecycle phases (`INITIATED`, `DEBITED`, `COMMITTED`, `COMPENSATING`, `ROLLED_BACK`, `FAILED_INITIAL`).
- `TransferTransaction.java`: Thread-safe in-flight transaction domain entity.
- `LedgerStateMachine.java`: Encapsulates state machine transitions and exposes `assertSystemicInvariant()` verifying zero monetary leakage.
- `SagaCoordinator.java`: Distributed Saga orchestrator featuring simulated network partition injection and automatic compensating actions.

---

## 3. Verification & Test Execution

Run the complete JUnit 5 verification suite via Maven:

```bash
mvn test -Dtest=AccountTransferInvariantTest
```

### Verified Test Scenarios

1. `testBaseCaseAndSequentialTransfers`: Verifies basic transfers strictly preserve $\sum B_i = S$.
2. `testSimulatedNetworkFailureAndCompensatingRollback`: Injects 100% downstream network timeout on credit. Verifies automatic compensation refunds source account with zero balance leakage.
3. `testHighConcurrencyChaosTransfersWithVirtualThreads`: Spawns 5,000 concurrent transfers across 10 accounts under 30% random network dropouts using Java 21 Virtual Threads (`Executors.newVirtualThreadPerTaskExecutor()`). Proves systemic balance equilibrium holds under heavy concurrency.
4. `testDoubleEntryJournalEquilibrium`: Verifies that across all committed and rolled-back entries, total debits match total credits down to the single cent.
