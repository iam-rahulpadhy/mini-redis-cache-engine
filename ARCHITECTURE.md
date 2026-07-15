# Mini-Redis Architecture & Workflow

This document explains the internal design and data flow of the Mini-Redis Engine. It is designed to handle high-throughput concurrent access while maintaining strict $O(1)$ time complexity for all cache operations and eviction.

## System Workflow

```mermaid
flowchart TB
    %% Client Interactions
    Clients["Client Threads (put, get, remove)"]

    %% Tenant Layer
    Registry["TenantRegistry<br/>(Map of Namespaces)"]

    %% Core Components
    subgraph Engine["Isolated MiniRedisEngine Instance"]
        direction TB
        LockManager["CacheLockManager<br/>(ReentrantReadWriteLock)"]
        
        subgraph DataStructures["Data Structures (Protected by Locks)"]
            direction LR
            NodeMap["ConcurrentHashMap<br/>(O(1) Key Lookup)"]
            LRUList["Doubly-Linked List<br/>(O(1) Eviction)"]
        end
    end
    
    %% Edges
    Clients -- "1. Request Namespace" --> Registry
    Registry -- "2. Returns Cache Instance" --> Engine
    
    Clients -- "3. Cache Operations" --> LockManager
    
    LockManager -- "Read Lock (get)" --> NodeMap
    LockManager -- "Write Lock (put, remove, evict)" --> DataStructures
    
    NodeMap -. "Stores Reference To" .-> LRUList
```

## Component Breakdown

### 1. Multi-Tenant Isolation (`TenantRegistry`)
The system is designed to handle multiple isolated cache namespaces within a single JVM process. The `TenantRegistry` maps string identifiers (e.g., "user-service", "product-data") to independent `MiniRedisEngine` instances. There is zero shared mutable state between different namespaces.
### 2. Concurrency & Locking (`CacheLockManager`)
Instead of locking individual segments, the engine uses a centralized `ReentrantReadWriteLock` with **fairness enabled**.
- **Read Path**: Multiple client threads can acquire the read lock simultaneously to execute `get()` operations. This ensures maximum throughput when cache hit rates are high.
- **Write Path**: Operations that mutate state (`put()`, `remove()`, or LRU reordering on a cache hit) must acquire the exclusive write lock. 

### 3. O(1) Key Lookup (`ConcurrentHashMap`)
The core storage is a standard Java `ConcurrentHashMap`. However, instead of just storing the raw value, the map stores a direct reference to a `DoublyLinkedListNode`. This allows the engine to jump directly to the node in the LRU list in $O(1)$ time without having to traverse the list.

### 4. O(1) LRU Eviction (`Doubly-Linked List`)
A custom-built intrusive doubly-linked list tracks the access order of keys. 
- When a key is accessed, the node is unlinked from its current position and moved to the head (Most Recently Used).
- When the cache reaches capacity, the node at the tail (Least Recently Used) is evicted.
- Because the `ConcurrentHashMap` gives us the exact memory reference to the node, unlinking and relinking takes exactly $O(1)$ time via pointer manipulation.
