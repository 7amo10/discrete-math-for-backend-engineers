package com.backend.discrete.week04_posets;

import java.util.Objects;

/**
 * Strongly typed identifier for a task in a partially ordered set (poset).
 *
 * @param value non-null, non-blank unique task identifier
 */
public record TaskId(String value) implements Comparable<TaskId> {

    public TaskId {
        Objects.requireNonNull(value, "TaskId value cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("TaskId value cannot be blank");
        }
    }

    public static TaskId of(String value) {
        return new TaskId(value);
    }

    @Override
    public int compareTo(TaskId other) {
        return this.value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
