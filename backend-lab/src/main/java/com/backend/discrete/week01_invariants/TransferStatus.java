package com.backend.discrete.week01_invariants;

/**
 * Lifecycle states of an account transfer in the Saga state machine.
 *
 * <pre>
 *   [INITIATED]
 *        |
 *   (step: debit)
 *        v
 *   [DEBITED] ----------------------------------+
 *        |                                      |
 *   (step: credit)                      (network/system error)
 *        v                                      v
 *   [COMMITTED] (terminal)             [COMPENSATING]
 *                                               |
 *                                       (refund credit)
 *                                               v
 *                                      [ROLLED_BACK] (terminal)
 * </pre>
 */
public enum TransferStatus {
    INITIATED,
    DEBITED,
    COMMITTED,
    COMPENSATING,
    ROLLED_BACK,
    FAILED_INITIAL
}
