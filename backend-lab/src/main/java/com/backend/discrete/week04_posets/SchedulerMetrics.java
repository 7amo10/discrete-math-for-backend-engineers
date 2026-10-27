package com.backend.discrete.week04_posets;

/**
 * Execution metrics capturing performance and concurrency speedup of a scheduled poset workflow.
 *
 * @param totalWallClockMs       actual physical wall-clock duration of parallel execution
 * @param totalTaskCostMs        sum of individual task estimated durations (sequential baseline)
 * @param criticalPathCostMs     theoretical minimum possible duration (length of longest chain)
 * @param waveCount              number of parallel antichain waves executed
 * @param totalTasks             total number of tasks completed
 * @param speedupFactor          actual speedup achieved: totalTaskCostMs / totalWallClockMs
 * @param theoreticalMaxSpeedup  theoretical upper bound on speedup: totalTaskCostMs / criticalPathCostMs
 * @param schedulingEfficiency   ratio of actual speedup to theoretical max: speedupFactor / theoreticalMaxSpeedup
 */
public record SchedulerMetrics(
        long totalWallClockMs,
        long totalTaskCostMs,
        long criticalPathCostMs,
        int waveCount,
        int totalTasks,
        double speedupFactor,
        double theoreticalMaxSpeedup,
        double schedulingEfficiency
) {
    public static SchedulerMetrics of(
            long totalWallClockMs,
            long totalTaskCostMs,
            long criticalPathCostMs,
            int waveCount,
            int totalTasks
    ) {
        long effectiveWallClock = Math.max(1, totalWallClockMs);
        long effectiveCriticalPath = Math.max(1, criticalPathCostMs);

        double speedup = (double) totalTaskCostMs / effectiveWallClock;
        double maxSpeedup = (double) totalTaskCostMs / effectiveCriticalPath;
        double efficiency = maxSpeedup > 0 ? Math.min(1.0, speedup / maxSpeedup) : 1.0;

        return new SchedulerMetrics(
                totalWallClockMs,
                totalTaskCostMs,
                criticalPathCostMs,
                waveCount,
                totalTasks,
                speedup,
                maxSpeedup,
                efficiency
        );
    }
}
