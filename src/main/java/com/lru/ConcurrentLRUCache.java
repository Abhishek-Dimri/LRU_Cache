package com.lru;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * High-performance thread-safe LRU Cache with memory awareness
 * Uses HashMap for O(1) lookups and doubly linked list for O(1) eviction
 */
public class ConcurrentLRUCache<K, V> {
    
    private final int maxCapacity;
    private final long maxMemoryBytes;
    private final ConcurrentHashMap<K, Node<K, V>> cache;
    private final ReentrantReadWriteLock lock;
    private final ReentrantReadWriteLock.ReadLock readLock;
    private final ReentrantReadWriteLock.WriteLock writeLock;
    
    // Doubly linked list for LRU ordering
    private Node<K, V> head;
    private Node<K, V> tail;
    
    // Metrics
    private final AtomicLong currentMemoryUsage;
    private volatile int currentSize;
    
    public ConcurrentLRUCache(int maxCapacity) {
        this(maxCapacity, Long.MAX_VALUE);
    }
    
    public ConcurrentLRUCache(int maxCapacity, long maxMemoryBytes) {
        this.maxCapacity = maxCapacity;
        this.maxMemoryBytes = maxMemoryBytes;
        this.cache = new ConcurrentHashMap<>(maxCapacity);
        this.lock = new ReentrantReadWriteLock();
        this.readLock = lock.readLock();
        this.writeLock = lock.writeLock();
        this.currentMemoryUsage = new AtomicLong(0);
        this.currentSize = 0;
        
        // Initialize dummy head and tail nodes
        this.head = new Node<>(null, null);
        this.tail = new Node<>(null, null);
        head.next = tail;
        tail.prev = head;
    }
    
    public V get(K key) {
        readLock.lock();
        try {
            Node<K, V> node = cache.get(key);
            if (node != null) {
                // Move to front (most recently used)
                moveToHead(node);
                return node.value;
            }
            return null;
        } finally {
            readLock.unlock();
        }
    }
    
    public void put(K key, V value) {
        // Handle zero capacity case - don't store anything
        if (maxCapacity == 0) {
            return;
        }
        
        writeLock.lock();
        try {
            Node<K, V> existingNode = cache.get(key);
            
            if (existingNode != null) {
                // Update existing node
                currentMemoryUsage.addAndGet(-existingNode.memorySize);
                existingNode.value = value;
                existingNode.memorySize = new Node<>(key, value).memorySize;
                currentMemoryUsage.addAndGet(existingNode.memorySize);
                moveToHead(existingNode);
            } else {
                // Add new node
                Node<K, V> newNode = new Node<>(key, value);
                
                // Check capacity and memory limits
                evictIfNeeded(newNode.memorySize);
                
                cache.put(key, newNode);
                addToHead(newNode);
                currentSize++;
                currentMemoryUsage.addAndGet(newNode.memorySize);
            }
        } finally {
            writeLock.unlock();
        }
    }
    
    public boolean remove(K key) {
        writeLock.lock();
        try {
            Node<K, V> node = cache.remove(key);
            if (node != null) {
                removeFromList(node);
                currentSize--;
                currentMemoryUsage.addAndGet(-node.memorySize);
                return true;
            }
            return false;
        } finally {
            writeLock.unlock();
        }
    }
    
    public void clear() {
        writeLock.lock();
        try {
            cache.clear();
            head.next = tail;
            tail.prev = head;
            currentSize = 0;
            currentMemoryUsage.set(0);
        } finally {
            writeLock.unlock();
        }
    }
    
    public int size() {
        return currentSize;
    }
    
    public long getMemoryUsage() {
        return currentMemoryUsage.get();
    }
    
    public boolean isEmpty() {
        return currentSize == 0;
    }
    
    private void evictIfNeeded(long newItemMemory) {
        // Evict based on capacity
        while (currentSize >= maxCapacity) {
            evictLRU();
        }
        
        // Evict based on memory usage
        while (currentMemoryUsage.get() + newItemMemory > maxMemoryBytes && currentSize > 0) {
            evictLRU();
        }
    }
    
    private void evictLRU() {
        Node<K, V> lru = tail.prev;
        if (lru != head) {
            cache.remove(lru.key);
            removeFromList(lru);
            currentSize--;
            currentMemoryUsage.addAndGet(-lru.memorySize);
        }
    }
    
    private void addToHead(Node<K, V> node) {
        node.prev = head;
        node.next = head.next;
        head.next.prev = node;
        head.next = node;
    }
    
    private void removeFromList(Node<K, V> node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }
    
    private void moveToHead(Node<K, V> node) {
        removeFromList(node);
        addToHead(node);
    }
}