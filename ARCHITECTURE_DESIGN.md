# LRU Cache Architecture & Design Patterns

## System Architecture Overview

### High-Level Architecture
```
┌─────────────────────────────────────────────────────────┐
│                    Client Application                   │
├─────────────────────────────────────────────────────────┤
│                 ConcurrentLRUCache                      │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐     │
│  │   HashMap   │  │ ReadWrite   │  │   Doubly    │     │
│  │  (O(1) Key  │  │   Lock      │  │   Linked    │     │
│  │  Lookup)    │  │ (Concurrent │  │    List     │     │
│  │             │  │   Safety)   │  │ (LRU Order) │     │
│  └─────────────┘  └─────────────┘  └─────────────┘     │
├─────────────────────────────────────────────────────────┤
│               Underlying Data Store                     │
│        (Database, File System, Network)                │
└─────────────────────────────────────────────────────────┘
```

### Memory Layout & Data Flow
```
Memory Organization:
┌──────────────────────────────────────────────────────────┐
│                     Heap Memory                          │
│                                                          │
│  HashMap<K, Node<K,V>>     Doubly Linked List          │
│  ┌─────────────────┐        ┌─────┐    ┌─────┐         │
│  │ key1 -> node1   │────────│Head │────│Node1│         │
│  │ key2 -> node2   │        │     │    │     │         │
│  │ key3 -> node3   │        └─────┘    └─────┘         │
│  │ ...             │                      │             │
│  └─────────────────┘                   ┌─────┐         │
│                                         │Node2│         │
│  Memory Tracking:                       │     │         │
│  ┌─────────────────┐                    └─────┘         │
│  │ AtomicLong      │                      │             │
│  │ currentMemory   │                   ┌─────┐         │
│  │ Usage           │                   │Node3│         │
│  └─────────────────┘                   │     │         │
│                                         └─────┘         │
│                                           │             │
│                                        ┌─────┐         │
│                                        │Tail │         │
│                                        │     │         │
│                                        └─────┘         │
└──────────────────────────────────────────────────────────┘
```

## Design Patterns Implementation

### 1. Adapter Pattern
**Use Case:** Integrating with different data sources

```java
public interface DataSource<K, V> {
    V load(K key);
    void save(K key, V value);
}

public class DatabaseAdapter<K, V> implements DataSource<K, V> {
    private final JdbcTemplate jdbcTemplate;
    
    @Override
    public V load(K key) {
        return jdbcTemplate.queryForObject(
            "SELECT data FROM cache_table WHERE key = ?", 
            valueClass, key);
    }
}

public class CacheWithDataSource<K, V> {
    private final ConcurrentLRUCache<K, V> cache;
    private final DataSource<K, V> dataSource;
    
    public V get(K key) {
        V value = cache.get(key);
        if (value == null) {
            value = dataSource.load(key);
            if (value != null) {
                cache.put(key, value);
            }
        }
        return value;
    }
}
```

### 2. Decorator Pattern
**Use Case:** Adding features like metrics, logging, compression

```java
public abstract class CacheDecorator<K, V> implements Cache<K, V> {
    protected final Cache<K, V> cache;
    
    public CacheDecorator(Cache<K, V> cache) {
        this.cache = cache;
    }
}

public class MetricsCache<K, V> extends CacheDecorator<K, V> {
    private final MeterRegistry meterRegistry;
    private final Counter hits, misses;
    
    @Override
    public V get(K key) {
        long start = System.nanoTime();
        V value = cache.get(key);
        long duration = System.nanoTime() - start;
        
        if (value != null) {
            hits.increment();
        } else {
            misses.increment();
        }
        
        Timer.Sample.start(meterRegistry)
            .stop(Timer.builder("cache.get").register(meterRegistry));
        
        return value;
    }
}

public class LoggingCache<K, V> extends CacheDecorator<K, V> {
    private static final Logger logger = LoggerFactory.getLogger(LoggingCache.class);
    
    @Override
    public V get(K key) {
        logger.debug("Cache get: {}", key);
        V value = cache.get(key);
        logger.debug("Cache {} for key: {}", value != null ? "hit" : "miss", key);
        return value;
    }
}
```

### 3. Strategy Pattern
**Use Case:** Different eviction policies

```java
public interface EvictionStrategy<K, V> {
    void onAccess(Node<K, V> node);
    Node<K, V> selectForEviction();
}

public class LRUEvictionStrategy<K, V> implements EvictionStrategy<K, V> {
    private Node<K, V> head, tail;
    
    @Override
    public void onAccess(Node<K, V> node) {
        moveToHead(node);
    }
    
    @Override
    public Node<K, V> selectForEviction() {
        return tail.prev;
    }
}

public class LFUEvictionStrategy<K, V> implements EvictionStrategy<K, V> {
    private final Map<Node<K, V>, Integer> frequencies = new ConcurrentHashMap<>();
    
    @Override
    public void onAccess(Node<K, V> node) {
        frequencies.merge(node, 1, Integer::sum);
    }
    
    @Override
    public Node<K, V> selectForEviction() {
        return frequencies.entrySet().stream()
            .min(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);
    }
}
```

### 4. Observer Pattern
**Use Case:** Cache event notifications

```java
public interface CacheEventListener<K, V> {
    void onCacheHit(K key, V value);
    void onCacheMiss(K key);
    void onEviction(K key, V value);
    void onExpiration(K key, V value);
}

public class ObservableCache<K, V> {
    private final ConcurrentLRUCache<K, V> cache;
    private final List<CacheEventListener<K, V>> listeners = new CopyOnWriteArrayList<>();
    
    public void addListener(CacheEventListener<K, V> listener) {
        listeners.add(listener);
    }
    
    public V get(K key) {
        V value = cache.get(key);
        if (value != null) {
            notifyHit(key, value);
        } else {
            notifyMiss(key);
        }
        return value;
    }
    
    private void notifyHit(K key, V value) {
        listeners.forEach(listener -> listener.onCacheHit(key, value));
    }
}
```

### 5. Factory Pattern
**Use Case:** Creating different cache configurations

```java
public class CacheFactory {
    
    public static <K, V> Cache<K, V> createCache(CacheConfig config) {
        switch (config.getType()) {
            case LRU:
                return new ConcurrentLRUCache<>(config.getMaxSize(), config.getMaxMemory());
            case LFU:
                return new LFUCache<>(config.getMaxSize());
            case FIFO:
                return new FIFOCache<>(config.getMaxSize());
            default:
                throw new IllegalArgumentException("Unknown cache type: " + config.getType());
        }
    }
    
    public static <K, V> Cache<K, V> createProductionCache(String environment) {
        CacheConfig config = loadConfiguration(environment);
        Cache<K, V> baseCache = createCache(config);
        
        // Add production decorators
        baseCache = new MetricsCache<>(baseCache);
        baseCache = new LoggingCache<>(baseCache);
        baseCache = new CircuitBreakerCache<>(baseCache);
        
        return baseCache;
    }
}
```

## Performance Optimization Patterns

### 1. Copy-on-Write Pattern
**Use Case:** Handling collections in cache values

```java
public class CopyOnWriteValue<T> {
    private volatile List<T> items;
    
    public List<T> getItems() {
        return items; // Safe to return reference - immutable view
    }
    
    public void addItem(T item) {
        synchronized (this) {
            List<T> newItems = new ArrayList<>(items);
            newItems.add(item);
            this.items = Collections.unmodifiableList(newItems);
        }
    }
}
```

### 2. Batching Pattern
**Use Case:** Reducing lock contention for write operations

```java
public class BatchingCache<K, V> {
    private final ConcurrentLRUCache<K, V> cache;
    private final BlockingQueue<CacheOperation<K, V>> operationQueue;
    private final ScheduledExecutorService batchProcessor;
    
    public void putAsync(K key, V value) {
        operationQueue.offer(new PutOperation<>(key, value));
    }
    
    private void processBatch() {
        List<CacheOperation<K, V>> batch = new ArrayList<>();
        operationQueue.drainTo(batch, 100); // Process up to 100 operations
        
        if (!batch.isEmpty()) {
            // Acquire write lock once for entire batch
            cache.writeLock().lock();
            try {
                batch.forEach(CacheOperation::execute);
            } finally {
                cache.writeLock().unlock();
            }
        }
    }
}
```

### 3. Segmented Locking Pattern
**Use Case:** Reducing lock contention for high-concurrency scenarios

```java
public class SegmentedLRUCache<K, V> {
    private final ConcurrentLRUCache<K, V>[] segments;
    private final int segmentMask;
    
    @SuppressWarnings("unchecked")
    public SegmentedLRUCache(int segmentCount, int capacityPerSegment) {
        this.segments = new ConcurrentLRUCache[segmentCount];
        this.segmentMask = segmentCount - 1;
        
        for (int i = 0; i < segmentCount; i++) {
            segments[i] = new ConcurrentLRUCache<>(capacityPerSegment);
        }
    }
    
    private ConcurrentLRUCache<K, V> getSegment(K key) {
        int hash = key.hashCode();
        return segments[hash & segmentMask];
    }
    
    public V get(K key) {
        return getSegment(key).get(key);
    }
    
    public void put(K key, V value) {
        getSegment(key).put(key, value);
    }
}
```

## Integration Patterns

### 1. Cache-Aside Pattern
**Most common pattern - application manages cache**

```java
@Service
public class UserService {
    private final UserRepository userRepository;
    private final ConcurrentLRUCache<Long, User> userCache;
    
    public User getUser(Long userId) {
        // Try cache first
        User user = userCache.get(userId);
        if (user == null) {
            // Cache miss - load from database
            user = userRepository.findById(userId);
            if (user != null) {
                userCache.put(userId, user);
            }
        }
        return user;
    }
    
    public void updateUser(User user) {
        userRepository.save(user);
        userCache.put(user.getId(), user); // Update cache
    }
    
    public void deleteUser(Long userId) {
        userRepository.deleteById(userId);
        userCache.remove(userId); // Invalidate cache
    }
}
```

### 2. Write-Through Pattern
**Cache is updated synchronously with data store**

```java
public class WriteThroughCache<K, V> {
    private final ConcurrentLRUCache<K, V> cache;
    private final DataStore<K, V> dataStore;
    
    public V get(K key) {
        V value = cache.get(key);
        if (value == null) {
            value = dataStore.load(key);
            if (value != null) {
                cache.put(key, value);
            }
        }
        return value;
    }
    
    public void put(K key, V value) {
        // Write to both cache and data store
        dataStore.save(key, value);
        cache.put(key, value);
    }
}
```

### 3. Write-Behind Pattern
**Asynchronous write to data store**

```java
public class WriteBehindCache<K, V> {
    private final ConcurrentLRUCache<K, V> cache;
    private final DataStore<K, V> dataStore;
    private final BlockingQueue<WriteOperation<K, V>> writeQueue;
    private final ScheduledExecutorService writeExecutor;
    
    public void put(K key, V value) {
        cache.put(key, value);
        writeQueue.offer(new WriteOperation<>(key, value));
    }
    
    private void processWrites() {
        List<WriteOperation<K, V>> operations = new ArrayList<>();
        writeQueue.drainTo(operations, 50);
        
        if (!operations.isEmpty()) {
            // Batch write to data store
            dataStore.saveBatch(operations);
        }
    }
}
```

### 4. Refresh-Ahead Pattern
**Proactive cache refresh before expiration**

```java
public class RefreshAheadCache<K, V> {
    private final ConcurrentLRUCache<K, RefreshableValue<V>> cache;
    private final DataLoader<K, V> dataLoader;
    private final ScheduledExecutorService refreshExecutor;
    
    public V get(K key) {
        RefreshableValue<V> cached = cache.get(key);
        
        if (cached == null) {
            // Cache miss - load synchronously
            V value = dataLoader.load(key);
            cache.put(key, new RefreshableValue<>(value));
            return value;
        }
        
        if (cached.shouldRefresh()) {
            // Asynchronous refresh
            refreshExecutor.submit(() -> refreshValue(key));
        }
        
        return cached.getValue();
    }
    
    private void refreshValue(K key) {
        try {
            V newValue = dataLoader.load(key);
            cache.put(key, new RefreshableValue<>(newValue));
        } catch (Exception e) {
            logger.warn("Failed to refresh cache key: " + key, e);
        }
    }
}
```

## Monitoring & Observability Patterns

### 1. Health Check Pattern
```java
@Component
public class CacheHealthIndicator implements HealthIndicator {
    private final ConcurrentLRUCache<?, ?> cache;
    
    @Override
    public Health health() {
        try {
            double hitRate = cache.getHitRate();
            long memoryUsage = cache.getMemoryUsage();
            
            Health.Builder builder = hitRate > 0.5 ? Health.up() : Health.down();
            
            return builder
                .withDetail("hitRate", hitRate)
                .withDetail("memoryUsage", memoryUsage)
                .withDetail("size", cache.size())
                .build();
        } catch (Exception e) {
            return Health.down().withException(e).build();
        }
    }
}
```

### 2. Circuit Breaker Pattern
```java
public class CircuitBreakerCache<K, V> implements Cache<K, V> {
    private final Cache<K, V> cache;
    private final CircuitBreaker circuitBreaker;
    
    @Override
    public V get(K key) {
        return circuitBreaker.executeSupplier(() -> cache.get(key));
    }
    
    @Override
    public void put(K key, V value) {
        circuitBreaker.executeRunnable(() -> cache.put(key, value));
    }
}
```

This architecture provides a robust foundation for building scalable, maintainable cache solutions that can adapt to various requirements and integrate seamlessly with existing systems.