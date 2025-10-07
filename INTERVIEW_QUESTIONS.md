# LRU Cache Interview Questions & Answers

## Core Concepts

### Q1: What is an LRU Cache and how does it work?
**Answer:** LRU (Least Recently Used) Cache is a cache eviction policy that removes the least recently accessed item when the cache reaches its capacity limit. It maintains the order of access, moving recently accessed items to the front and evicting from the back.

**Key Components:**
- HashMap for O(1) key lookups
- Doubly linked list for O(1) insertion/deletion and maintaining access order
- Head pointer (most recent) and tail pointer (least recent)

### Q2: Why use HashMap + Doubly Linked List instead of other data structures?

**Answer:**
- **HashMap**: Provides O(1) average time complexity for key lookups
- **Doubly Linked List**: Enables O(1) insertion, deletion, and reordering
- **Alternative approaches and their limitations:**
  - Array: O(n) for reordering elements
  - Single linked list: O(n) to find previous node for deletion
  - TreeMap: O(log n) operations, unnecessary ordering overhead

### Q3: How do you handle thread safety in LRU Cache?

**Answer:**
```java
// Our approach uses ReentrantReadWriteLock
private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
private final ReadLock readLock = lock.readLock();
private final WriteLock writeLock = lock.writeLock();

// Read operations (get) - multiple threads can read concurrently
readLock.lock();
try {
    // read operation
} finally {
    readLock.unlock();
}

// Write operations (put/remove) - exclusive access
writeLock.lock();
try {
    // write operation
} finally {
    writeLock.unlock();
}
```

**Why ReentrantReadWriteLock over synchronized?**
- Allows multiple concurrent readers
- Better performance for read-heavy workloads
- Reduces contention compared to synchronized methods

## Implementation Deep Dive

### Q4: Walk me through the PUT operation step by step.

**Answer:**
1. **Check capacity**: If maxCapacity is 0, return immediately
2. **Acquire write lock**: Ensure exclusive access for modifications
3. **Check if key exists**: Look up in HashMap
4. **If exists**: Update value, adjust memory usage, move to head
5. **If new**: Create new node, check eviction needs, add to HashMap and head
6. **Update metrics**: Increment size, add memory usage
7. **Release lock**: Ensure other threads can proceed

```java
// Simplified PUT logic
if (existingNode != null) {
    updateExistingNode(existingNode, value);
    moveToHead(existingNode);
} else {
    evictIfNeeded(newNode.memorySize);
    addNewNode(key, value);
}
```

### Q5: How do you handle the GET operation for optimal performance?

**Answer:**
```java
public V get(K key) {
    readLock.lock();  // Allow concurrent reads
    try {
        Node<K, V> node = cache.get(key);  // O(1) HashMap lookup
        if (node != null) {
            moveToHead(node);  // O(1) reordering
            return node.value;
        }
        return null;
    } finally {
        readLock.unlock();
    }
}
```

**Key optimizations:**
- Read lock allows multiple concurrent readers
- HashMap provides O(1) average lookup
- Doubly linked list enables O(1) reordering
- No unnecessary object creation

### Q6: How do you implement memory-aware eviction?

**Answer:**
```java
private void evictIfNeeded(long newItemMemory) {
    // Capacity-based eviction
    while (currentSize >= maxCapacity) {
        evictLRU();
    }
    
    // Memory-based eviction
    while (currentMemoryUsage.get() + newItemMemory > maxMemoryBytes && currentSize > 0) {
        evictLRU();
    }
}

// Memory estimation per node
private long estimateMemorySize(K key, V value) {
    long size = 16; // base object overhead
    
    if (key instanceof String) {
        size += ((String) key).length() * 2 + 24; // UTF-16 encoding + overhead
    } else {
        size += 8; // reference size
    }
    
    // Similar calculation for value
    return size;
}
```

## Performance & Scalability

### Q7: What's the time complexity of your LRU Cache operations?

**Answer:**
- **GET**: O(1) average, O(n) worst case (HashMap collision)
- **PUT**: O(1) average, O(n) worst case (HashMap collision + eviction)
- **REMOVE**: O(1) average, O(n) worst case
- **Space**: O(n) where n is the number of items

**Why O(1) average?**
- HashMap operations are O(1) average with good hash function
- Doubly linked list operations (add/remove/move) are always O(1)
- Eviction is O(1) since we always remove from tail

### Q8: How would you optimize for different workload patterns?

**Answer:**

**Read-Heavy Workload (95% reads, 5% writes):**
- Our ReentrantReadWriteLock is perfect
- Consider read-only cache replicas
- Use concurrent data structures

**Write-Heavy Workload (50% writes, 50% reads):**
- Consider ConcurrentHashMap with custom eviction
- Lock-free implementations with atomic operations
- Batch write operations

**Memory-Constrained Environment:**
- Implement soft references for values
- Add compression for large objects
- Use off-heap storage (Chronicle Map)

**High-Concurrency Environment:**
- Segment-based locking (like ConcurrentHashMap)
- Lock-free algorithms using CAS operations
- Thread-local caches with periodic synchronization

## Edge Cases & Error Handling

### Q9: How do you handle edge cases?

**Answer:**

**Zero Capacity Cache:**
```java
if (maxCapacity == 0) {
    return; // Don't store anything
}
```

**Null Keys/Values:**
```java
// Allow null values but not null keys (following HashMap convention)
if (key == null) {
    throw new NullPointerException("Key cannot be null");
}
```

**Memory Overflow:**
```java
// Continuous eviction until memory constraint is satisfied
while (currentMemoryUsage.get() + newItemMemory > maxMemoryBytes && currentSize > 0) {
    evictLRU();
}
```

**Concurrent Modifications:**
```java
// Proper lock acquisition order to prevent deadlocks
// Always acquire write lock for any structural modifications
// Use atomic operations for metrics (AtomicLong for memory usage)
```

### Q10: How would you add features like TTL (Time To Live)?

**Answer:**
```java
public class Node<K, V> {
    K key;
    V value;
    Node<K, V> prev, next;
    long expirationTime; // New field
    
    public boolean isExpired() {
        return System.currentTimeMillis() > expirationTime;
    }
}

// In get operation
public V get(K key) {
    readLock.lock();
    try {
        Node<K, V> node = cache.get(key);
        if (node != null && !node.isExpired()) {
            moveToHead(node);
            return node.value;
        } else if (node != null && node.isExpired()) {
            // Lazy eviction of expired items
            removeExpiredNode(node);
        }
        return null;
    } finally {
        readLock.unlock();
    }
}
```

## Advanced Topics

### Q11: How would you implement distributed LRU cache?

**Answer:**

**Consistent Hashing Approach:**
- Partition cache across multiple nodes using consistent hashing
- Each node maintains its own LRU cache
- Handle node failures with replication

**Centralized Metadata Approach:**
- Separate storage nodes from metadata management
- Central coordinator tracks access patterns
- Distributed storage with centralized eviction decisions

**Hybrid Approach:**
- Local L1 cache with global L2 cache
- Asynchronous replication of access patterns
- Probabilistic eviction algorithms

### Q12: How would you monitor and debug cache performance?

**Answer:**

**Key Metrics:**
```java
// Cache hit ratio
public double getHitRatio() {
    return (double) hits / (hits + misses);
}

// Average response time
public double getAverageLatency() {
    return totalLatency / totalOperations;
}

// Memory efficiency
public double getMemoryEfficiency() {
    return (double) cache.size() / maxCapacity;
}

// Eviction rate
public double getEvictionRate() {
    return evictions / totalOperations;
}
```

**Debugging Tools:**
- JMX beans for runtime monitoring
- Detailed logging with sampling
- Performance counters and histograms
- Cache dump utilities for state inspection

### Q13: What are the trade-offs of your implementation?

**Answer:**

**Advantages:**
- True O(1) operations for common cases
- Excellent concurrent read performance
- Memory awareness prevents OOM
- Comprehensive edge case handling

**Trade-offs:**
- Memory overhead: ~40 bytes per cache entry
- Write operations are slower due to lock acquisition
- HashMap resize can cause temporary performance degradation
- More complex than simple synchronized solutions

**When to use alternatives:**
- **ConcurrentHashMap**: For simple key-value without LRU semantics
- **Caffeine**: For feature-rich caching with advanced eviction policies
- **Redis**: For distributed caching across multiple applications
- **Hazelcast**: For in-memory data grid with high availability

## System Design Context

### Q14: How does this fit into a larger system architecture?

**Answer:**

**Microservices Architecture:**
- Application-level cache for frequently accessed data
- Reduces database load and improves response times
- Complements Redis for multi-service caching

**Database Layer:**
- Query result caching
- Connection pool optimization
- Prepared statement caching

**Web Applications:**
- Session data caching
- User preference caching
- Static content caching

**API Gateway:**
- Rate limiting token bucket cache
- Authentication token cache
- Route configuration cache

### Q15: How would you test this in production?

**Answer:**

**Unit Tests:**
- Basic operations (put/get/remove)
- LRU eviction correctness
- Thread safety under concurrent load
- Edge cases (zero capacity, memory limits)

**Integration Tests:**
- Performance benchmarks under realistic load
- Memory usage validation
- Long-running stability tests
- Failover and recovery scenarios

**Production Monitoring:**
- Cache hit/miss ratios
- Memory usage trends
- Lock contention metrics
- GC impact analysis

**Load Testing:**
- Gradual load increase to find breaking points
- Mixed read/write workload simulation
- Memory pressure testing
- Concurrent user simulation

This implementation provides a solid foundation for understanding LRU cache concepts and can be extended for various real-world scenarios.