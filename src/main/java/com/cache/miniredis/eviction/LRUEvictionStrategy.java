package com.cache.miniredis.eviction;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Standard LRU implementation using a doubly-linked list and HashMap.
 * Not thread-safe; relies on engine locks.
 */
public class LRUEvictionStrategy<K, V> {

    private final DoublyLinkedListNode<K, V> head;
    private final DoublyLinkedListNode<K, V> tail;
    private final Map<K, DoublyLinkedListNode<K, V>> nodeIndex;

    public LRUEvictionStrategy() {
        this.head = new DoublyLinkedListNode<>();
        this.tail = new DoublyLinkedListNode<>();
        head.next = tail;
        tail.prev = head;
        this.nodeIndex = new HashMap<>();
    }

    public void keyAccessed(K key) {
        DoublyLinkedListNode<K, V> node = nodeIndex.get(key);
        if (node == null) return;
        
        detachNode(node);
        insertAfterHead(node);
    }

    public void keyAdded(K key, DoublyLinkedListNode<K, V> node) {
        insertAfterHead(node);
        nodeIndex.put(key, node);
    }

    public K evictNext() {
        DoublyLinkedListNode<K, V> lru = tail.prev;
        if (lru == head) {
            throw new NoSuchElementException("LRU list is empty");
        }
        
        detachNode(lru);
        nodeIndex.remove(lru.key);
        
        lru.prev = null;
        lru.next = null;
        
        return lru.key;
    }

    public void keyRemoved(K key) {
        DoublyLinkedListNode<K, V> node = nodeIndex.remove(key);
        if (node == null) return;
        
        detachNode(node);
        node.prev = null;
        node.next = null;
    }

    public DoublyLinkedListNode<K, V> getNode(K key) { 
        return nodeIndex.get(key); 
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
        nodeIndex.clear();
    }

    private void detachNode(DoublyLinkedListNode<K, V> node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }

    private void insertAfterHead(DoublyLinkedListNode<K, V> node) {
        node.next = head.next;
        node.prev = head;
        head.next.prev = node;
        head.next = node;
    }
}
