# Mini-Redis Cache Engine

A pure Java, zero-dependency in-memory caching engine. Designed for high concurrency with strict $O(1)$ LRU eviction and background TTL expiration.

## Features

- **Pure Java**: No Spring, no Maven, no external dependencies.
- **Thread-Safe**: Uses `ReentrantReadWriteLock` for high-throughput concurrent reads and safe writes.
- **O(1) LRU Eviction**: Custom doubly-linked list and `ConcurrentHashMap` guarantee constant time operations.
- **Multi-Tenant Isolation**: Features a `TenantRegistry` that manages completely isolated cache instances (namespaces) inside a single JVM.
- **Lazy TTL**: Keys expire lazily on access, naturally preventing memory leaks without the overhead of background threads.

## Architecture & Workflow

For a complete breakdown of the internal data structures, lock management, and a visual flowchart of the system, please see [ARCHITECTURE.md](ARCHITECTURE.md).


## Project Structure

```text
src/main/java/com/cache/miniredis/
├── concurrency/
│   └── CacheLockManager.java      # Centralized read/write lock
├── core/
    ├── TenantRegistry.java        # Multi-tenant namespace isolation
│   ├── CacheManager.java          # Core interface
│   └── MiniRedisEngine.java       # Primary cache implementation
├── eviction/
│   ├── DoublyLinkedListNode.java  # Node for LRU tracking
│   └── LRUEvictionStrategy.java   # O(1) list manipulation
├── MiniRedisApplication.java      # Entry point
└── TestEngine.java                # Multi-threaded test harness
```

## Building and Testing

Because this project is built entirely without build tools like Maven or Gradle, you can compile and run the comprehensive native test suite directly via the `javac` and `java` CLI.

```bash
# 1. Compile all source files
find src/main/java -name "*.java" | xargs javac -d out/

# 2. Run the TestEngine (Spawns 50 threads doing 500k ops)
java -cp out/ com.cache.miniredis.TestEngine
```
