# Week 2: Number Theory, Modular Arithmetic & Consistent Hashing

Implements consistent hash ring partitioning, modular residue calculations, and virtual node distribution modeling distributed systems (e.g. Apache Cassandra, Amazon DynamoDB).

---

## 1. Mathematical Model & Invariants

### 1.1 Residue Ring Modulo $2^{32}$
Hash outputs map to the cyclic ring $\mathbb{Z}_{2^{32}} = \{0, 1, 2, \ldots, 2^{32} - 1\}$:
$$h(\text{key}) \pmod{2^{32}}$$
For tokens $h_1, h_2$, clockwise ring distance is:
$$\text{dist}(h_1, h_2) = (h_2 - h_1) \pmod{2^{32}}$$

### 1.2 Multiplicative Inverses & Coprimality
Multipliers $c_1, c_2$ in linear congruential and mixing routines require coprimality with the modulus:
$$\gcd(c_1, 2^{32}) = 1$$
Using the extended Euclidean algorithm (the Pulverizer):
$$s \cdot c_1 + t \cdot 2^{32} = \gcd(c_1, 2^{32}) = 1 \implies s \cdot c_1 \equiv 1 \pmod{2^{32}}$$
Multipliers sharing factors with $2^{32}$ collapse residue classes into a proper subgroup of size $2^{32-k}$, degrading distribution entropy.

### 1.3 Key Resharding Under Direct Modulo
Under direct modulo sharding:
$$\text{node}(k) = h(k) \pmod N$$
When scaling from $N$ to $N + 1$ nodes, consecutive integers are coprime ($\gcd(N, N + 1) = 1$):
$$\mathbb{E}[\text{Migrated Keys}] = \frac{N}{N + 1} \approx 90.9\% \quad (\text{for } N = 10)$$

### 1.4 Consistent Hashing Migration Bounds
In a consistent hash ring on $[0, 2^{32}-1]$:
* Nodes own the clockwise arc between their token and their predecessor.
* Adding a node $S_{N+1}$ splits only the arc of its clockwise successor.
* The expected number of displaced keys is bounded:
$$\mathbb{E}[\text{Migrated Keys}] = \frac{K}{N + 1} \approx 9.1\% \quad (\text{for } N = 10)$$
The remaining $\frac{N}{N + 1}$ keys remain on their existing hosts.

### 1.5 Virtual Node Distribution
Placing $N$ physical nodes randomly on a continuous circle introduces variance in arc lengths. By allocating $V$ virtual nodes per physical host, the circle is partitioned into $N \cdot V$ smaller segments. The coefficient of variation ($CV = \sigma / \mu$) scales as:
$$CV \propto \mathcal{O}\left(\frac{1}{\sqrt{V}}\right)$$
With $V = 150$, load variance across hosts decreases significantly.

---

## 2. Lab Implementation Overview

* **`NumberTheory.java`:** Euclidean GCD, extended Euclidean algorithm (Bézout coefficients), modular multiplicative inverse, and ring distance.
* **`HashFunction.java`:** 32-bit MurmurHash3 algorithm projecting keys onto the unsigned $[0, 2^{32}-1]$ integer circle.
* **`ServerNode.java`:** Record modeling physical servers and replica endpoints.
* **`NaiveModuloRouter.java`:** Baseline sharding router for migration rate comparison.
* **`ConsistentHashRing.java`:** Thread-safe consistent hash ring built on `ConcurrentSkipListMap` supporting virtual nodes and replica deduplication.
* **`PartitionMigrationSimulator.java`:** Utility measuring migration ratios and load distribution metrics.
* **`ConsistentHashRingTest.java`:** JUnit 5 test suite verifying migration bounds, load balancing, and concurrency using Java 21 virtual threads.

---

## 3. Running the Verification Suite

```bash
mvn test -Dtest=ConsistentHashRingTest
```
