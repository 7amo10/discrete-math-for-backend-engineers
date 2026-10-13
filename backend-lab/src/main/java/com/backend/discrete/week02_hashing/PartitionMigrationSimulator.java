package com.backend.discrete.week02_hashing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simulates cluster topology changes and measures key migration and load skew.
 */
public final class PartitionMigrationSimulator {

    public record MigrationResult(
            String strategy,
            int initialNodes,
            int newNodes,
            int totalKeys,
            int migratedKeys,
            double migrationPercentage,
            ConsistentHashRing.LoadMetrics postMigrationMetrics
    ) {}

    /**
     * Simulates scaling naive modulo sharding from initialNodeCount to initialNodeCount + 1.
     */
    public static MigrationResult simulateNaiveScaling(List<String> keys, int initialNodeCount) {
        NaiveModuloRouter router = new NaiveModuloRouter();
        for (int i = 0; i < initialNodeCount; i++) {
            router.addNode(ServerNode.of("node-" + i));
        }

        Map<String, ServerNode> initialAssignments = new HashMap<>();
        for (String key : keys) {
            initialAssignments.put(key, router.route(key));
        }

        ServerNode newNode = ServerNode.of("node-" + initialNodeCount);
        router.addNode(newNode);

        int migrated = 0;
        Map<ServerNode, Integer> postDistribution = new HashMap<>();
        for (ServerNode node : router.getNodes()) {
            postDistribution.put(node, 0);
        }

        for (String key : keys) {
            ServerNode newAssignment = router.route(key);
            postDistribution.merge(newAssignment, 1, Integer::sum);
            if (!newAssignment.equals(initialAssignments.get(key))) {
                migrated++;
            }
        }

        double pct = ((double) migrated / keys.size()) * 100.0;
        double mean = (double) keys.size() / router.getNodeCount();
        double varianceSum = 0.0;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;

        for (int count : postDistribution.values()) {
            varianceSum += Math.pow(count - mean, 2);
            min = Math.min(min, count);
            max = Math.max(max, count);
        }
        double stdDev = Math.sqrt(varianceSum / router.getNodeCount());
        double cv = mean > 0 ? stdDev / mean : 0.0;

        ConsistentHashRing.LoadMetrics metrics = new ConsistentHashRing.LoadMetrics(
                keys.size(), router.getNodeCount(), mean, stdDev, cv, min, max
        );

        return new MigrationResult(
                "Naive Modulo [hash(k) % N]",
                initialNodeCount,
                initialNodeCount + 1,
                keys.size(),
                migrated,
                pct,
                metrics
        );
    }

    /**
     * Simulates scaling a consistent hash ring from initialNodeCount to initialNodeCount + 1.
     */
    public static MigrationResult simulateConsistentRingScaling(
            List<String> keys,
            int initialNodeCount,
            int vnodesPerNode
    ) {
        ConsistentHashRing ring = new ConsistentHashRing(vnodesPerNode);
        for (int i = 0; i < initialNodeCount; i++) {
            ring.addNode(ServerNode.of("node-" + i));
        }

        Map<String, ServerNode> initialAssignments = new HashMap<>();
        for (String key : keys) {
            initialAssignments.put(key, ring.route(key));
        }

        ServerNode newNode = ServerNode.of("node-" + initialNodeCount);
        ring.addNode(newNode);

        int migrated = 0;
        for (String key : keys) {
            ServerNode newAssignment = ring.route(key);
            if (!newAssignment.equals(initialAssignments.get(key))) {
                migrated++;
            }
        }

        double pct = ((double) migrated / keys.size()) * 100.0;
        ConsistentHashRing.LoadMetrics metrics = ring.calculateLoadMetrics(keys);

        return new MigrationResult(
                "Consistent Hash Ring [Vnodes=" + vnodesPerNode + "]",
                initialNodeCount,
                initialNodeCount + 1,
                keys.size(),
                migrated,
                pct,
                metrics
        );
    }

    /**
     * Generates a sample of synthetic partition keys.
     */
    public static List<String> generateKeys(int count) {
        List<String> keys = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            keys.add("account-partition-key-" + i + "-" + Integer.toHexString(i * 31));
        }
        return keys;
    }
}
