package com.backend.discrete.week04_posets;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe representation of a finite Strict Partially Ordered Set P = (X, &prec;).
 * <p>
 * In systems architecture (e.g. Apache Airflow, Spring IoC, Gradle), vertices represent
 * runnable tasks or bean definitions, and directed edges represent strict prerequisite
 * dependencies: {@code u &prec; v} denotes that task {@code u} must complete before task
 * {@code v} can be scheduled.
 * </p>
 */
public class PosetGraph {

    private final Map<TaskId, Task> taskRegistry = new ConcurrentHashMap<>();
    private final Map<TaskId, Set<TaskId>> outgoing = new ConcurrentHashMap<>(); // u -> set of v where u < v
    private final Map<TaskId, Set<TaskId>> incoming = new ConcurrentHashMap<>(); // v -> set of u where u < v

    public PosetGraph() {}

    /**
     * Registers a new task into the poset vertex set.
     */
    public synchronized PosetGraph addTask(Task task) {
        Objects.requireNonNull(task, "Task cannot be null");
        taskRegistry.put(task.id(), task);
        outgoing.computeIfAbsent(task.id(), k -> ConcurrentHashMap.newKeySet());
        incoming.computeIfAbsent(task.id(), k -> ConcurrentHashMap.newKeySet());
        return this;
    }

    /**
     * Adds a directed dependency edge representing the strict partial order relation:
     * {@code prerequisite &prec; dependent}.
     *
     * @param prerequisite task that must complete first
     * @param dependent    task that waits for prerequisite
     * @return this graph builder instance
     */
    public synchronized PosetGraph addDependency(TaskId prerequisite, TaskId dependent) {
        Objects.requireNonNull(prerequisite, "Prerequisite TaskId cannot be null");
        Objects.requireNonNull(dependent, "Dependent TaskId cannot be null");

        if (prerequisite.equals(dependent)) {
            throw new IllegalArgumentException("Strict partial order is irreflexive: task cannot depend on itself: " + prerequisite);
        }

        if (!taskRegistry.containsKey(prerequisite)) {
            addTask(Task.of(prerequisite.value(), 0));
        }
        if (!taskRegistry.containsKey(dependent)) {
            addTask(Task.of(dependent.value(), 0));
        }

        outgoing.get(prerequisite).add(dependent);
        incoming.get(dependent).add(prerequisite);
        return this;
    }

    public synchronized PosetGraph addDependency(String prerequisite, String dependent) {
        return addDependency(TaskId.of(prerequisite), TaskId.of(dependent));
    }

    public Task getTask(TaskId id) {
        return taskRegistry.get(id);
    }

    public Set<TaskId> getAllTaskIds() {
        return Collections.unmodifiableSet(taskRegistry.keySet());
    }

    public Collection<Task> getAllTasks() {
        return Collections.unmodifiableCollection(taskRegistry.values());
    }

    public Set<TaskId> getDirectDependents(TaskId id) {
        Set<TaskId> deps = outgoing.get(id);
        return deps == null ? Collections.emptySet() : Collections.unmodifiableSet(deps);
    }

    public Set<TaskId> getDirectPrerequisites(TaskId id) {
        Set<TaskId> prereqs = incoming.get(id);
        return prereqs == null ? Collections.emptySet() : Collections.unmodifiableSet(prereqs);
    }

    public int inDegree(TaskId id) {
        Set<TaskId> prereqs = incoming.get(id);
        return prereqs == null ? 0 : prereqs.size();
    }

    public int outDegree(TaskId id) {
        Set<TaskId> deps = outgoing.get(id);
        return deps == null ? 0 : deps.size();
    }

    public int vertexCount() {
        return taskRegistry.size();
    }

    public int edgeCount() {
        return outgoing.values().stream().mapToInt(Set::size).sum();
    }

    /**
     * Checks if there exists a directed path from {@code start} to {@code target}
     * in the dependency graph (transitive reachability: start &prec;* target).
     */
    public boolean isReachable(TaskId start, TaskId target) {
        if (start.equals(target)) {
            return true;
        }
        Set<TaskId> visited = new HashSet<>();
        Queue<TaskId> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            TaskId curr = queue.poll();
            for (TaskId neighbor : getDirectDependents(curr)) {
                if (neighbor.equals(target)) {
                    return true;
                }
                if (visited.add(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }
        return false;
    }

    /**
     * Validates that the poset is strictly acyclic (a valid DAG).
     *
     * @throws CircularDependencyException if any cycle is detected
     */
    public void validateAcyclic() {
        Optional<List<TaskId>> cycleOpt = findCycle();
        if (cycleOpt.isPresent()) {
            throw new CircularDependencyException("Cycle detected in task poset", cycleOpt.get());
        }
    }

    public boolean hasCycle() {
        return findCycle().isPresent();
    }

    /**
     * Finds and returns a directed cycle in the graph if one exists, using DFS 3-coloring.
     */
    public Optional<List<TaskId>> findCycle() {
        Map<TaskId, Integer> color = new HashMap<>(); // 0: unvisited, 1: visiting, 2: visited
        Map<TaskId, TaskId> parent = new HashMap<>();

        for (TaskId node : taskRegistry.keySet()) {
            color.put(node, 0);
        }

        for (TaskId node : taskRegistry.keySet()) {
            if (color.get(node) == 0) {
                List<TaskId> cycle = dfsCycle(node, color, parent);
                if (cycle != null) {
                    return Optional.of(cycle);
                }
            }
        }
        return Optional.empty();
    }

    private List<TaskId> dfsCycle(TaskId u, Map<TaskId, Integer> color, Map<TaskId, TaskId> parent) {
        color.put(u, 1);

        for (TaskId v : getDirectDependents(u)) {
            if (color.get(v) == 1) {
                // Cycle found: trace back from u to v
                List<TaskId> cycle = new ArrayList<>();
                cycle.add(v);
                TaskId curr = u;
                while (!curr.equals(v)) {
                    cycle.add(curr);
                    curr = parent.get(curr);
                }
                cycle.add(v);
                Collections.reverse(cycle);
                return cycle;
            }
            if (color.get(v) == 0) {
                parent.put(v, u);
                List<TaskId> cycle = dfsCycle(v, color, parent);
                if (cycle != null) {
                    return cycle;
                }
            }
        }

        color.put(u, 2);
        return null;
    }

    /**
     * Computes the Transitive Reduction (Hasse Diagram representation) of this poset.
     * <p>
     * An edge (u, w) is redundant if there exists an alternative path from u to w of length &ge; 2.
     * In build systems and task orchestrators, pruning redundant edges eliminates redundant barrier checks
     * and simplifies dependency graphs without altering scheduling semantics.
     * </p>
     *
     * @return a new PosetGraph containing only the irreducible covering relations
     */
    public PosetGraph computeTransitiveReduction() {
        validateAcyclic();

        PosetGraph reduction = new PosetGraph();
        for (Task task : taskRegistry.values()) {
            reduction.addTask(task);
        }

        // For every edge (u, v), check if there is an alternate path from u to v via another neighbor
        for (TaskId u : taskRegistry.keySet()) {
            for (TaskId v : getDirectDependents(u)) {
                boolean redundant = false;
                for (TaskId intermediate : getDirectDependents(u)) {
                    if (!intermediate.equals(v) && isReachable(intermediate, v)) {
                        redundant = true;
                        break;
                    }
                }
                if (!redundant) {
                    reduction.addDependency(u, v);
                }
            }
        }

        return reduction;
    }
}
