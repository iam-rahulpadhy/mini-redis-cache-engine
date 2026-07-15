package com.cache.miniredis.eviction;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * LRUEvictionStrategy - O(1) LRU via sentinel doubly-linked list + HashMap.
 *
 * List invariant:
 *   [HEAD] <-> [MRU] <-> ... <-> [LRU] <-> [TAIL]
 *
 * All three operations are O(1) pointer manipulation.
 * NOT thread-safe - callers must hold the write lock.
 */
public class LRUEvictionStrategy<K, V> implements EvictionPolicy<K> {

    // Permanent sentinel nodes. Using sentinels eliminates null-checks;
    // the list is always at least [HEAD <-> TAIL].
    private final DoublyLinkedListNode<K, V> head; // MRU end
    private final DoublyLinkedListNode<K, V> tail; // LRU end

    // Secondary index for O(1) node lookup in keyAccessed().
    // Plain HashMap is fine: all access is serialised via the write lock.
    private final Map<K, DoublyLinkedListNode<K, V>> nodeIndex;

    public LRUEvictionStrategy() {
        this.head      = new DoublyLinkedListNode<>();
        this.tail      = new DoublyLinkedListNode<>();
        head.next      = tail;
        tail.prev      = head;
        this.nodeIndex = new HashMap<>();
    }

    @Override
    public void keyAccessed(K key) {
        DoublyLinkedListNode<K, V> node = nodeIndex.get(key);
        if (node == null) return; // stale call after concurrent eviction - skip
        detachNode(node);
        insertAfterHead(node);
    }

    // Interface-only keyAdded(K) is not used - engine calls the overloaded
    // version below which accepts the pre-built node directly.
    @Override
    public void keyAdded(K key) {
        throw new UnsupportedOperationException("call keyAdded(K, DoublyLinkedListNode) instead");
    }

    /** Engine-facing variant: engine builds the node, then hands it to us. */
    public void keyAdded(K key, DoublyLinkedListNode<K, V> node) {
        insertAfterHead(node);
        nodeIndex.put(key, node);
    }

    @Override
    public K evictNext() {
        DoublyLinkedListNode<K, V> lru = tail.prev;
        if (lru == head) throw new NoSuchElementException("evictNext() on empty LRU list");
        detachNode(lru);
        nodeIndex.remove(lru.key);
        // Null pointers so the GC can collect this node immediately.
        lru.prev = null;
        lru.next = null;
        return lru.key;
    }

    /**
     * Called when the engine explicitly deletes a key. We must unlink it from
     * the LRU list too, otherwise evictNext() would return a dead key.
     */
    public void keyRemoved(K key) {
        DoublyLinkedListNode<K, V> node = nodeIndex.remove(key);
        if (node == null) return;
        detachNode(node);
        node.prev = null;
        node.next = null;
    }

    public DoublyLinkedListNode<K, V> getNode(K key) { return nodeIndex.get(key); }

    /** Resets to empty-list state. Called by MiniRedisEngine.clear(). */
    public void clear() {
        // Sever all pointer chains so the GC can collect old nodes.
        // Just calling nodeIndex.clear() would leave the sentinel chain intact.
        DoublyLinkedListNode<K, V> cur = head.next;
        while (cur != tail) {
            DoublyLinkedListNode<K, V> nxt = cur.next;
            cur.prev  = null;
            cur.next  = null;
            cur.key   = null;
            cur.value = null;
            cur = nxt;
        }
        head.next = tail;
        tail.prev = head;
        nodeIndex.clear();
    }

    // -------------------------------------------------------------------
    // O(1) pointer manipulation helpers
    // -------------------------------------------------------------------

    /** Unlinks node from its current position. Exactly 2 pointer writes. */
    private void detachNode(DoublyLinkedListNode<K, V> node) {
        node.prev.next = node.next;  // bypass node going forward
        node.next.prev = node.prev;  // bypass node going backward
    }

    /**
     * Inserts node at MRU position (right after HEAD). Exactly 4 pointer writes.
     * ORDER MATTERS: head.next.prev must be set before head.next is overwritten,
     * otherwise we create a self-loop (node.prev = node bug).
     */
    private void insertAfterHead(DoublyLinkedListNode<K, V> node) {
        node.next      = head.next;   // node -> old MRU
        node.prev      = head;        // node <- HEAD
        head.next.prev = node;        // old MRU <- node  (before head.next changes!)
        head.next      = node;        // HEAD -> node
    }
}
