package com.backend.discrete.week04_posets;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Enterprise ETL & Microservice DAG Benchmark Simulator.
 * Models a production data pipeline with parallel extraction, transformation, validation, and loading stages.
 */
public class WorkflowSimulator {

    public record BenchmarkResult(
            SchedulerMetrics metrics,
            int executedTaskCount,
            int criticalPathHopCount,
            int maxAntichainWidth,
            double actualSpeedup
    ) {}

    /**
     * Constructs and executes a benchmark ETL pipeline demonstrating antichain speedup.
     * <pre>
     *                    [ExtractCustomers (40ms)]  [ExtractOrders (40ms)]  [ExtractInventory (40ms)]
     *                               \                      /                       /
     *                                [TransformSales (30ms)]          [TransformStock (30ms)]
     *                                           \                       /
     *                                            [AggregateAnalytics (20ms)]
     *                                           /                         \
     *                            [GenerateInvoices (25ms)]       [UpdateSearchIndex (25ms)]
     *                                           \                         /
     *                                            [DispatchNotifications (15ms)]
     * </pre>
     */
    public BenchmarkResult runPipelineBenchmark() {
        PosetGraph graph = new PosetGraph();
        AtomicInteger counter = new AtomicInteger(0);

        // Stage 1 (Wave 1): Parallel Extractions (3 independent tasks)
        graph.addTask(Task.of("extract_cust", "Extract Customer Records", 40, counter::incrementAndGet));
        graph.addTask(Task.of("extract_orders", "Extract Daily Orders", 40, counter::incrementAndGet));
        graph.addTask(Task.of("extract_inv", "Extract Warehouse Inventory", 40, counter::incrementAndGet));

        // Stage 2 (Wave 2): Parallel Transformations (2 independent tasks)
        graph.addTask(Task.of("transform_sales", "Transform Sales Data", 30, counter::incrementAndGet));
        graph.addTask(Task.of("transform_stock", "Transform Stock Balances", 30, counter::incrementAndGet));

        // Stage 3 (Wave 3): Aggregation (1 bottleneck task)
        graph.addTask(Task.of("agg_analytics", "Aggregate Executive Analytics", 20, counter::incrementAndGet));

        // Stage 4 (Wave 4): Parallel Output Generation (2 independent tasks)
        graph.addTask(Task.of("gen_invoices", "Generate Customer Invoices", 25, counter::incrementAndGet));
        graph.addTask(Task.of("index_search", "Update Elastic Search Index", 25, counter::incrementAndGet));

        // Stage 5 (Wave 5): Dispatch & Notification (1 closing task)
        graph.addTask(Task.of("dispatch_notif", "Dispatch Webhook Notifications", 15, counter::incrementAndGet));

        // Dependencies (Partial Order Relations)
        // Stage 1 -> Stage 2
        graph.addDependency("extract_cust", "transform_sales");
        graph.addDependency("extract_orders", "transform_sales");
        graph.addDependency("extract_inv", "transform_stock");

        // Stage 2 -> Stage 3
        graph.addDependency("transform_sales", "agg_analytics");
        graph.addDependency("transform_stock", "agg_analytics");

        // Stage 3 -> Stage 4
        graph.addDependency("agg_analytics", "gen_invoices");
        graph.addDependency("agg_analytics", "index_search");

        // Stage 4 -> Stage 5
        graph.addDependency("gen_invoices", "dispatch_notif");
        graph.addDependency("index_search", "dispatch_notif");

        AntichainWaveScheduler scheduler = new AntichainWaveScheduler();
        CriticalPathAnalyzer analyzer = new CriticalPathAnalyzer();

        var waves = scheduler.computeAntichainWaves(graph);
        var criticalPath = analyzer.computeCriticalPath(graph);
        int maxWidth = analyzer.computeMaxAntichainWidth(waves);

        // Execute using Virtual Threads
        SchedulerMetrics metrics = scheduler.execute(graph, true);

        return new BenchmarkResult(
                metrics,
                counter.get(),
                criticalPath.path().size(),
                maxWidth,
                metrics.speedupFactor()
        );
    }
}
