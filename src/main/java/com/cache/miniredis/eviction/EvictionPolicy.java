package com.cache.miniredis.eviction;

/**
 * {@code EvictionPolicy} -- Strategy contract for selecting which cache key to evict
 * when the cache reaches its configured capacity ceiling.
 *
 * <p>Follows the <strong>Strategy Pattern</strong>: {@code MiniRedisEngine} holds a
 * reference to an {@code EvictionPolicy} implementation and delegates all eviction-order
 * bookkeeping to it. This decouples storage mechanics from the replacement algorithm,
 * enabling runtime-swappable policies (LRU, LFU, FIFO) without modifying the engine.
 *
 * <h2>Concurrency Contract</h2>
 * <p>Implementations are <strong>NOT</strong> required to be internally thread-safe.
 * Thread safety is the caller's responsibility ({@code MiniRedisEngine} via
 * {@code CacheLockManager}). Policy methods are always called while the caller holds
 * the exclusive write lock.
 *
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li>On every {@code put} (new key): {@link #keyAdded(Object)} called after node inserted.</li>
 *   <li>On every {@code get} (cache hit): {@link #keyAccessed(Object)} called to update recency.</li>
 *   <li>When at capacity: {@link #evictNext()} called; engine removes returned key from map.</li>
 * </ol>
 *
 * @param <K> type of cache keys tracked by this eviction policy
 *
 * @author  Mini-Redis Engineering Team
 * @version 1.0.0
 * @since   Java 21
 * @see     LRUEvictionStrategy
 */
public interface EvictionPolicy<K> {

    /**
     * Notifies the policy that an existing key has been accessed (cache hit).
     *
     * <p>For LRU: moves the accessed node to the Most-Recently-Used (MRU) head end
     * via O(1) detach-and-reinsert (2 + 4 = 6 pointer assignments total).
     *
     * <p><strong>Precondition:</strong> key must have been registered via {@link #keyAdded(Object)}.
     * Time Complexity: O(1) for LRU; O(log n) for LFU min-heap variants.
     *
     * @param key the cache key that was accessed; must not be null
     */
    void keyAccessed(K key);

    /**
     * Notifies the policy that a new key has been inserted into the cache.
     *
     * <p>For LRU: inserts a new node at the MRU (head) position of the doubly-linked
     * list via O(1) head insertion (4 pointer assignments).
     *
     * <p><strong>Precondition:</strong> key must NOT already be present in the tracking structure.
     * The engine calls {@link #keyAccessed(Object)} (not keyAdded) for updates to existing keys.
     * Time Complexity: O(1).
     *
     * @param key the newly inserted cache key; must not be null
     */
    void keyAdded(K key);

    /**
     * Selects and returns the next key to evict, then removes it from the tracking structure.
     *
     * <p>For LRU: returns the key at the Least-Recently-Used (LRU/tail) end and unlinks
     * the tail sentinel's predecessor in O(1) -- exactly 2 pointer assignments.
     *
     * <p><strong>Post-condition:</strong> Returned key is no longer tracked by this policy.
     * Engine is responsible for removing it from the backing storage map.
     * Time Complexity: O(1) for LRU.
     *
     * @return key of the entry to evict; never null if tracking structure is non-empty
     * @throws java.util.NoSuchElementException if the policy tracks no keys
     */
    K evictNext();
}
