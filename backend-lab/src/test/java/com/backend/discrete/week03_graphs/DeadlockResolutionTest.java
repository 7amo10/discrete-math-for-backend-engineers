package com.backend.discrete.week03_graphs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Week 3 Lab Verification: Graph Topologies & Deadlock Resolution")
class DeadlockResolutionTest {

    @Test
    @DisplayName("Verify direct 2-transaction circular deadlock detection (T1 -> T2 -> T1)")
    void testDirectTwoTransactionDeadlockCycle() {
        WaitForGraph graph = new WaitForGraph();
        TransactionId t1 = TransactionId.of("tx-1");
        TransactionId t2 = TransactionId.of("tx-2");

        graph.addEdge(t1, t2);
        graph.addEdge(t2, t1);

        TarjanDeadlockDetector tarjan = new TarjanDeadlockDetector();
        List<DeadlockCycle> cycles = tarjan.detectCycles(graph);

        assertEquals(1, cycles.size(), "Should detect exactly one deadlock cycle");
        DeadlockCycle cycle = cycles.getFirst();
        assertEquals(2, cycle.size());
        assertTrue(cycle.contains(t1));
        assertTrue(cycle.contains(t2));

        DfsCycleDetector dfs = new DfsCycleDetector();
        List<DeadlockCycle> dfsCycles = dfs.detectCycles(graph);
        assertEquals(1, dfsCycles.size(), "DFS detector should also detect the 2-node cycle");
    }

    @Test
    @DisplayName("Verify multi-transaction circular deadlock detection (T1 -> T2 -> T3 -> T1)")
    void testMultiTransactionCircularDeadlock() {
        WaitForGraph graph = new WaitForGraph();
        TransactionId t1 = TransactionId.of("tx-1");
        TransactionId t2 = TransactionId.of("tx-2");
        TransactionId t3 = TransactionId.of("tx-3");

        graph.addEdge(t1, t2);
        graph.addEdge(t2, t3);
        graph.addEdge(t3, t1);

        TarjanDeadlockDetector tarjan = new TarjanDeadlockDetector();
        List<DeadlockCycle> cycles = tarjan.detectCycles(graph);

        assertEquals(1, cycles.size());
        DeadlockCycle cycle = cycles.getFirst();
        assertEquals(3, cycle.size());
        assertTrue(cycle.contains(t1));
        assertTrue(cycle.contains(t2));
        assertTrue(cycle.contains(t3));
    }

    @Test
    @DisplayName("Verify DAG topologies contain no deadlocks (Acyclic Property)")
    void testDirectedAcyclicGraphNoDeadlock() {
        WaitForGraph graph = new WaitForGraph();
        TransactionId t1 = TransactionId.of("tx-root");
        TransactionId t2 = TransactionId.of("tx-mid-1");
        TransactionId t3 = TransactionId.of("tx-mid-2");
        TransactionId t4 = TransactionId.of("tx-sink");

        // Diamond DAG: T1 -> T2 -> T4, T1 -> T3 -> T4
        graph.addEdge(t1, t2);
        graph.addEdge(t1, t3);
        graph.addEdge(t2, t4);
        graph.addEdge(t3, t4);

        TarjanDeadlockDetector tarjan = new TarjanDeadlockDetector();
        List<DeadlockCycle> cycles = tarjan.detectCycles(graph);

        assertTrue(cycles.isEmpty(), "Diamond DAG must not trigger any deadlock detection");

        DfsCycleDetector dfs = new DfsCycleDetector();
        assertTrue(dfs.detectCycles(graph).isEmpty(), "DFS detector must confirm graph is acyclic");
    }

    @Test
    @DisplayName("Verify Minimum-Cost victim selection preserves high-work batch transaction")
    void testMinimumCostVictimSelectionPreservesBatchWork() {
        TransactionId tBatch = TransactionId.of("tx-batch-analytics");
        TransactionId tLight = TransactionId.of("tx-quick-update");

        long now = System.currentTimeMillis();
        // Batch transaction has written 10,000 rows, started 5 seconds ago
        TransactionMetadata mBatch = new TransactionMetadata(tBatch, now - 5000, 10_000, 10);
        // Quick update has written only 1 row, started 2 milliseconds ago
        TransactionMetadata mLight = new TransactionMetadata(tLight, now - 2, 1, 1);

        Map<TransactionId, TransactionMetadata> metaMap = Map.of(tBatch, mBatch, tLight, mLight);
        DeadlockCycle cycle = new DeadlockCycle(List.of(tBatch, tLight));

        VictimSelector minCostSelector = new MinimumCostVictimSelector();
        TransactionId victim = minCostSelector.selectVictim(cycle, metaMap);

        assertEquals(tLight, victim, "Minimal-cost selector must pick the lightweight query as victim");
    }

    @Test
    @DisplayName("Verify thread-safe LockManager resolves circular wait under Java 21 Virtual Threads")
    void testLockManagerResolvesCircularWait() throws Exception {
        LockManager lockManager = new LockManager(new TarjanDeadlockDetector(), new MinimumCostVictimSelector());

        ResourceId r1 = ResourceId.of("accounts:row:101");
        ResourceId r2 = ResourceId.of("accounts:row:202");

        TransactionId t1 = TransactionId.of("tx-heavy");
        TransactionId t2 = TransactionId.of("tx-light");

        long now = System.currentTimeMillis();
        lockManager.registerTransaction(new TransactionMetadata(t1, now - 1000, 500, 5));
        lockManager.registerTransaction(new TransactionMetadata(t2, now - 10, 2, 1));

        // T1 acquires R1
        lockManager.acquire(t1, r1);
        // T2 acquires R2
        lockManager.acquire(t2, r2);

        AtomicInteger deadlockExceptionsCaught = new AtomicInteger(0);
        AtomicInteger successfulCompletions = new AtomicInteger(0);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            // Worker 1: T1 attempts to acquire R2
            Future<?> f1 = executor.submit(() -> {
                try {
                    lockManager.acquire(t1, r2);
                    successfulCompletions.incrementAndGet();
                } catch (DeadlockException de) {
                    deadlockExceptionsCaught.incrementAndGet();
                } finally {
                    lockManager.releaseAll(t1);
                }
            });

            // Small delay to ensure T1 is registered as waiting on R2 (held by T2)
            Thread.sleep(50);

            // Worker 2: T2 attempts to acquire R1 (completes the cycle: T1 -> T2 and T2 -> T1)
            Future<?> f2 = executor.submit(() -> {
                try {
                    lockManager.acquire(t2, r1);
                    successfulCompletions.incrementAndGet();
                } catch (DeadlockException de) {
                    deadlockExceptionsCaught.incrementAndGet();
                } finally {
                    lockManager.releaseAll(t2);
                }
            });

            f1.get(5, TimeUnit.SECONDS);
            f2.get(5, TimeUnit.SECONDS);
        }

        // Exactly one transaction should be aborted as victim, and the other must succeed
        assertEquals(1, deadlockExceptionsCaught.get(), "Exactly one transaction must receive DeadlockException");
        assertEquals(1, successfulCompletions.get(), "Survivor transaction must complete acquisition and commit");
        assertEquals(1, lockManager.getDeadlocksDetected(), "LockManager must detect exactly 1 deadlock");
        assertEquals(1, lockManager.getDeadlocksResolved(), "LockManager must resolve exactly 1 deadlock");
    }

    @Test
    @DisplayName("Verify DeadlockResolutionSimulator benchmarks work preservation")
    void testContentionBenchmarkSimulator() {
        DeadlockResolutionSimulator simulator = new DeadlockResolutionSimulator();
        var result = simulator.runContentionBenchmark(new MinimumCostVictimSelector(), "MinCost", 50);

        assertEquals(50, result.totalDeadlocks());
        assertTrue(result.totalRowsPreserved() > result.totalRowsWasted(),
                "Minimum-cost selection must preserve significantly more work than it discards");
        assertTrue(result.wastedWorkPercentage() < 10.0,
                "Wasted work percentage under min-cost selection should be < 10%");
        assertTrue(result.avgResolutionTimeMs() < 5.0,
                "Average graph cycle resolution time should be under 5ms");
    }
}
