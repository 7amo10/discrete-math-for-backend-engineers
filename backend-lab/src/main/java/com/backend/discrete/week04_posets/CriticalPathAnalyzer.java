package com.backend.discrete.week04_posets;

import java.util.*;

/**
 * Analyzes the critical path (longest chain) and Dilworth antichain bounds of a task poset.
 * <p>
 * In workflow orchestration (Airflow, Bazel, CI/CD):
 * <ul>
 *   <li>The <b>Critical Path</b> is the maximal chain that dictates the absolute theoretical
 *       lower bound on execution time, regardless of available compute resources.</li>
 *   <li><b>Dilworth's Theorem</b> proves that the maximum antichain width equals the minimum
 *       number of worker threads required to achieve optimal parallel speedup without starvation.</li>
 * </ul>
 * </p>
 */
public class CriticalPathAnalyzer {

    private final TopologicalLinearizer linearizer = new TopologicalLinearizer();

    public record CriticalPathResult(
            List<TaskId> path,
            long durationMs
    ) {}

    /**
     * Computes the Critical Path (longest weighted chain) in O(V + E) time using DAG dynamic programming.
     *
     * @param graph acyclic poset task graph
     * @return critical path tasks and theoretical minimum execution duration
     */
    public CriticalPathResult computeCriticalPath(PosetGraph graph) {
        Objects.requireNonNull(graph, "PosetGraph cannot be null");
        graph.validateAcyclic();

        List<TaskId> topoOrder = linearizer.linearize(graph);
        Map<TaskId, Long> dist = new HashMap<>();
        Map<TaskId, TaskId> parent = new HashMap<>();

        for (TaskId id : topoOrder) {
            Task task = graph.getTask(id);
            long cost = task == null ? 0 : task.estimatedCostMs();
            dist.put(id, cost);
        }

        TaskId bestEndNode = null;
        long maxDist = 0;

        for (TaskId u : topoOrder) {
            long currentDist = dist.get(u);
            if (currentDist > maxDist) {
                maxDist = currentDist;
                bestEndNode = u;
            }

            for (TaskId v : graph.getDirectDependents(u)) {
                Task targetTask = graph.getTask(v);
                long targetCost = targetTask == null ? 0 : targetTask.estimatedCostMs();
                long newDist = currentDist + targetCost;

                if (newDist > dist.get(v)) {
                    dist.put(v, newDist);
                    parent.put(v, u);
                }
            }
        }

        // Reconstruct path
        List<TaskId> path = new ArrayList<>();
        if (bestEndNode != null) {
            TaskId curr = bestEndNode;
            while (curr != null) {
                path.add(curr);
                curr = parent.get(curr);
            }
            Collections.reverse(path);
        }

        return new CriticalPathResult(Collections.unmodifiableList(path), maxDist);
    }

    /**
     * Computes the maximum antichain width from the canonical level-based antichain decomposition.
     * By Mirsky's Theorem and Dilworth's Theorem, this provides the optimal concurrency capacity.
     */
    public int computeMaxAntichainWidth(List<List<TaskId>> waves) {
        return waves.stream()
                .mapToInt(List::size)
                .max()
                .orElse(0);
    }
}
