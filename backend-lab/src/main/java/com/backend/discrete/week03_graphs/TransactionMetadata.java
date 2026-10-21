package com.backend.discrete.week03_graphs;

import java.util.Objects;

/**
 * Metadata associated with an active transaction, used by victim selection heuristics
 * to compute rollback cost metrics.
 *
 * @param id transaction identifier
 * @param startedAtEpochMs start timestamp in milliseconds
 * @param rowsModified number of mutations or rows written so far
 * @param priority static priority weight (higher values denote higher-priority workloads)
 */
public record TransactionMetadata(
        TransactionId id,
        long startedAtEpochMs,
        int rowsModified,
        int priority
) {

    public TransactionMetadata {
        Objects.requireNonNull(id, "TransactionId must not be null");
        if (rowsModified < 0) {
            throw new IllegalArgumentException("rowsModified cannot be negative");
        }
        if (priority < 0) {
            throw new IllegalArgumentException("priority cannot be negative");
        }
    }

    /**
     * Computes the linear composite rollback cost metric:
     * Cost = (ageWeight * elapsedMs) + (rowsWeight * rowsModified) + (priorityWeight * priority)
     *
     * Higher cost indicates more discarded work or higher business disruption if aborted.
     */
    public double computeRollbackCost(long currentEpochMs, double ageWeight, double rowsWeight, double priorityWeight) {
        long elapsedMs = Math.max(0, currentEpochMs - startedAtEpochMs);
        return (ageWeight * elapsedMs) + (rowsWeight * rowsModified) + (priorityWeight * priority);
    }
}
