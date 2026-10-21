package com.backend.discrete.week03_graphs;

import java.util.Objects;

/**
 * Identifier for an acquireable database resource (e.g. a table row or shard key).
 *
 * @param name the resource key (e.g. "accounts:row:42")
 */
public record ResourceId(String name) implements Comparable<ResourceId> {

    public ResourceId {
        Objects.requireNonNull(name, "ResourceId name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("ResourceId name must not be blank");
        }
    }

    public static ResourceId of(String name) {
        return new ResourceId(name);
    }

    @Override
    public int compareTo(ResourceId other) {
        return this.name.compareTo(other.name);
    }

    @Override
    public String toString() {
        return name;
    }
}
