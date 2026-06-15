# mini-redis

A multi-tenant, concurrent in-memory caching engine written in pure Java. Designed for scenarios where multiple isolated cache namespaces must coexist within a single JVM process, with strict control over memory usage and deterministic eviction behavior.

---

## Architecture Overview

The engine is composed of three layers:

1. **Tenant Registry** -- maps tenant identifiers to isolated cache instances.
2. **Cache Store** -- a \ConcurrentHashMap\ used as the primary key-value lookup table.
3. **Eviction Layer** -- a custom doubly-linked list that tracks access order independently of the hash map, enabling O(1) LRU eviction.

Each tenant operates on a completely separate cache instance. There is no shared mutable state between tenants.

---

## Concurrency Model

The engine uses \ReentrantReadWriteLock\ at the cache-instance level. Each tenant's cache holds its own lock, which separates read access from write access at the granularity of individual operations.

### Read path

\get\ operations acquire the **read lock** before accessing the map or the linked list. Multiple threads belonging to the same tenant can hold the read lock concurrently, so parallel reads do not block each other.

### Write path

\put\, \evict\, and internal LRU reordering operations acquire the **write lock** exclusively. No other thread -- reader or writer -- can proceed until the write lock is released.

### ABA problem mitigation

In cache engines that perform compare-and-swap (CAS) operations on version counters or node pointers, the ABA problem arises when a value is read as A, changed to B, then changed back to A before a CAS completes -- causing the CAS to succeed incorrectly even though an intermediate mutation occurred.

\ReentrantReadWriteLock\ avoids this entirely. Rather than relying on optimistic lock-free CAS loops, the write lock provides mutual exclusion: between acquiring and releasing the write lock, no other thread can observe or modify the cache state. There is no check-then-act window where an ABA sequence can silently corrupt the outcome. The linked list node pointer updates that occur during LRU reordering are always performed inside the write-locked section, making node pointer changes fully visible and atomic from the perspective of any subsequent lock acquisition.

---

## Memory and Eviction

### Data structure

LRU eviction is implemented using a combination of:

- \ConcurrentHashMap<String, Node>\ -- provides O(1) average-case key lookup and stores a direct reference to the corresponding node in the linked list.
- A hand-written **doubly-linked list** -- maintains insertion and access order. Each node holds \key\, \alue\, \prev\, and ext\ pointers.

The map and the list are kept in sync at all times within the write lock.

### O(1) guarantee

Standard Java collections do not provide a data structure that guarantees O(1) access, O(1) insertion, and O(1) removal simultaneously. \LinkedHashMap\ approximates this but does not expose internal node references, making external manipulation of ordering impossible without full traversal.

The custom doubly-linked list solves this by storing the node reference directly in the map value. When a key is accessed or updated:

1. The map lookup retrieves the node reference in O(1).
2. The node is unlinked from its current position using its \prev\ and ext\ pointers in O(1).
3. The node is relinked at the head of the list in O(1).

Eviction targets the node at the tail of the list, which is always the least recently used entry. The tail node's key is used to remove it from the map in O(1), and the tail pointer is updated in O(1).

No traversal of the list occurs at any point during normal cache operation.

### Capacity enforcement

Each cache instance is initialized with a fixed integer capacity. On every \put\ operation, if the cache is at capacity, the tail node is evicted before the new entry is inserted. The capacity ceiling is enforced inside the write lock, so concurrent writers cannot bypass it.

---

## Local Setup

### Prerequisites

- JDK 17 or higher
- Apache Maven 3.8+

### Build

\\ash
mvn clean compile
\
### Run tests

\\ash
mvn test
\
### Package

\\ash
mvn clean package
\
The resulting JAR will be placed in \	arget/\.

### Run

\\ash
java -jar target/mini-redis-<version>.jar
\
---

## Project Structure

\mini-redis/
├── src/
│   ├── main/
│   │   └── java/
│   │       └── com/miniredis/
│   │           ├── CacheEngine.java        # Tenant registry and lifecycle management
│   │           ├── CacheInstance.java      # Per-tenant cache with lock and eviction logic
│   │           ├── LRULinkedList.java      # Doubly-linked list implementation
│   │           └── Node.java              # Linked list node definition
│   └── test/
│       └── java/
│           └── com/miniredis/
│               ├── CacheEngineTest.java
│               └── ConcurrencyTest.java
└── pom.xml
\
---

## Limitations

- All data is held in heap memory. There is no persistence layer and no write-ahead log.
- Eviction is strictly LRU. No TTL-based expiry is implemented in the current version.
- The engine does not support distributed deployments. It is a single-process, single-JVM store.
- No serialization protocol is exposed. Integration requires direct JVM method calls.
