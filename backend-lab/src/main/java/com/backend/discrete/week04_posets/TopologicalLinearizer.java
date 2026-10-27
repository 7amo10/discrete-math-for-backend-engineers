package com.backend.discrete.week04_posets;

import java.util.*;

/**
 * Computes a Linear Extension (Topological Sort) of a finite poset P = (X, &prec;).
 * <p>
 * A linear extension is a total ordering of tasks such that whenever {@code u &prec; v},
 * {@code u} appears before {@code v} in the sequence. This is the exact algorithm used
 * by single-threaded task orchestrators, migration sequence generators, and Spring bean loaders.
 * </p>
 * <p>
 * Complexity: &Omicron;(V + E) time and &Omicron;(V) space using Kahn's algorithm.
 * </p>
 */
public class TopologicalLinearizer {

    /**
     * Computes a deterministic linear extension of the given poset.
     * When multiple tasks have in-degree 0 concurrently, tasks are ordered by natural TaskId order.
     *
     * @param graph poset task graph
     * @return ordered list of TaskIds forming a valid linear extension
     * @throws CircularDependencyException if graph contains cycles
     */
    public List<TaskId> linearize(PosetGraph graph) {
        Objects.requireNonNull(graph, "PosetGraph cannot be null");

        Map<TaskId, Integer> inDegrees = new HashMap<>();
        PriorityQueue<TaskId> zeroInDegreeQueue = new PriorityQueue<>();

        for (TaskId id : graph.getAllTaskIds()) {
            int inDeg = graph.inDegree(id);
            inDegrees.put(id, inDeg);
            if (inDeg == 0) {
                zeroInDegreeQueue.add(id);
            }
        }

        List<TaskId> linearOrder = new ArrayList<>(graph.vertexCount());

        while (!zeroInDegreeQueue.isEmpty()) {
            TaskId current = zeroInDegreeQueue.poll();
            linearOrder.add(current);

            for (TaskId dependent : graph.getDirectDependents(current)) {
                int updatedInDeg = inDegrees.get(dependent) - 1;
                inDegrees.put(dependent, updatedInDeg);
                if (updatedInDeg == 0) {
                    zeroInDegreeQueue.add(dependent);
                }
            }
        }

        if (linearOrder.size() < graph.vertexCount()) {
            graph.validateAcyclic();
            throw new CircularDependencyException("Unable to linearize graph: cycle detected", graph.findCycle().orElse(List.of()));
        }

        return Collections.unmodifiableList(linearOrder);
    }

    /**
     * Verifies that an arbitrary sequence of tasks satisfies all poset dependency constraints.
     */
    public boolean isValidLinearExtension(PosetGraph graph, List<TaskId> order) {
        if (order.size() != graph.vertexCount()) {
            return false;
        }

        Map<TaskId, Integer> positionMap = new HashMap<>();
        for (int i = 0; i < order.size(); i++) {
            positionMap.put(order.get(i), i);
        }

        for (TaskId u : graph.getAllTaskIds()) {
            Integer posU = positionMap.get(u);
            if (posU == null) return false;

            for (TaskId v : graph.getDirectDependents(u)) {
                Integer posV = positionMap.get(v);
                if (posV == null || posU >= posV) {
                    return false; // u must precede v
                }
            }
        }
        return true;
    }
}
