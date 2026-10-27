package com.backend.discrete.week04_posets;

import java.util.Objects;

/**
 * Represents a discrete task within a partially ordered workflow.
 *
 * @param id              unique identifier
 * @param name            human-readable task description
 * @param estimatedCostMs estimated execution time or computational cost in milliseconds
 * @param action          executable workload payload
 */
public record Task(
        TaskId id,
        String name,
        long estimatedCostMs,
        Runnable action
) {
    public Task {
        Objects.requireNonNull(id, "TaskId cannot be null");
        Objects.requireNonNull(name, "Task name cannot be null");
        if (estimatedCostMs < 0) {
            throw new IllegalArgumentException("Estimated cost cannot be negative: " + estimatedCostMs);
        }
        if (action == null) {
            action = () -> {};
        }
    }

    public static Task of(String id, String name, long estimatedCostMs, Runnable action) {
        return new Task(TaskId.of(id), name, estimatedCostMs, action);
    }

    public static Task of(String id, String name, long estimatedCostMs) {
        return new Task(TaskId.of(id), name, estimatedCostMs, () -> {});
    }

    public static Task of(String id, long estimatedCostMs) {
        return new Task(TaskId.of(id), id, estimatedCostMs, () -> {});
    }

    public void execute() {
        action.run();
    }
}
