package com.cache.miniredis.eviction;

/**
 * DoublyLinkedListNode - Intrusive list node for the LRU eviction structure.
 *
 * Holds the cache key, value, TTL deadline, and the prev/next list pointers.
 * expiryTime == 0 means the entry is immortal.
 */
public class DoublyLinkedListNode<K, V> {

    public DoublyLinkedListNode<K, V> prev;
    public DoublyLinkedListNode<K, V> next;
    public K    key;
    public V    value;
    public long expiryTime;

    public DoublyLinkedListNode(K key, V value, long expiryTime) {
        this.key        = key;
        this.value      = value;
        this.expiryTime = expiryTime;
        this.prev       = null;
        this.next       = null;
    }

    // Sentinel constructor - HEAD and TAIL nodes carry no data.
    public DoublyLinkedListNode() { this(null, null, 0L); }

    @Override
    public String toString() {
        return "Node{key=" + key + ", value=" + value + ", expiryTime=" + expiryTime + "}";
    }
}
