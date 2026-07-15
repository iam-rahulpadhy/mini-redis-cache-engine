package com.cache.miniredis.core;

import com.cache.miniredis.concurrency.CacheLockManager;
import com.cache.miniredis.eviction.DoublyLinkedListNode;
import com.cache.miniredis.eviction.EvictionPolicy;
import com.cache.miniredis.eviction.LRUEvictionStrategy;
import com.cache.miniredis.eviction.TtlHeap;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MiniRedisEngine - Core cache engine. Phase 2 fills in put/get/remove/clear.
 *
 * Data structures:
 *   ConcurrentHashMap for O(1) key lookup
 *   LRUEvictionStrategy (doubly-linked list) for O(1) LRU eviction
 *   TtlHeap (min-heap) for the background TTL reaper (Phase 3)
 */
public class MiniRedisEngine<K, V> implements CacheManager<K, V> {

    private final ConcurrentHashMap<K, DoublyLinkedListNode<K, V>> nodeMap;
    private final LRUEvictionStrategy<K, V> lruStrategy;
    private final CacheLockManager          lockManager;
    private final TtlHeap<K>                ttlHeap;
    private final int                       capacity;
    private final AtomicInteger             liveCount;

    public MiniRedisEngine(int capacity,
                           LRUEvictionStrategy<K, V> lruStrategy,
                           CacheLockManager lockManager) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
        this.capacity    = capacity;
        this.lruStrategy = lruStrategy;
        this.lockManager = lockManager;
        this.nodeMap     = new ConcurrentHashMap<>(capacity);
        this.ttlHeap     = new TtlHeap<>();
        this.liveCount   = new AtomicInteger(0);
    }

    @Override
    public void put(K key, V value, long ttlMillis) {
        lockManager.acquireWriteLock();
        try {
            long expiryTime = (ttlMillis > 0) ? System.currentTimeMillis() + ttlMillis : 0;
            DoublyLinkedListNode<K, V> existingNode = nodeMap.get(key);

            if (existingNode != null) {
                // Update in-place
                existingNode.value = value;
                existingNode.expiryTime = expiryTime;
                lruStrategy.keyAccessed(key);
                
                if (expiryTime > 0) {
                    ttlHeap.push(expiryTime, key);
                }
            } else {
                evictIfAtCapacity();

                DoublyLinkedListNode<K, V> newNode = new DoublyLinkedListNode<>(key, value, expiryTime);
                nodeMap.put(key, newNode);
                lruStrategy.keyAdded(key, newNode);
                liveCount.incrementAndGet();

                if (expiryTime > 0) {
                    ttlHeap.push(expiryTime, key);
                }
            }
        } finally {
            lockManager.releaseWriteLock();
        }
    }
    @Override
    public V get(K key) {
        lockManager.acquireReadLock();
        DoublyLinkedListNode<K, V> node;
        try {
            node = nodeMap.get(key);
        } finally {
            lockManager.releaseReadLock();
        }

        if (node == null) {
            return null; // Fast path: cache miss
        }

        // We have a node, but we must acquire the write lock because both 
        // lazy expiry (removal) and LRU promotion (list update) mutate state.
        lockManager.acquireWriteLock();
        try {
            // ABA prevention: re-fetch the node because it may have been 
            // evicted by another thread while we were waiting for the write lock.
            node = nodeMap.get(key);
            if (node == null) {
                return null;
            }

            if (isExpired(node)) {
                // Lazy expiry cleanup
                nodeMap.remove(key);
                lruStrategy.keyRemoved(key);
                liveCount.decrementAndGet();
                return null;
            }

            // Valid cache hit: promote to MRU and return
            lruStrategy.keyAccessed(key);
            return node.value;
        } finally {
            lockManager.releaseWriteLock();
        }
    }
    @Override public boolean remove(K key)                       { return false; }
    @Override public void    clear()                             { /* Phase 2 */ }
    @Override public int     size()                              { return liveCount.get(); }

    // expiryTime == 0 means immortal. Only hit the clock when there is a TTL.
    boolean isExpired(DoublyLinkedListNode<K, V> node) {
        return node.expiryTime > 0 && System.currentTimeMillis() > node.expiryTime;
    }

    private void evictIfAtCapacity() {
        while (liveCount.get() >= capacity) {
            K victim = lruStrategy.evictNext();
            nodeMap.remove(victim);
            liveCount.decrementAndGet();
        }
    }

    public TtlHeap<K> getTtlHeap() { return ttlHeap; }
}
