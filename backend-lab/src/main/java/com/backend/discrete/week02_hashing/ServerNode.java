package com.backend.discrete.week02_hashing;

import java.util.Objects;

/**
 * Represents a physical server, database instance, or Kafka broker node in a cluster.
 */
public record ServerNode(String id, String host, int port) implements Comparable<ServerNode> {

    public ServerNode {
        Objects.requireNonNull(id, "Server node id must not be null");
        Objects.requireNonNull(host, "Server node host must not be null");
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535: " + port);
        }
    }

    public static ServerNode of(String id) {
        return new ServerNode(id, "127.0.0.1", 8080);
    }

    public static ServerNode of(String id, String host, int port) {
        return new ServerNode(id, host, port);
    }

    @Override
    public int compareTo(ServerNode other) {
        return this.id.compareTo(other.id);
    }

    @Override
    public String toString() {
        return id + "(" + host + ":" + port + ")";
    }
}
