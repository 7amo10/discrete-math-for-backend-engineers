package com.backend.discrete.week03_graphs;

import java.util.List;

/**
 * Strategy interface for cycle detection algorithms on Wait-For Graphs.
 */
public interface DeadlockDetector {

    /**
     * Inspects the Wait-For Graph and returns all detected circular dependencies.
     * If the graph is a Directed Acyclic Graph (DAG), returns an empty list.
     *
     * @param graph the wait-for graph to analyze
     * @return list of detected deadlock cycles
     */
    List<DeadlockCycle> detectCycles(WaitForGraph graph);
}
