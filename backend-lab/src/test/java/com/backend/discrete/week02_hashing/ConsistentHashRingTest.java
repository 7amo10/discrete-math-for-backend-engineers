package com.backend.discrete.week02_hashing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Consistent Hash Ring and Number Theoretic Sharding Tests")
class ConsistentHashRingTest {

    @Test
    @DisplayName("Verifies Euclidean GCD, Bézout identity, and modular multiplicative inverse")
    void testEuclideanGcdAndPulverizer() {
        assertThat(NumberTheory.gcd(1071, 462)).isEqualTo(21);
        assertThat(NumberTheory.gcd(48, 18)).isEqualTo(6);
        assertThat(NumberTheory.gcd(101, 103)).isEqualTo(1);

        long a = 252;
        long b = 198;
        NumberTheory.BezoutCoefficients bezout = NumberTheory.pulverizer(a, b);
        assertThat(bezout.gcd()).isEqualTo(18);
        assertThat(bezout.s() * a + bezout.t() * b).isEqualTo(bezout.gcd());

        long num = 7;
        long mod = 26;
        long inv = NumberTheory.modularInverse(num, mod);
        assertThat(inv).isEqualTo(15);
        assertThat((num * inv) % mod).isEqualTo(1);

        assertThatThrownBy(() -> NumberTheory.modularInverse(24, HashFunction.RING_SIZE))
                .isInstanceOf(ArithmeticException.class)
                .hasMessageContaining("Modular inverse does not exist");

        long c1 = 0xcc9e2d51L;
        assertThat(NumberTheory.gcd(c1, HashFunction.RING_SIZE)).isEqualTo(1);
        long c1Inverse = NumberTheory.modularInverse(c1, HashFunction.RING_SIZE);
        assertThat((int) c1 * (int) c1Inverse).isEqualTo(1);
    }

    @Test
    @DisplayName("Verifies high migration rate when scaling naive modulo router")
    void testNaiveModuloMigrationCatastrophe() {
        int keyCount = 50_000;
        int initialNodes = 10;
        List<String> keys = PartitionMigrationSimulator.generateKeys(keyCount);

        PartitionMigrationSimulator.MigrationResult result =
                PartitionMigrationSimulator.simulateNaiveScaling(keys, initialNodes);

        assertThat(result.migrationPercentage())
                .as("Naive modulo sharding moves approximately N / (N + 1) keys upon node addition")
                .isGreaterThan(85.0);
    }

    @Test
    @DisplayName("Verifies minimal key migration when adding a node to the consistent hash ring")
    void testConsistentHashRingMinimalMigrationTheorem() {
        int keyCount = 50_000;
        int initialNodes = 10;
        int vnodes = 150;
        List<String> keys = PartitionMigrationSimulator.generateKeys(keyCount);

        PartitionMigrationSimulator.MigrationResult result =
                PartitionMigrationSimulator.simulateConsistentRingScaling(keys, initialNodes, vnodes);

        assertThat(result.migrationPercentage())
                .as("Consistent hash ring moves approximately 1 / (N + 1) keys upon node addition")
                .isBetween(6.5, 12.5);
    }

    @Test
    @DisplayName("Verifies load distribution variance reduction using virtual nodes")
    void testPigeonholeHotspotMitigationViaVirtualNodes() {
        int keyCount = 50_000;
        int nodeCount = 8;
        List<String> keys = PartitionMigrationSimulator.generateKeys(keyCount);

        ConsistentHashRing rawRing = new ConsistentHashRing(1);
        for (int i = 0; i < nodeCount; i++) {
            rawRing.addNode(ServerNode.of("raw-node-" + i));
        }
        ConsistentHashRing.LoadMetrics rawMetrics = rawRing.calculateLoadMetrics(keys);

        ConsistentHashRing vnodeRing = new ConsistentHashRing(150);
        for (int i = 0; i < nodeCount; i++) {
            vnodeRing.addNode(ServerNode.of("vnode-node-" + i));
        }
        ConsistentHashRing.LoadMetrics vnodeMetrics = vnodeRing.calculateLoadMetrics(keys);

        assertThat(vnodeMetrics.coefficientOfVariation())
                .as("Virtual nodes reduce coefficient of variation across physical hosts")
                .isLessThan(rawMetrics.coefficientOfVariation() / 2.5);

        double ratio = (double) vnodeMetrics.maxKeys() / vnodeMetrics.minKeys();
        assertThat(ratio).as("Max to min key ratio with virtual nodes remains bounded").isLessThan(1.6);
    }

    @Test
    @DisplayName("Verifies replica routing deduplicates multiple tokens for the same physical node")
    void testReplicaRoutingWithPhysicalDeduplication() {
        ConsistentHashRing ring = new ConsistentHashRing(100);
        ServerNode nodeA = ServerNode.of("node-A", "10.0.0.1", 8080);
        ServerNode nodeB = ServerNode.of("node-B", "10.0.0.2", 8080);
        ServerNode nodeC = ServerNode.of("node-C", "10.0.0.3", 8080);
        ServerNode nodeD = ServerNode.of("node-D", "10.0.0.4", 8080);

        ring.addNode(nodeA);
        ring.addNode(nodeB);
        ring.addNode(nodeC);
        ring.addNode(nodeD);

        List<ServerNode> replicas = ring.routeReplicas("payment-tx-491823", 3);

        assertThat(replicas).hasSize(3);
        Set<ServerNode> uniqueReplicas = new HashSet<>(replicas);
        assertThat(uniqueReplicas)
                .as("Replicas must be distinct physical servers")
                .hasSize(3);
    }

    @Test
    @DisplayName("Verifies concurrent ring lookups and topology updates under virtual threads")
    void testConcurrentRingAccessWithVirtualThreads() throws InterruptedException {
        ConsistentHashRing ring = new ConsistentHashRing(100);
        for (int i = 0; i < 6; i++) {
            ring.addNode(ServerNode.of("cluster-node-" + i));
        }

        int concurrentLookups = 2_000;
        CountDownLatch latch = new CountDownLatch(concurrentLookups);
        AtomicInteger successfulRoutes = new AtomicInteger(0);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < concurrentLookups; i++) {
                final String key = "virtual-thread-key-" + i;
                executor.submit(() -> {
                    try {
                        ServerNode routed = ring.route(key);
                        if (routed != null) {
                            successfulRoutes.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            executor.submit(() -> {
                ServerNode dynamicNode = ServerNode.of("dynamic-worker-node");
                ring.addNode(dynamicNode);
                try {
                    Thread.sleep(5);
                } catch (InterruptedException ignored) {}
                ring.removeNode(dynamicNode);
            });

            boolean finished = latch.await(10, TimeUnit.SECONDS);
            assertThat(finished).isTrue();
            assertThat(successfulRoutes.get()).isEqualTo(concurrentLookups);
        }
    }
}
