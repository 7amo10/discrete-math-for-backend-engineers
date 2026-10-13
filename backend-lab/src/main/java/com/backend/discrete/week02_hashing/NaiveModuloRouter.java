package com.backend.discrete.week02_hashing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Sharding router using direct modulo key placement: index = hash(key) % N.
 */
public final class NaiveModuloRouter {

    private final List<ServerNode> nodes;
    private final HashFunction hashFunction;

    public NaiveModuloRouter() {
        this(new HashFunction());
    }

    public NaiveModuloRouter(HashFunction hashFunction) {
        this.nodes = new CopyOnWriteArrayList<>();
        this.hashFunction = Objects.requireNonNull(hashFunction, "hashFunction must not be null");
    }

    public void addNode(ServerNode node) {
        Objects.requireNonNull(node, "node must not be null");
        if (!nodes.contains(node)) {
            nodes.add(node);
        }
    }

    public boolean removeNode(ServerNode node) {
        return nodes.remove(node);
    }

    public int getNodeCount() {
        return nodes.size();
    }

    public List<ServerNode> getNodes() {
        return Collections.unmodifiableList(new ArrayList<>(nodes));
    }

    /**
     * Routes a key to a physical node using hash(key) % N.
     */
    public ServerNode route(String key) {
        if (nodes.isEmpty()) {
            throw new IllegalStateException("No available nodes in router");
        }
        long hashVal = hashFunction.hash(key);
        int index = (int) (hashVal % nodes.size());
        return nodes.get(index);
    }
}
