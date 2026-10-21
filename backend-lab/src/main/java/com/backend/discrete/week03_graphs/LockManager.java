package com.backend.discrete.week03_graphs;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

/**
 * Thread-safe transactional lock manager with integrated Wait-For Graph (WFG) tracking,
 * linear-time Tarjan cycle detection, and minimal-cost victim selection.
 */
public class LockManager {

    private final Map<ResourceId, TransactionId> lockHolders = new HashMap<>();
    private final Map<TransactionId, Set<ResourceId>> heldLocks = new HashMap<>();
    private final Map<TransactionId, ResourceId> waitingForResource = new HashMap<>();
    private final Map<TransactionId, CompletableFuture<Void>> waitFutures = new HashMap<>();
    private final Map<TransactionId, TransactionMetadata> metadataMap = new ConcurrentHashMap<>();
    private final Map<ResourceId, Queue<TransactionId>> resourceWaiters = new HashMap<>();

    private final WaitForGraph wfg = new WaitForGraph();
    private final DeadlockDetector detector;
    private final VictimSelector victimSelector;

    private int deadlocksDetected = 0;
    private int deadlocksResolved = 0;

    public LockManager() {
        this(new TarjanDeadlockDetector(), new MinimumCostVictimSelector());
    }

    public LockManager(DeadlockDetector detector, VictimSelector victimSelector) {
        this.detector = Objects.requireNonNull(detector, "Detector cannot be null");
        this.victimSelector = Objects.requireNonNull(victimSelector, "VictimSelector cannot be null");
    }

    public synchronized void registerTransaction(TransactionMetadata metadata) {
        Objects.requireNonNull(metadata, "Metadata cannot be null");
        metadataMap.put(metadata.id(), metadata);
        wfg.addVertex(metadata.id());
        heldLocks.put(metadata.id(), new HashSet<>());
    }

    public synchronized void incrementRowsModified(TransactionId tx, int count) {
        TransactionMetadata meta = metadataMap.get(tx);
        if (meta != null) {
            metadataMap.put(tx, new TransactionMetadata(
                    meta.id(),
                    meta.startedAtEpochMs(),
                    meta.rowsModified() + count,
                    meta.priority()
            ));
        }
    }

    /**
     * Attempts to acquire an exclusive lock on the target resource.
     * If the resource is held by another transaction, registers dependency in the WFG,
     * triggers deadlock detection, and blocks until the lock is granted or aborted.
     */
    public void acquire(TransactionId tx, ResourceId resource) {
        CompletableFuture<Void> futureToWait = null;

        synchronized (this) {
            ensureRegistered(tx);

            TransactionId currentHolder = lockHolders.get(resource);

            // Case 1: Resource is free
            if (currentHolder == null) {
                grantLock(tx, resource);
                return;
            }

            // Case 2: Already held by current transaction (reentrant lock)
            if (currentHolder.equals(tx)) {
                return;
            }

            // Case 3: Contended lock - register dependency in Wait-For Graph
            wfg.addEdge(tx, currentHolder);
            waitingForResource.put(tx, resource);
            resourceWaiters.computeIfAbsent(resource, k -> new LinkedList<>()).add(tx);

            // Run cycle detection on the updated graph topology
            List<DeadlockCycle> cycles = detector.detectCycles(wfg);
            if (!cycles.isEmpty()) {
                deadlocksDetected++;
                resolveDeadlock(cycles.getFirst(), tx);
            }

            // If this transaction wasn't aborted as victim, prepare to await lock grant
            if (lockHolders.get(resource) == null || lockHolders.get(resource).equals(tx)) {
                // Resolved in our favor immediately
                grantLock(tx, resource);
                return;
            }

            CompletableFuture<Void> future = new CompletableFuture<>();
            waitFutures.put(tx, future);
            futureToWait = future;
        }

        // Wait outside synchronization lock
        try {
            futureToWait.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted while waiting for lock: " + resource, e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof DeadlockException de) {
                throw de;
            }
            throw new RuntimeException("Failed while waiting for lock", e.getCause());
        }
    }

    /**
     * Resolves a detected deadlock cycle by selecting a victim transaction and aborting it.
     */
    private synchronized void resolveDeadlock(DeadlockCycle cycle, TransactionId callerTx) {
        TransactionId victim = victimSelector.selectVictim(cycle, metadataMap);
        deadlocksResolved++;

        if (victim.equals(callerTx)) {
            // Caller itself is the victim
            cleanUpTransaction(victim);
            throw new DeadlockException(victim, cycle);
        } else {
            // Another transaction in the cycle is the victim
            CompletableFuture<Void> victimFuture = waitFutures.remove(victim);
            cleanUpTransaction(victim);
            if (victimFuture != null) {
                victimFuture.completeExceptionally(new DeadlockException(victim, cycle));
            }
        }
    }

    private synchronized void grantLock(TransactionId tx, ResourceId resource) {
        lockHolders.put(resource, tx);
        heldLocks.computeIfAbsent(tx, k -> new HashSet<>()).add(resource);
        waitingForResource.remove(tx);
        Queue<TransactionId> waiters = resourceWaiters.get(resource);
        if (waiters != null) {
            waiters.remove(tx);
        }
    }

    public synchronized void release(TransactionId tx, ResourceId resource) {
        TransactionId holder = lockHolders.get(resource);
        if (!Objects.equals(holder, tx)) {
            return;
        }

        lockHolders.remove(resource);
        Set<ResourceId> txHeld = heldLocks.get(tx);
        if (txHeld != null) {
            txHeld.remove(resource);
        }

        // Hand over to the next waiter in line
        Queue<TransactionId> waiters = resourceWaiters.get(resource);
        if (waiters != null && !waiters.isEmpty()) {
            TransactionId nextWaiter = waiters.poll();
            wfg.removeEdge(nextWaiter, tx);
            grantLock(nextWaiter, resource);

            CompletableFuture<Void> future = waitFutures.remove(nextWaiter);
            if (future != null) {
                future.complete(null);
            }
        }
    }

    public synchronized void releaseAll(TransactionId tx) {
        Set<ResourceId> resources = heldLocks.remove(tx);
        if (resources != null) {
            List<ResourceId> toRelease = new ArrayList<>(resources);
            for (ResourceId res : toRelease) {
                release(tx, res);
            }
        }
        cleanUpTransaction(tx);
    }

    private synchronized void cleanUpTransaction(TransactionId tx) {
        ResourceId waitingOn = waitingForResource.remove(tx);
        if (waitingOn != null) {
            Queue<TransactionId> waiters = resourceWaiters.get(waitingOn);
            if (waiters != null) {
                waiters.remove(tx);
            }
        }

        // Release all locks held by this transaction
        Set<ResourceId> resources = heldLocks.remove(tx);
        if (resources != null) {
            for (ResourceId res : resources) {
                lockHolders.remove(res);
                Queue<TransactionId> waiters = resourceWaiters.get(res);
                if (waiters != null && !waiters.isEmpty()) {
                    TransactionId next = waiters.poll();
                    wfg.removeEdge(next, tx);
                    grantLock(next, res);
                    CompletableFuture<Void> nextFuture = waitFutures.remove(next);
                    if (nextFuture != null) {
                        nextFuture.complete(null);
                    }
                }
            }
        }

        wfg.removeVertex(tx);
    }

    private void ensureRegistered(TransactionId tx) {
        if (!metadataMap.containsKey(tx)) {
            registerTransaction(new TransactionMetadata(tx, System.currentTimeMillis(), 0, 1));
        }
    }

    public synchronized WaitForGraph getGraph() {
        return wfg.snapshot();
    }

    public synchronized int getDeadlocksDetected() {
        return deadlocksDetected;
    }

    public synchronized int getDeadlocksResolved() {
        return deadlocksResolved;
    }

    public synchronized boolean isHeldBy(ResourceId resource, TransactionId tx) {
        return Objects.equals(lockHolders.get(resource), tx);
    }

    public synchronized TransactionMetadata getMetadata(TransactionId tx) {
        return metadataMap.get(tx);
    }
}
