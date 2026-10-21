package com.backend.discrete.week03_graphs;

/**
 * State lifecycle of a transactional worker thread.
 */
public enum TransactionStatus {
    ACTIVE,
    WAITING,
    COMMITTED,
    ABORTED
}
