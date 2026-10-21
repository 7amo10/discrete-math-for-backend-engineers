package com.backend.discrete.week03_graphs;

import java.util.*;

/**
 * Deadlock cycle detector based on Robert Tarjan's Strongly Connected Components (SCC) algorithm.
 * Operates in linear time O(V + E) where V is the number of active transactions and E is the number
 * of lock dependencies.
 */
public class TarjanDeadlockDetector implements DeadlockDetector {

    @Override
    public List<DeadlockCycle> detectCycles(WaitForGraph graph) {
        Objects.requireNonNull(graph, "Graph cannot be null");

        WaitForGraph snapshot = graph.snapshot();
        Set<TransactionId> vertices = snapshot.getVertices();

        Map<TransactionId, Integer> dfn = new HashMap<>();
        Map<TransactionId, Integer> low = new HashMap<>();
        Deque<TransactionId> stack = new ArrayDeque<>();
        Set<TransactionId> onStack = new HashSet<>();
        List<List<TransactionId>> sccs = new ArrayList<>();
        int[] index = new int[]{0};

        for (TransactionId v : vertices) {
            if (!dfn.containsKey(v)) {
                strongConnect(v, snapshot, dfn, low, stack, onStack, sccs, index);
            }
        }

        List<DeadlockCycle> cycles = new ArrayList<>();
        for (List<TransactionId> scc : sccs) {
            if (scc.size() > 1) {
                // Extract an elementary directed cycle from the strongly connected component
                DeadlockCycle cycle = extractCycle(scc, snapshot);
                if (cycle != null) {
                    cycles.add(cycle);
                }
            } else if (scc.size() == 1) {
                TransactionId single = scc.getFirst();
                if (snapshot.containsEdge(single, single)) {
                    cycles.add(new DeadlockCycle(List.of(single)));
                }
            }
        }

        return cycles;
    }

    private void strongConnect(
            TransactionId u,
            WaitForGraph graph,
            Map<TransactionId, Integer> dfn,
            Map<TransactionId, Integer> low,
            Deque<TransactionId> stack,
            Set<TransactionId> onStack,
            List<List<TransactionId>> sccs,
            int[] index
    ) {
        dfn.put(u, index[0]);
        low.put(u, index[0]);
        index[0]++;

        stack.push(u);
        onStack.add(u);

        for (TransactionId v : graph.getOutgoingNeighbors(u)) {
            if (!dfn.containsKey(v)) {
                strongConnect(v, graph, dfn, low, stack, onStack, sccs, index);
                low.put(u, Math.min(low.get(u), low.get(v)));
            } else if (onStack.contains(v)) {
                low.put(u, Math.min(low.get(u), dfn.get(v)));
            }
        }

        if (low.get(u).equals(dfn.get(u))) {
            List<TransactionId> scc = new ArrayList<>();
            TransactionId w;
            do {
                w = stack.pop();
                onStack.remove(w);
                scc.add(w);
            } while (!u.equals(w));

            sccs.add(scc);
        }
    }

    /**
     * Finds a directed cycle within an SCC by performing a restricted DFS starting
     * from an arbitrary vertex in the component.
     */
    private DeadlockCycle extractCycle(List<TransactionId> scc, WaitForGraph graph) {
        Set<TransactionId> sccSet = new HashSet<>(scc);
        TransactionId start = scc.getFirst();

        Map<TransactionId, TransactionId> parent = new HashMap<>();
        Set<TransactionId> visited = new HashSet<>();
        Deque<TransactionId> path = new ArrayDeque<>();

        if (findCycleDfs(start, start, sccSet, graph, visited, parent, path)) {
            List<TransactionId> cycleList = new ArrayList<>(path);
            Collections.reverse(cycleList);
            return new DeadlockCycle(cycleList);
        }

        return null;
    }

    private boolean findCycleDfs(
            TransactionId current,
            TransactionId target,
            Set<TransactionId> sccSet,
            WaitForGraph graph,
            Set<TransactionId> visited,
            Map<TransactionId, TransactionId> parent,
            Deque<TransactionId> path
    ) {
        visited.add(current);

        for (TransactionId next : graph.getOutgoingNeighbors(current)) {
            if (!sccSet.contains(next)) {
                continue;
            }

            if (next.equals(target)) {
                path.push(current);
                return true;
            }

            if (!visited.contains(next)) {
                parent.put(next, current);
                if (findCycleDfs(next, target, sccSet, graph, visited, parent, path)) {
                    path.push(current);
                    return true;
                }
            }
        }

        return false;
    }
}
