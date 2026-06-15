package com.cache.miniredis.core;

/**
 * {@code CacheManager} -- Primary contract for the Mini-Redis in-memory cache.
 *
 * <h2>Concurrency Contract</h2>
 * <p>All implementations MUST be thread-safe. Multiple reader threads may call
 * {@link #get(Object)} concurrently without blocking each other. Write operations
 * ({@link #put}, {@link #remove}, {@link #clear}) must acquire exclusive access via
 * the {@code CacheLockManager} to prevent data races on the underlying {@code nodeMap}
 * and the intrusive doubly-linked eviction list.
 *
 * <h2>TTL Semantics</h2>
 * <p>Enforced via <em>lazy expiry</em> at read time AND a background reaper thread.
 * An entry whose TTL has elapsed MUST appear absent to {@link #get(Object)} callers
 * even if the physical {@code DoublyLinkedListNode} has not yet been garbage-collected.
 *
 * <h2>Memory Layout Expectations</h2>
 * <p>Implementations maintain a {@code ConcurrentHashMap} (O(1) lookup) paired with
 * an intrusive doubly-linked list (O(1) LRU eviction).
 * {@link #size()} refers to <em>live, non-expired</em> entries only.
 *
 * @param <K> type of cache keys -- must implement hashCode() and equals() correctly
 * @param <V> type of cache values -- any non-primitive reference type
 *
 * @author  Mini-Redis Engineering Team
 * @version 1.0.0
 * @since   Java 21
 * @see     MiniRedisEngine
 */
public interface CacheManager<K, V> {

    /**
     * Inserts or updates a key-value pair in the cache with an explicit TTL.
     *
     * <p><strong>Time Complexity:</strong> O(1) amortised.
     * HashMap insertion and doubly-linked-list head insertion are both O(1).
     *
     * <p><strong>Eviction:</strong> If at capacity, delegates to
     * {@code EvictionPolicy.evictNext()} BEFORE inserting the new entry,
     * maintaining the capacity invariant atomically under the write lock.
     *
     * <p><strong>Concurrency:</strong> Requires exclusive write lock.
     * Concurrent readers blocked for the write duration to prevent observing
     * a partially-constructed node.
     *
     * @param key       non-null cache key
     * @param value     value to store (null semantics are implementation-defined)
     * @param ttlMillis TTL in ms from current wall-clock; 0 or negative means no expiry
     * @throws IllegalArgumentException if key is null
     */
    void put(K key, V value, long ttlMillis);

    /**
     * Retrieves the value for key if present and not expired.
     *
     * <p><strong>Time Complexity:</strong> O(1).
     * Hash-map lookup plus O(1) LRU node detach-and-reinsert (4 pointer writes).
     *
     * <p><strong>Lazy Expiry:</strong> Compares {@code node.expiryTime} with
     * {@link System#currentTimeMillis()} on every call. Expired entries are silently
     * removed and {@code null} returned.
     *
     * <p><strong>Concurrency:</strong> Acquires shared read lock. Updating LRU order
     * on a cache hit requires a brief write-lock upgrade (pointer move in eviction list).
     * NOTE: {@code ReentrantReadWriteLock} does NOT support direct read-to-write upgrade;
     * the read lock must be released first (ABA re-validation required afterward).
     *
     * @param key non-null cache key to look up
     * @return the associated value, or null if absent or expired
     */
    V get(K key);

    /**
     * Removes the entry for key, regardless of TTL.
     *
     * <p><strong>Time Complexity:</strong> O(1).
     * HashMap remove plus doubly-linked-list unlink ({@code detachNode}) = 2 pointer writes.
     *
     * <p><strong>Concurrency:</strong> Requires exclusive write lock. Two-phase removal
     * (map then list) must be atomic -- no reader may observe a dangling list pointer.
     *
     * @param key non-null cache key to remove
     * @return true if key existed and was removed; false if absent or already expired
     */
    boolean remove(K key);

    /**
     * Removes all entries, resetting the cache to an empty state.
     *
     * <p><strong>Time Complexity:</strong> O(n). Must traverse the eviction list to
     * null all node pointers; map clear is O(n) in {@link java.util.HashMap}.
     *
     * <p><strong>Memory Contract:</strong> After {@code clear()} returns, GC must be
     * able to collect all previously-stored nodes. HEAD and TAIL sentinels must be
     * re-linked to restore the empty-list invariant: {@code head.next = tail},
     * {@code tail.prev = head}.
     *
     * <p><strong>Concurrency:</strong> Requires exclusive write lock for full duration.
     */
    void clear();

    /**
     * Returns the number of live, non-expired entries.
     *
     * <p><strong>Time Complexity:</strong> O(1) -- backed by an {@code AtomicInteger}
     * maintained on every {@link #put} and {@link #remove}.
     *
     * <p><strong>Accuracy Note:</strong> May transiently include expired entries not
     * yet reaped by the background TTL reaper. Treat as a best-effort estimate.
     *
     * @return live entry count; never negative
     */
    int size();
}
