package com.cache.miniredis.eviction;

import java.util.NoSuchElementException;

/**
 * Standard LRU implementation using a doubly-linked list.
 * Not thread-safe; relies on engine locks.
 * Synchronized directly with the engine's ConcurrentHashMap for strict O(1).
 */
public class LRUEvictionStrategy<K, V> {

    private final DoublyLinkedListNode<K, V> head;
    private final DoublyLinkedListNode<K, V> tail;

    public LRUEvictionStrategy() {
        this.head = new DoublyLinkedListNode<>();
        this.tail = new DoublyLinkedListNode<>();
        head.next = tail;
        tail.prev = head;
    }

    public void nodeAccessed(DoublyLinkedListNode<K, V> node) {
        detachNode(node);
        insertAfterHead(node);
    }

    public void nodeAdded(DoublyLinkedListNode<K, V> node) {
        insertAfterHead(node);
    }

    public DoublyLinkedListNode<K, V> evictNext() {
        DoublyLinkedListNode<K, V> lru = tail.prev;
        if (lru == head) {
            throw new NoSuchElementException("LRU list is empty");
        }
        
        detachNode(lru);
        lru.prev = null;
        lru.next = null;
        
        return lru;
    }

    public void nodeRemoved(DoublyLinkedListNode<K, V> node) {
        detachNode(node);
        node.prev = null;
        node.next = null;
    }

    public void clear() {
        DoublyLinkedListNode<K, V> cur = head.next;
        while (cur != tail) {
            DoublyLinkedListNode<K, V> nxt = cur.next;
            cur.prev = null;
            cur.next = null;
            cur.key = null;
            cur.value = null;
            cur = nxt;
        }
        head.next = tail;
        tail.prev = head;
    }

    private void detachNode(DoublyLinkedListNode<K, V> node) {
        if (node.prev != null && node.next != null) {
            node.prev.next = node.next;
            node.next.prev = node.prev;
        }
    }

    private void insertAfterHead(DoublyLinkedListNode<K, V> node) {
        node.next = head.next;
        node.prev = head;
        head.next.prev = node;
        head.next = node;
    }
}
