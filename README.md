# Discrete Mathematics for Backend Engineers

A hands-on engineering lab and editorial field guide bridging formal discrete mathematics directly to production Java architectures, distributed systems, and open-source engines.

Curriculum and foundations based on **MIT 6.042J: Mathematics for Computer Science (Fall 2010)**.

---

## The Core Concept

High-scale backend infrastructure relies on mathematical guarantees rather than hope:
* **State Machine Invariants** ensure transactional ledgers never leak funds during network failures.
* **Modular Arithmetic & Pigeonhole Bounds** dictate partition key distribution and consistent hash rings.
* **Graph Theory** resolves transactional deadlocks in database storage engines via Wait-For Graphs.
* **Partially Ordered Sets (Posets)** power DAG task execution engines and circular dependency detection.

This repository pairs formal proofs and visual intuition with runnable **Java 21** implementations and **JUnit 5** verification suites.

---

## 4-Week Discrete Curriculum

| Week | Mathematical Topic | Systems Problem Space | Java Module |
| :---: | :--- | :--- | :--- |
| **01** | **Propositions & State Invariants** | Double-Entry Ledgers & Saga Rollbacks | [`week01_invariants`](backend-lab/src/main/java/com/backend/discrete/week01_invariants) |
| **02** | **Number Theory & Modular Arithmetic** | Consistent Hash Rings & Virtual Node Balancing | [`week02_hashing`](backend-lab/src/main/java/com/backend/discrete/week02_hashing) |
| **03** | **Graph Theory & Network Routing** | Wait-For Graph (WFG) Deadlock Cycle Detection | [`week03_graphs`](backend-lab/src/main/java/com/backend/discrete/week03_graphs) |
| **04** | **Relations, Posets & Task Scheduling** | Workflow DAG Orchestration & Antichain Concurrency | [`week04_posets`](backend-lab/src/main/java/com/backend/discrete/week04_posets) |

---

## Getting Started

### Prerequisites
* **Java 21 LTS**
* **Maven 3.8+**
* **Node.js 18+** (for the documentation site)

### Running Java Verification Suites
Execute the entire test lab or target a specific week:
```bash
cd backend-lab

# Run all verification suites
mvn clean test

# Run a specific week's challenge (e.g. Week 1)
mvn test -Dtest=AccountTransferInvariantTest
```

### Running the Documentation Site
The static documentation site is powered by **Astro Starlight** with native KaTeX ($\LaTeX$) math rendering and hand-drawn architecture diagrams:
```bash
cd site
npm install
npm run dev
```
Open [http://localhost:4321](http://localhost:4321) in your browser.

---

## License
MIT
