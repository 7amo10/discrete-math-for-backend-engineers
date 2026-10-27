package com.backend.discrete.week04_posets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskSchedulingTest {

    private final TopologicalLinearizer linearizer = new TopologicalLinearizer();
    private final AntichainWaveScheduler scheduler = new AntichainWaveScheduler();
    private final CriticalPathAnalyzer analyzer = new CriticalPathAnalyzer();

    @Test
    @DisplayName("Theorem 2: Kahn's algorithm produces a valid linear extension respecting all partial order constraints")
    void testLinearExtensionOrderPreservation() {
        PosetGraph graph = new PosetGraph();
        graph.addTask(Task.of("A", 10));
        graph.addTask(Task.of("B", 10));
        graph.addTask(Task.of("C", 10));
        graph.addTask(Task.of("D", 10));
        graph.addTask(Task.of("E", 10));

        // A -> B -> D
        // A -> C -> D
        // D -> E
        graph.addDependency("A", "B");
        graph.addDependency("A", "C");
        graph.addDependency("B", "D");
        graph.addDependency("C", "D");
        graph.addDependency("D", "E");

        List<TaskId> order = linearizer.linearize(graph);

        assertThat(order).hasSize(5);
        assertThat(linearizer.isValidLinearExtension(graph, order)).isTrue();

        // Specific constraints
        assertThat(order.indexOf(TaskId.of("A"))).isLessThan(order.indexOf(TaskId.of("B")));
        assertThat(order.indexOf(TaskId.of("A"))).isLessThan(order.indexOf(TaskId.of("C")));
        assertThat(order.indexOf(TaskId.of("B"))).isLessThan(order.indexOf(TaskId.of("D")));
        assertThat(order.indexOf(TaskId.of("C"))).isLessThan(order.indexOf(TaskId.of("D")));
        assertThat(order.indexOf(TaskId.of("D"))).isLessThan(order.indexOf(TaskId.of("E")));
    }

    @Test
    @DisplayName("Theorem 1: Directed cycles violate strict partial order asymmetry and throw CircularDependencyException")
    void testCycleDetectionThrowsException() {
        PosetGraph graph = new PosetGraph();
        graph.addTask(Task.of("ServiceA", 10));
        graph.addTask(Task.of("ServiceB", 10));
        graph.addTask(Task.of("ServiceC", 10));

        // Circular loop: A -> B -> C -> A
        graph.addDependency("ServiceA", "ServiceB");
        graph.addDependency("ServiceB", "ServiceC");
        graph.addDependency("ServiceC", "ServiceA");

        assertThat(graph.hasCycle()).isTrue();

        assertThatThrownBy(() -> linearizer.linearize(graph))
                .isInstanceOf(CircularDependencyException.class)
                .hasMessageContaining("Cycle detected");
    }

    @Test
    @DisplayName("Hasse Diagram: Transitive reduction prunes redundant shortcuts while preserving exact reachability")
    void testTransitiveReductionHasseDiagram() {
        PosetGraph graph = new PosetGraph();
        graph.addTask(Task.of("compile", 10));
        graph.addTask(Task.of("unit_test", 10));
        graph.addTask(Task.of("package", 10));

        // compile -> unit_test -> package
        graph.addDependency("compile", "unit_test");
        graph.addDependency("unit_test", "package");

        // Redundant direct shortcut: compile -> package
        graph.addDependency("compile", "package");

        assertThat(graph.edgeCount()).isEqualTo(3);

        PosetGraph hasse = graph.computeTransitiveReduction();

        // In the Hasse diagram, the shortcut compile -> package is pruned
        assertThat(hasse.edgeCount()).isEqualTo(2);
        assertThat(hasse.getDirectDependents(TaskId.of("compile"))).containsExactly(TaskId.of("unit_test"));
        assertThat(hasse.getDirectDependents(TaskId.of("unit_test"))).containsExactly(TaskId.of("package"));

        // Reachability is identically preserved
        assertThat(hasse.isReachable(TaskId.of("compile"), TaskId.of("package"))).isTrue();
    }

    @Test
    @DisplayName("Mirsky's Theorem: Level decomposition partitions graph into valid antichains of mutually incomparable tasks")
    void testAntichainIncomparability() {
        PosetGraph graph = new PosetGraph();
        graph.addTask(Task.of("T1", 10));
        graph.addTask(Task.of("T2", 10));
        graph.addTask(Task.of("T3", 10));
        graph.addTask(Task.of("T4", 10));
        graph.addTask(Task.of("T5", 10));

        // Wave 0: T1, T2 (in-degree 0)
        // Wave 1: T3, T4 (depends on T1 or T2)
        // Wave 2: T5 (depends on T3 and T4)
        graph.addDependency("T1", "T3");
        graph.addDependency("T2", "T4");
        graph.addDependency("T3", "T5");
        graph.addDependency("T4", "T5");

        List<List<TaskId>> waves = scheduler.computeAntichainWaves(graph);

        assertThat(waves).hasSize(3);

        // Every single wave must be a verified antichain (mutually incomparable elements)
        for (List<TaskId> wave : waves) {
            assertThat(scheduler.isValidAntichain(graph, wave)).isTrue();
        }

        assertThat(waves.get(0)).containsExactlyInAnyOrder(TaskId.of("T1"), TaskId.of("T2"));
        assertThat(waves.get(1)).containsExactlyInAnyOrder(TaskId.of("T3"), TaskId.of("T4"));
        assertThat(waves.get(2)).containsExactly(TaskId.of("T5"));
    }

    @Test
    @DisplayName("Theorem 3: Critical path calculates theoretical minimum duration and Dilworth concurrency width")
    void testCriticalPathCalculation() {
        PosetGraph graph = new PosetGraph();
        graph.addTask(Task.of("init", 10));
        graph.addTask(Task.of("heavy_calc", 100)); // Bottleneck path
        graph.addTask(Task.of("fast_calc", 20));
        graph.addTask(Task.of("aggregate", 15));

        graph.addDependency("init", "heavy_calc");
        graph.addDependency("init", "fast_calc");
        graph.addDependency("heavy_calc", "aggregate");
        graph.addDependency("fast_calc", "aggregate");

        CriticalPathAnalyzer.CriticalPathResult result = analyzer.computeCriticalPath(graph);

        // Longest chain: init (10) -> heavy_calc (100) -> aggregate (15) = 125ms
        assertThat(result.durationMs()).isEqualTo(125);
        assertThat(result.path()).containsExactly(
                TaskId.of("init"),
                TaskId.of("heavy_calc"),
                TaskId.of("aggregate")
        );

        List<List<TaskId>> waves = scheduler.computeAntichainWaves(graph);
        int maxAntichainWidth = analyzer.computeMaxAntichainWidth(waves);

        // Max antichain width is 2: {heavy_calc, fast_calc}
        assertThat(maxAntichainWidth).isEqualTo(2);
    }

    @Test
    @DisplayName("Systems Benchmark: Enterprise ETL pipeline executes concurrently on Virtual Threads with high speedup")
    void testVirtualThreadConcurrencyAndSpeedup() {
        WorkflowSimulator simulator = new WorkflowSimulator();
        WorkflowSimulator.BenchmarkResult result = simulator.runPipelineBenchmark();

        assertThat(result.executedTaskCount()).isEqualTo(9);
        assertThat(result.maxAntichainWidth()).isEqualTo(3);
        assertThat(result.criticalPathHopCount()).isEqualTo(5);

        // Sequential duration: 40+40+40 + 30+30 + 20 + 25+25 + 15 = 265 ms
        // Critical path duration: 40 + 30 + 20 + 25 + 15 = 130 ms
        assertThat(result.metrics().totalTaskCostMs()).isEqualTo(265);
        assertThat(result.metrics().criticalPathCostMs()).isEqualTo(130);

        // Theoretical maximum speedup: 265 / 130 = 2.038x
        assertThat(result.metrics().theoreticalMaxSpeedup()).isGreaterThan(2.0);

        // Parallel speedup with Virtual Threads must be significantly higher than sequential (> 1.4x)
        assertThat(result.actualSpeedup()).isGreaterThan(1.4);
    }
}
