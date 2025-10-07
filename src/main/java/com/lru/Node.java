package com.lru;

/**
 * Doubly linked list node for the LRU cache
 */
public class Node<K, V> {
    K key;
    V value;
    Node<K, V> prev;
    Node<K, V> next;
    long memorySize; // approximate memory footprint in bytes
    
    public Node(K key, V value) {
        this.key = key;
        this.value = value;
        this.memorySize = estimateMemorySize(key, value);
    }
    
    private long estimateMemorySize(K key, V value) {
        long size = 16; // base object overhead
        
        if (key instanceof String) {
            size += ((String) key).length() * 2 + 24; // char array + string overhead
        } else {
            size += 8; // reference size
        }
        
        if (value instanceof String) {
            size += ((String) value).length() * 2 + 24;
        } else {
            size += 8;
        }
        
        return size;
    }
}