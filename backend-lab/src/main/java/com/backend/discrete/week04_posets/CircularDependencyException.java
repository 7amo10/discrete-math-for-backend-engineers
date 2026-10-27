package com.backend.discrete.week04_posets;

import java.util.List;

/**
 * Exception thrown when a dependency graph contains one or more directed cycles,
 * violating the strict partial order axioms (irreflexivity and asymmetry).
 */
public class CircularDependencyException extends RuntimeException {

    private final List<TaskId> cycle;

    public CircularDependencyException(String message, List<TaskId> cycle) {
        super(message);
        this.cycle = cycle == null ? List.of() : List.copyOf(cycle);
    }

    public CircularDependencyException(List<TaskId> cycle) {
        this("Cyclic dependency detected in task poset: " + cycle, cycle);
    }

    public List<TaskId> getCycle() {
        return cycle;
    }
}
