# Week 03: Graph Topologies & Deadlock Resolution (Java Lab)

## Overview
This module models relational database lock dependency topologies using **Directed Graphs** from **MIT 6.042J Chapters 5 & 6**. It provides an executable simulation of PostgreSQL and MySQL InnoDB's Wait-For Graph (WFG) engine, resolving transactional deadlocks in $\mathcal{O}(V + E)$ time using Robert Tarjan's Strongly Connected Components algorithm with minimal-cost victim selection.

---

## Architectural Components

1. **`TransactionId` & `ResourceId`**: Value records identifying concurrent transactional workers and granular locked resources (e.g. database rows).
2. **`WaitForGraph`**: Directed graph representation $G = (V, E)$ where vertices are active transactions and edges $(u \to v)$ represent transaction $u$ blocked waiting for a lock held by transaction $v$.
3. **`TarjanDeadlockDetector`**: Implementation of Tarjan's $\mathcal{O}(V + E)$ SCC algorithm, traversing discovery indices (`dfn`) and lowest reachable ancestors (`low`) on a DFS stack to identify directed cycles.
4. **`DfsCycleDetector`**: Classical 3-color (WHITE, GRAY, BLACK) depth-first cycle detector providing a reference implementation.
5. **`VictimSelector` & `MinimumCostVictimSelector`**: Cost-weighted victim selection heuristic choosing the transaction that minimizes discarded computational work ($\min_{T \in C} \text{Cost}(T)$).
6. **`LockManager`**: Thread-safe transactional lock manager coordinating exclusive lock acquisitions, dynamic WFG dependency updates, and automated cycle resolution.
7. **`DeadlockResolutionSimulator`**: Benchmarking simulator evaluating work preservation metrics across high-concurrency lock contention workloads.

---

## Running the Verification Suite

Execute the JUnit 5 test suite via modern Maven:

```bash
mvn test -Dtest=DeadlockResolutionTest
```
