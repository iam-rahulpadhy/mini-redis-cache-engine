package com.cache.miniredis.eviction;

import java.util.PriorityQueue;

/**
 * Min-heap tracking (expiryTime, key) pairs for the TTL reaper.
 *
 * Root is always the entry that expires soonest. 
 * Not thread-safe; relies on engine locks.
 */
public class TtlHeap<K> {

    public static class TtlEntry<K> implements Comparable<TtlEntry<K>> {
        public final long expiryTime;
        public final K    key;

        public TtlEntry(long expiryTime, K key) {
            this.expiryTime = expiryTime;
            this.key        = key;
        }

        @Override
        public int compareTo(TtlEntry<K> other) {
            return Long.compare(this.expiryTime, other.expiryTime);
        }
    }

    private final PriorityQueue<TtlEntry<K>> heap = new PriorityQueue<>();

    // Skip entries with no TTL - they live until explicitly removed or evicted.
    public void push(long expiryTime, K key) {
        if (expiryTime <= 0) return;
        heap.offer(new TtlEntry<>(expiryTime, key));
    }

    public TtlEntry<K> peek()    { return heap.peek();    }
    public TtlEntry<K> pop()     { return heap.poll();    }
    public int         size()    { return heap.size();    }
    public boolean     isEmpty() { return heap.isEmpty(); }
    public void        clear()   { heap.clear(); }
}
