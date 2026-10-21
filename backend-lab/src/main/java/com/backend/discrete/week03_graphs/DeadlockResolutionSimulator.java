package com.backend.discrete.week03_graphs;

import java.util.*;

/**
 * Benchmark simulator comparing discrete Wait-For Graph (WFG) cycle detection
 * against naive arbitrary lock wait timeouts.
 */
public class DeadlockResolutionSimulator {

    public record SimulationResult(
            String strategy,
            int totalDeadlocks,
            long totalRowsPreserved,
            long totalRowsWasted,
            double wastedWorkPercentage,
            double avgResolutionTimeMs
    ) {}

    /**
     * Simulates a suite of cyclic lock contention scenarios and evaluates
     * wasted work metrics between Minimum-Cost victim selection and Youngest-Transaction heuristic.
     */
    public SimulationResult runContentionBenchmark(VictimSelector selector, String strategyName, int iterations) {
        LockManager lockManager = new LockManager(new TarjanDeadlockDetector(), selector);

        int totalDeadlocks = 0;
        long totalRowsPreserved = 0;
        long totalRowsWasted = 0;
        long totalTimeNs = 0;

        for (int i = 0; i < iterations; i++) {
            ResourceId r1 = ResourceId.of("account:row:A_" + i);
            ResourceId r2 = ResourceId.of("account:row:B_" + i);

            // T1: Heavy batch transaction (written 500 rows, started 100ms ago, high priority 5)
            TransactionId t1 = TransactionId.of("tx-batch-" + i);
            TransactionMetadata m1 = new TransactionMetadata(t1, System.currentTimeMillis() - 100, 500, 5);

            // T2: Lightweight fast query (written 2 rows, started 5ms ago, low priority 1)
            TransactionId t2 = TransactionId.of("tx-light-" + i);
            TransactionMetadata m2 = new TransactionMetadata(t2, System.currentTimeMillis() - 5, 2, 1);

            lockManager.registerTransaction(m1);
            lockManager.registerTransaction(m2);

            // T1 acquires R1, T2 acquires R2
            lockManager.acquire(t1, r1);
            lockManager.acquire(t2, r2);

            long start = System.nanoTime();
            try {
                // T1 tries to acquire R2 (blocks, waiting for T2)
                // In a single-threaded simulation loop, simulate T1 waiting for T2
                WaitForGraph testGraph = new WaitForGraph();
                testGraph.addEdge(t1, t2); // T1 waits for T2 (holds R2)
                testGraph.addEdge(t2, t1); // T2 waits for T1 (holds R1) -> Forms cycle!

                TarjanDeadlockDetector detector = new TarjanDeadlockDetector();
                List<DeadlockCycle> cycles = detector.detectCycles(testGraph);
                if (!cycles.isEmpty()) {
                    totalDeadlocks++;
                    DeadlockCycle cycle = cycles.getFirst();
                    Map<TransactionId, TransactionMetadata> metaMap = Map.of(t1, m1, t2, m2);
                    TransactionId victim = selector.selectVictim(cycle, metaMap);

                    TransactionMetadata victimMeta = metaMap.get(victim);
                    TransactionMetadata survivorMeta = victim.equals(t1) ? m2 : m1;

                    totalRowsWasted += victimMeta.rowsModified();
                    totalRowsPreserved += survivorMeta.rowsModified();
                }
            } finally {
                totalTimeNs += (System.nanoTime() - start);
                lockManager.releaseAll(t1);
                lockManager.releaseAll(t2);
            }
        }

        long totalRows = totalRowsPreserved + totalRowsWasted;
        double wastedPct = totalRows == 0 ? 0.0 : (double) totalRowsWasted / totalRows * 100.0;
        double avgTimeMs = (double) totalTimeNs / (iterations * 1_000_000.0);

        return new SimulationResult(
                strategyName,
                totalDeadlocks,
                totalRowsPreserved,
                totalRowsWasted,
                wastedPct,
                avgTimeMs
        );
    }
}
