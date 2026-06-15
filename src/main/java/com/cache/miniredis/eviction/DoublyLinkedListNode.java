package com.cache.miniredis.eviction;

/**
 * DoublyLinkedListNode -- The fundamental storage unit of the Mini-Redis eviction list.
 *
 * DUAL ROLE:
 *   1. Value Container: holds cache key, value, and TTL deadline (expiryTime).
 *   2. Eviction List Element: participates in the doubly-linked eviction list
 *      via intrusive prev/next pointer fields.
 *
 * This intrusive design eliminates any separate wrapper structure.
 * The ConcurrentHashMap in MiniRedisEngine maps keys directly to these nodes,
 * enabling O(1) both-map-and-list removal without traversal.
 *
 * APPROXIMATE HEAP LAYOUT (64-bit JVM, compressed oops):
 *   Object header (mark + class) : 16 bytes
 *   prev        : reference        8 bytes
 *   next        : reference        8 bytes
 *   key         : reference        8 bytes
 *   value       : reference        8 bytes
 *   expiryTime  : long             8 bytes
 *   Total approx: 56 bytes per node (before alignment padding)
 *
 * SENTINEL NODE DESIGN:
 *   HEAD (MRU end) and TAIL (LRU end) are permanent data-less sentinels.
 *   All real nodes live between them:
 *
 *   [HEAD-sentinel] <--> [node_A] <--> [node_B] <--> [TAIL-sentinel]
 *    (most recent)                                   (least recent)
 *
 * CONCURRENCY:
 *   Nodes are mutable (prev/next change on every access). NOT thread-safe.
 *   All mutations must occur while the caller holds the CacheLockManager write lock.
 *   Visibility guaranteed by the happens-before edge of lock release/acquire.
 *
 * @param <K> type of the cache key held by this node
 * @param <V> type of the cache value held by this node
 *
 * @author  Mini-Redis Engineering Team
 * @version 1.0.0
 * @since   Java 21
 */
public class DoublyLinkedListNode<K, V> {

    /**
     * Pointer toward the MRU / HEAD end of the eviction list.
     * null only for the HEAD sentinel node itself.
     * A null prev on a non-sentinel node indicates a dangling-reference bug.
     */
    public DoublyLinkedListNode<K, V> prev;

    /**
     * Pointer toward the LRU / TAIL end of the eviction list.
     * null only for the TAIL sentinel node itself.
     */
    public DoublyLinkedListNode<K, V> next;

    /**
     * Cache key associated with this node. Used during eviction to remove the
     * corresponding entry from ConcurrentHashMap in O(1).
     * null for sentinel nodes.
     */
    public K key;

    /**
     * The cached value. May be updated in-place on a put() for an existing key,
     * avoiding a node allocation and list re-linking.
     * null for sentinel nodes.
     */
    public V value;

    /**
     * Absolute wall-clock expiry deadline in epoch-milliseconds.
     * Computed as: System.currentTimeMillis() + ttlMillis at insertion time.
     *
     * A value of 0 (or any negative value) signals no expiry -- the entry is
     * immortal until explicitly removed or evicted.
     *
     * Precision Note: System.currentTimeMillis() has ~10ms granularity on most
     * OS schedulers. Sub-millisecond precision would require System.nanoTime().
     */
    public long expiryTime;

    /**
     * Constructs a fully-initialised cache data node.
     *
     * The new node is NOT linked into any list by this constructor.
     * Linking is the responsibility of LRUEvictionStrategy to maintain
     * its invariants atomically.
     *
     * @param key        cache key; null only for sentinel nodes
     * @param value      cached value
     * @param expiryTime absolute epoch-millis expiry time; 0 for immortal entries
     */
    public DoublyLinkedListNode(K key, V value, long expiryTime) {
        this.key        = key;
        this.value      = value;
        this.expiryTime = expiryTime;
        this.prev       = null;
        this.next       = null;
    }

    /**
     * Constructs a sentinel node (HEAD or TAIL) with no data fields.
     *
     * Sentinel nodes are permanent fixtures -- never inserted into ConcurrentHashMap,
     * never returned by evictNext(), never evicted.
     */
    public DoublyLinkedListNode() {
        this(null, null, 0L);
    }

    /**
     * Human-readable representation for debugging. Intentionally omits prev/next
     * to avoid infinite recursion when printing linked nodes.
     *
     * @return string of the form "Node{key=..., value=..., expiryTime=...}"
     */
    @Override
    public String toString() {
        return "Node{key=" + key + ", value=" + value + ", expiryTime=" + expiryTime + "}";
    }
}
