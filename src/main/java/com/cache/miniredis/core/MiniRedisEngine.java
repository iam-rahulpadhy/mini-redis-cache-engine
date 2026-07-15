package com.cache.miniredis.core;

import com.cache.miniredis.concurrency.CacheLockManager;
import com.cache.miniredis.eviction.DoublyLinkedListNode;
import com.cache.miniredis.eviction.LRUEvictionStrategy;
import com.cache.miniredis.eviction.TtlHeap;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Core cache engine implementing thread-safe O(1) operations,
 * strict LRU eviction, and background TTL reaping.
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
            DoublyLinkedListNode<K, V> node = nodeMap.get(key);

            if (node != null) {
                node.value = value;
                node.expiryTime = expiryTime;
                lruStrategy.keyAccessed(key);
            } else {
                evictIfAtCapacity();
                node = new DoublyLinkedListNode<>(key, value, expiryTime);
                nodeMap.put(key, node);
                lruStrategy.keyAdded(key, node);
                liveCount.incrementAndGet();
            }

            if (expiryTime > 0) {
                ttlHeap.push(expiryTime, key);
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

        // Read lock released. Acquire write lock since both lazy expiry 
        // and LRU promotion mutate state.
        lockManager.acquireWriteLock();
        try {
            // Re-fetch to prevent ABA if another thread modified it
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
    @Override
    public boolean remove(K key) {
        lockManager.acquireWriteLock();
        try {
            DoublyLinkedListNode<K, V> node = nodeMap.remove(key);
            if (node != null) {
                lruStrategy.keyRemoved(key);
                liveCount.decrementAndGet();
                return true;
            }
            return false;
        } finally {
            lockManager.releaseWriteLock();
        }
    }
    @Override
    public void clear() {
        lockManager.acquireWriteLock();
        try {
            lruStrategy.clear();
            nodeMap.clear();
            ttlHeap.clear();
            liveCount.set(0);
        } finally {
            lockManager.releaseWriteLock();
        }
    }

    /**
     * Called by the background reaper thread. Removes the key only if it is
     * currently expired, guarding against stale TTL heap entries.
     */
    public void reapExpired(K key) {
        lockManager.acquireWriteLock();
        try {
            DoublyLinkedListNode<K, V> node = nodeMap.get(key);
            if (node != null && isExpired(node)) {
                nodeMap.remove(key);
                lruStrategy.keyRemoved(key);
                liveCount.decrementAndGet();
            }
        } finally {
            lockManager.releaseWriteLock();
        }
    }
    @Override public int     size()                              { return liveCount.get(); }

    // expiryTime == 0 means immortal.
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
