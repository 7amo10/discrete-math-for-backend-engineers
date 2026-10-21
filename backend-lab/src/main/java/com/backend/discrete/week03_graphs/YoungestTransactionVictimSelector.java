package com.backend.discrete.week03_graphs;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;

/**
 * Heuristic victim selector that aborts the youngest transaction (highest start epoch timestamp)
 * in the deadlock cycle.
 */
public class YoungestTransactionVictimSelector implements VictimSelector {

    @Override
    public TransactionId selectVictim(DeadlockCycle cycle, Map<TransactionId, TransactionMetadata> metadataMap) {
        Objects.requireNonNull(cycle, "Cycle cannot be null");
        Objects.requireNonNull(metadataMap, "MetadataMap cannot be null");

        return cycle.transactions().stream()
                .max(Comparator
                        .<TransactionId>comparingLong(tx -> {
                            TransactionMetadata meta = metadataMap.get(tx);
                            return meta == null ? 0L : meta.startedAtEpochMs();
                        })
                        .thenComparing(TransactionId::value))
                .orElseThrow(() -> new IllegalStateException("Cannot select victim from empty cycle"));
    }
}
