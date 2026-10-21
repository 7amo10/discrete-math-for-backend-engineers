package com.backend.discrete.week03_graphs;

import java.util.*;

/**
 * Cycle detector using classical 3-color Depth First Search (WHITE, GRAY, BLACK).
 * Detects back-edges to gray ancestors in O(V + E) time.
 */
public class DfsCycleDetector implements DeadlockDetector {

    private enum Color {
        WHITE, // Unvisited
        GRAY,  // Currently in exploration recursion stack
        BLACK  // Fully explored
    }

    @Override
    public List<DeadlockCycle> detectCycles(WaitForGraph graph) {
        Objects.requireNonNull(graph, "Graph cannot be null");

        WaitForGraph snapshot = graph.snapshot();
        Set<TransactionId> vertices = snapshot.getVertices();

        Map<TransactionId, Color> colors = new HashMap<>();
        Map<TransactionId, TransactionId> parent = new HashMap<>();
        for (TransactionId v : vertices) {
            colors.put(v, Color.WHITE);
        }

        List<DeadlockCycle> cycles = new ArrayList<>();

        for (TransactionId v : vertices) {
            if (colors.get(v) == Color.WHITE) {
                dfs(v, snapshot, colors, parent, cycles);
            }
        }

        return cycles;
    }

    private void dfs(
            TransactionId u,
            WaitForGraph graph,
            Map<TransactionId, Color> colors,
            Map<TransactionId, TransactionId> parent,
            List<DeadlockCycle> cycles
    ) {
        colors.put(u, Color.GRAY);

        for (TransactionId v : graph.getOutgoingNeighbors(u)) {
            Color vColor = colors.get(v);
            if (vColor == Color.GRAY) {
                // Back-edge detected: cycle from v to u, closed by edge u -> v
                List<TransactionId> cyclePath = new ArrayList<>();
                cyclePath.add(u);
                TransactionId curr = u;
                while (!curr.equals(v) && parent.containsKey(curr)) {
                    curr = parent.get(curr);
                    cyclePath.add(curr);
                }
                Collections.reverse(cyclePath);
                cycles.add(new DeadlockCycle(cyclePath));
            } else if (vColor == Color.WHITE) {
                parent.put(v, u);
                dfs(v, graph, colors, parent, cycles);
            }
        }

        colors.put(u, Color.BLACK);
    }
}
