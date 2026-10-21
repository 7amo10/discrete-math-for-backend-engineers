package com.backend.discrete.week03_graphs;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;

/**
 * Victim selector that chooses the transaction with the minimal composite rollback cost,
 * minimizing total discarded work across the database engine.
 */
public class MinimumCostVictimSelector implements VictimSelector {

    private final double ageWeight;
    private final double rowsWeight;
    private final double priorityWeight;

    public MinimumCostVictimSelector() {
        this(0.1, 1.0, 10.0);
    }

    public MinimumCostVictimSelector(double ageWeight, double rowsWeight, double priorityWeight) {
        this.ageWeight = ageWeight;
        this.rowsWeight = rowsWeight;
        this.priorityWeight = priorityWeight;
    }

    @Override
    public TransactionId selectVictim(DeadlockCycle cycle, Map<TransactionId, TransactionMetadata> metadataMap) {
        Objects.requireNonNull(cycle, "Cycle cannot be null");
        Objects.requireNonNull(metadataMap, "MetadataMap cannot be null");

        long now = System.currentTimeMillis();

        return cycle.transactions().stream()
                .min(Comparator
                        .<TransactionId>comparingDouble(tx -> {
                            TransactionMetadata meta = metadataMap.get(tx);
                            if (meta == null) {
                                return 0.0;
                            }
                            return meta.computeRollbackCost(now, ageWeight, rowsWeight, priorityWeight);
                        })
                        .thenComparing(TransactionId::value))
                .orElseThrow(() -> new IllegalStateException("Cannot select victim from empty cycle"));
    }
}
