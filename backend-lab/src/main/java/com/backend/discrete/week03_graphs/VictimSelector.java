package com.backend.discrete.week03_graphs;

import java.util.Map;

/**
 * Strategy interface to select which transaction in a deadlock cycle must be aborted.
 */
public interface VictimSelector {

    /**
     * Chooses the victim transaction from a detected deadlock cycle.
     *
     * @param cycle the circular dependency
     * @param metadataMap transaction metadata lookup table
     * @return the selected transaction to abort
     */
    TransactionId selectVictim(DeadlockCycle cycle, Map<TransactionId, TransactionMetadata> metadataMap);
}
