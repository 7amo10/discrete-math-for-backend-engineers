package com.backend.discrete.week03_graphs;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Directed Wait-For Graph (WFG) modeling lock dependency topologies.
 * Vertices V represent active transactions; directed edges (u -> v) represent
 * transaction u waiting for a lock held by transaction v.
 */
public class WaitForGraph {

    private final Map<TransactionId, Set<TransactionId>> outgoingEdges = new ConcurrentHashMap<>();
    private final Map<TransactionId, Set<TransactionId>> incomingEdges = new ConcurrentHashMap<>();
    private final Set<TransactionId> vertices = new CopyOnWriteArraySet<>();

    public WaitForGraph() {
    }

    public synchronized void addVertex(TransactionId tx) {
        Objects.requireNonNull(tx, "Transaction must not be null");
        vertices.add(tx);
        outgoingEdges.computeIfAbsent(tx, k -> new HashSet<>());
        incomingEdges.computeIfAbsent(tx, k -> new HashSet<>());
    }

    public synchronized void addEdge(TransactionId from, TransactionId to) {
        Objects.requireNonNull(from, "From transaction must not be null");
        Objects.requireNonNull(to, "To transaction must not be null");

        addVertex(from);
        addVertex(to);

        outgoingEdges.get(from).add(to);
        incomingEdges.get(to).add(from);
    }

    public synchronized void removeEdge(TransactionId from, TransactionId to) {
        if (from == null || to == null) {
            return;
        }
        Set<TransactionId> out = outgoingEdges.get(from);
        if (out != null) {
            out.remove(to);
        }
        Set<TransactionId> in = incomingEdges.get(to);
        if (in != null) {
            in.remove(from);
        }
    }

    public synchronized void removeVertex(TransactionId tx) {
        if (tx == null || !vertices.contains(tx)) {
            return;
        }

        // Remove all outgoing edges from tx
        Set<TransactionId> out = outgoingEdges.remove(tx);
        if (out != null) {
            for (TransactionId neighbor : out) {
                Set<TransactionId> inc = incomingEdges.get(neighbor);
                if (inc != null) {
                    inc.remove(tx);
                }
            }
        }

        // Remove all incoming edges pointing to tx
        Set<TransactionId> inc = incomingEdges.remove(tx);
        if (inc != null) {
            for (TransactionId waiter : inc) {
                Set<TransactionId> waiterOut = outgoingEdges.get(waiter);
                if (waiterOut != null) {
                    waiterOut.remove(tx);
                }
            }
        }

        vertices.remove(tx);
    }

    public synchronized Set<TransactionId> getVertices() {
        return Collections.unmodifiableSet(new HashSet<>(vertices));
    }

    public synchronized Set<TransactionId> getOutgoingNeighbors(TransactionId tx) {
        Set<TransactionId> neighbors = outgoingEdges.get(tx);
        return neighbors == null ? Collections.emptySet() : Collections.unmodifiableSet(new HashSet<>(neighbors));
    }

    public synchronized Set<TransactionId> getIncomingNeighbors(TransactionId tx) {
        Set<TransactionId> waiters = incomingEdges.get(tx);
        return waiters == null ? Collections.emptySet() : Collections.unmodifiableSet(new HashSet<>(waiters));
    }

    public synchronized boolean containsEdge(TransactionId from, TransactionId to) {
        Set<TransactionId> out = outgoingEdges.get(from);
        return out != null && out.contains(to);
    }

    public synchronized int vertexCount() {
        return vertices.size();
    }

    public synchronized int edgeCount() {
        int count = 0;
        for (Set<TransactionId> edges : outgoingEdges.values()) {
            count += edges.size();
        }
        return count;
    }

    /**
     * Creates an independent detached snapshot of this graph to allow cycle detection
     * and inspection algorithms to execute without holding locks.
     */
    public synchronized WaitForGraph snapshot() {
        WaitForGraph copy = new WaitForGraph();
        for (TransactionId v : vertices) {
            copy.addVertex(v);
        }
        for (Map.Entry<TransactionId, Set<TransactionId>> entry : outgoingEdges.entrySet()) {
            TransactionId from = entry.getKey();
            for (TransactionId to : entry.getValue()) {
                copy.addEdge(from, to);
            }
        }
        return copy;
    }
}
