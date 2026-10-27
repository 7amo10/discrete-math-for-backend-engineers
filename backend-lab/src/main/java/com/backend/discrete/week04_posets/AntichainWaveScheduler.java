package com.backend.discrete.week04_posets;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * Parallel workflow scheduler based on Antichain Decomposition of Partially Ordered Sets.
 * <p>
 * An <b>antichain</b> is a subset of elements A &sube; X where every pair (u, v) is mutually
 * incomparable: {@code u || v} (neither {@code u &prec; v} nor {@code v &prec; u}).
 * In concurrent computing, an antichain represents an embarrassingly parallel execution wave:
 * all tasks within the antichain can execute simultaneously without locks, barriers, or data races.
 * </p>
 * <p>
 * Uses Java 21 Virtual Threads ({@link Executors#newVirtualThreadPerTaskExecutor()}) to scale
 * lightweight concurrency across arbitrarily wide antichain waves.
 * </p>
 */
public class AntichainWaveScheduler {

    private final CriticalPathAnalyzer criticalPathAnalyzer = new CriticalPathAnalyzer();

    /**
     * Decomposes the poset into a minimal sequence of maximal antichains (parallel execution waves).
     * By Mirsky's Theorem, the number of waves equals the number of nodes in the longest chain (height).
     *
     * @param graph acyclic poset task graph
     * @return ordered list of waves, where each wave is an antichain of mutually incomparable tasks
     */
    public List<List<TaskId>> computeAntichainWaves(PosetGraph graph) {
        Objects.requireNonNull(graph, "PosetGraph cannot be null");
        graph.validateAcyclic();

        Map<TaskId, Integer> remainingInDegrees = new HashMap<>();
        Queue<TaskId> currentWave = new ArrayDeque<>();

        for (TaskId id : graph.getAllTaskIds()) {
            int inDeg = graph.inDegree(id);
            remainingInDegrees.put(id, inDeg);
            if (inDeg == 0) {
                currentWave.add(id);
            }
        }

        List<List<TaskId>> waves = new ArrayList<>();

        while (!currentWave.isEmpty()) {
            List<TaskId> wave = new ArrayList<>(currentWave);
            currentWave.clear();
            waves.add(Collections.unmodifiableList(wave));

            for (TaskId completed : wave) {
                for (TaskId dependent : graph.getDirectDependents(completed)) {
                    int updated = remainingInDegrees.get(dependent) - 1;
                    remainingInDegrees.put(dependent, updated);
                    if (updated == 0) {
                        currentWave.add(dependent);
                    }
                }
            }
        }

        return Collections.unmodifiableList(waves);
    }

    /**
     * Verifies that a given set of tasks forms a valid antichain (no task depends on another).
     */
    public boolean isValidAntichain(PosetGraph graph, Collection<TaskId> candidate) {
        List<TaskId> list = new ArrayList<>(candidate);
        for (int i = 0; i < list.size(); i++) {
            TaskId u = list.get(i);
            for (int j = i + 1; j < list.size(); j++) {
                TaskId v = list.get(j);
                if (graph.isReachable(u, v) || graph.isReachable(v, u)) {
                    return false; // Not incomparable
                }
            }
        }
        return true;
    }

    /**
     * Executes the task poset concurrently wave-by-wave using Java 21 Virtual Threads.
     *
     * @param graph                acyclic poset task graph
     * @param simulateCostDuration whether to simulate task execution duration via thread sleep
     * @return performance metrics comparing parallel execution against sequential baseline
     */
    public SchedulerMetrics execute(PosetGraph graph, boolean simulateCostDuration) {
        List<List<TaskId>> waves = computeAntichainWaves(graph);
        CriticalPathAnalyzer.CriticalPathResult criticalPath = criticalPathAnalyzer.computeCriticalPath(graph);

        long totalTaskCostMs = graph.getAllTasks().stream()
                .mapToLong(Task::estimatedCostMs)
                .sum();

        Instant start = Instant.now();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (List<TaskId> wave : waves) {
                List<CompletableFuture<Void>> futures = new ArrayList<>(wave.size());

                for (TaskId taskId : wave) {
                    Task task = graph.getTask(taskId);
                    futures.add(CompletableFuture.runAsync(() -> {
                        if (task != null) {
                            if (simulateCostDuration && task.estimatedCostMs() > 0) {
                                try {
                                    Thread.sleep(task.estimatedCostMs());
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    throw new RuntimeException("Task interrupted: " + taskId, e);
                                }
                            }
                            task.execute();
                        }
                    }, executor));
                }

                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            }
        }

        long totalWallClockMs = Duration.between(start, Instant.now()).toMillis();

        return SchedulerMetrics.of(
                totalWallClockMs,
                totalTaskCostMs,
                criticalPath.durationMs(),
                waves.size(),
                graph.vertexCount()
        );
    }
}
