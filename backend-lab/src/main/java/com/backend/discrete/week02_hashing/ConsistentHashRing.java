package com.backend.discrete.week02_hashing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Consistent hash ring mapped to an unsigned 32-bit circular keyspace [0, 2^32 - 1].
 * Supports virtual nodes per host and replica routing with physical host deduplication.
 */
public final class ConsistentHashRing {

    public static final int DEFAULT_VNODES = 150;

    private final HashFunction hashFunction;
    private final int vnodesPerNode;
    private final ConcurrentSkipListMap<Long, ServerNode> ring;
    private final Set<ServerNode> physicalNodes;

    public ConsistentHashRing() {
        this(DEFAULT_VNODES, new HashFunction());
    }

    public ConsistentHashRing(int vnodesPerNode) {
        this(vnodesPerNode, new HashFunction());
    }

    public ConsistentHashRing(int vnodesPerNode, HashFunction hashFunction) {
        if (vnodesPerNode <= 0) {
            throw new IllegalArgumentException("vnodesPerNode must be strictly positive: " + vnodesPerNode);
        }
        this.vnodesPerNode = vnodesPerNode;
        this.hashFunction = Objects.requireNonNull(hashFunction, "hashFunction must not be null");
        this.ring = new ConcurrentSkipListMap<>();
        this.physicalNodes = ConcurrentHashMap.newKeySet();
    }

    /**
     * Adds a physical host to the ring by placing V virtual node tokens.
     */
    public synchronized void addNode(ServerNode node) {
        Objects.requireNonNull(node, "node must not be null");
        if (physicalNodes.add(node)) {
            for (int i = 0; i < vnodesPerNode; i++) {
                long token = computeVirtualToken(node, i);
                ring.put(token, node);
            }
        }
    }

    /**
     * Removes a physical host and all associated virtual node tokens from the ring.
     */
    public synchronized boolean removeNode(ServerNode node) {
        Objects.requireNonNull(node, "node must not be null");
        if (physicalNodes.remove(node)) {
            for (int i = 0; i < vnodesPerNode; i++) {
                long token = computeVirtualToken(node, i);
                ring.remove(token, node);
            }
            return true;
        }
        return false;
    }

    /**
     * Routes a partition key to the nearest clockwise node token on the ring.
     */
    public ServerNode route(String key) {
        if (ring.isEmpty()) {
            throw new IllegalStateException("Consistent hash ring is empty");
        }
        long keyHash = hashFunction.hash(key);
        Map.Entry<Long, ServerNode> entry = ring.ceilingEntry(keyHash);

        if (entry == null) {
            entry = ring.firstEntry();
        }
        return entry.getValue();
    }

    /**
     * Routes a partition key to R distinct physical hosts by traversing clockwise.
     */
    public List<ServerNode> routeReplicas(String key, int replicationFactor) {
        if (ring.isEmpty()) {
            throw new IllegalStateException("Consistent hash ring is empty");
        }
        if (replicationFactor <= 0) {
            throw new IllegalArgumentException("Replication factor must be positive: " + replicationFactor);
        }

        int targetReplicas = Math.min(replicationFactor, physicalNodes.size());
        Set<ServerNode> replicas = new LinkedHashSet<>(targetReplicas);

        long keyHash = hashFunction.hash(key);

        for (ServerNode node : ring.tailMap(keyHash).values()) {
            replicas.add(node);
            if (replicas.size() == targetReplicas) {
                return new ArrayList<>(replicas);
            }
        }

        for (ServerNode node : ring.headMap(keyHash).values()) {
            replicas.add(node);
            if (replicas.size() == targetReplicas) {
                return new ArrayList<>(replicas);
            }
        }

        return new ArrayList<>(replicas);
    }

    public int getVnodesPerNode() {
        return vnodesPerNode;
    }

    public int getPhysicalNodeCount() {
        return physicalNodes.size();
    }

    public int getTokenCount() {
        return ring.size();
    }

    public Set<ServerNode> getPhysicalNodes() {
        return Collections.unmodifiableSet(physicalNodes);
    }

    public Map<Long, ServerNode> getRingTokens() {
        return Collections.unmodifiableMap(ring);
    }

    /**
     * Computes the number of keys assigned to each physical host.
     */
    public Map<ServerNode, Integer> getLoadDistribution(List<String> keys) {
        Map<ServerNode, Integer> distribution = new HashMap<>();
        for (ServerNode node : physicalNodes) {
            distribution.put(node, 0);
        }
        for (String key : keys) {
            ServerNode assigned = route(key);
            distribution.merge(assigned, 1, Integer::sum);
        }
        return distribution;
    }

    /**
     * Statistical load metrics across active physical nodes.
     */
    public record LoadMetrics(
            int totalKeys,
            int nodeCount,
            double mean,
            double standardDeviation,
            double coefficientOfVariation,
            int minKeys,
            int maxKeys
    ) {}

    public LoadMetrics calculateLoadMetrics(List<String> keys) {
        if (physicalNodes.isEmpty() || keys.isEmpty()) {
            return new LoadMetrics(keys.size(), physicalNodes.size(), 0, 0, 0, 0, 0);
        }
        Map<ServerNode, Integer> distribution = getLoadDistribution(keys);

        double mean = (double) keys.size() / physicalNodes.size();
        double varianceSum = 0.0;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;

        for (int count : distribution.values()) {
            varianceSum += Math.pow(count - mean, 2);
            min = Math.min(min, count);
            max = Math.max(max, count);
        }

        double stdDev = Math.sqrt(varianceSum / physicalNodes.size());
        double cv = mean > 0 ? stdDev / mean : 0.0;

        return new LoadMetrics(keys.size(), physicalNodes.size(), mean, stdDev, cv, min, max);
    }

    private long computeVirtualToken(ServerNode node, int vnodeIndex) {
        String tokenKey = node.id() + "#vnode#" + vnodeIndex;
        return hashFunction.hash(tokenKey);
    }
}
