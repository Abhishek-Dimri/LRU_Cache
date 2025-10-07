package com.lru;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Random;

/**
 * Comprehensive test suite for ConcurrentLRUCache
 */
public class ConcurrentLRUCacheTest {
    
    private ConcurrentLRUCache<String, String> cache;
    
    @BeforeEach
    void setUp() {
        cache = new ConcurrentLRUCache<>(3);
    }
    
    @Test
    @DisplayName("Basic put and get operations")
    void testBasicOperations() {
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        
        assertEquals("value1", cache.get("key1"));
        assertEquals("value2", cache.get("key2"));
        assertNull(cache.get("nonexistent"));
    }
    
    @Test
    @DisplayName("LRU eviction policy works correctly")
    void testLRUEviction() {
        // Fill cache to capacity
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        cache.put("key3", "value3");
        assertEquals(3, cache.size());
        
        // Access key1 to make it most recently used
        cache.get("key1");
        
        // Add new item - should evict key2 (least recently used)
        cache.put("key4", "value4");
        
        assertNotNull(cache.get("key1")); // still present
        assertNull(cache.get("key2"));    // evicted
        assertNotNull(cache.get("key3")); // still present
        assertNotNull(cache.get("key4")); // newly added
        assertEquals(3, cache.size());
    }
    
    @Test
    @DisplayName("Update existing key maintains LRU order")
    void testUpdateExistingKey() {
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        cache.put("key3", "value3");
        
        // Update existing key
        cache.put("key1", "updated_value1");
        
        assertEquals("updated_value1", cache.get("key1"));
        assertEquals(3, cache.size());
        
        // Add new item - key2 should be evicted (not key1 since it was updated)
        cache.put("key4", "value4");
        
        assertNotNull(cache.get("key1")); // updated, so most recent
        assertNull(cache.get("key2"));    // evicted
        assertNotNull(cache.get("key3"));
        assertNotNull(cache.get("key4"));
    }
    
    @Test
    @DisplayName("Remove operation works correctly")
    void testRemoveOperation() {
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        
        assertTrue(cache.remove("key1"));
        assertFalse(cache.remove("nonexistent"));
        
        assertNull(cache.get("key1"));
        assertEquals("value2", cache.get("key2"));
        assertEquals(1, cache.size());
    }
    
    @Test
    @DisplayName("Clear operation empties cache")
    void testClearOperation() {
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        
        cache.clear();
        
        assertTrue(cache.isEmpty());
        assertEquals(0, cache.size());
        assertNull(cache.get("key1"));
        assertNull(cache.get("key2"));
    }
    
    @Test
    @DisplayName("Memory-aware eviction works")
    void testMemoryAwareEviction() {
        // Create cache with small memory limit
        ConcurrentLRUCache<String, String> memoryCache = 
            new ConcurrentLRUCache<>(100, 1000); // 1KB limit
        
        // Add items until memory limit is approached
        String largeValue = "X".repeat(200); // ~400 bytes per item
        
        memoryCache.put("key1", largeValue);
        memoryCache.put("key2", largeValue);
        
        long memoryBefore = memoryCache.getMemoryUsage();
        assertTrue(memoryBefore > 0);
        
        // Adding this should trigger memory-based eviction
        memoryCache.put("key3", largeValue);
        
        // Should still be under memory limit due to eviction
        assertTrue(memoryCache.getMemoryUsage() <= 1000);
    }
    
    @Test
    @DisplayName("Concurrent access is thread-safe")
    void testConcurrentAccess() throws InterruptedException {
        int numThreads = 10;
        int operationsPerThread = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger successfulOperations = new AtomicInteger(0);
        
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                Random random = new Random(threadId);
                
                for (int j = 0; j < operationsPerThread; j++) {
                    String key = "thread" + threadId + "_key" + j;
                    String value = "thread" + threadId + "_value" + j;
                    
                    try {
                        if (random.nextBoolean()) {
                            cache.put(key, value);
                        } else {
                            cache.get(key);
                        }
                        successfulOperations.incrementAndGet();
                    } catch (Exception e) {
                        fail("Concurrent operation failed: " + e.getMessage());
                    }
                }
                latch.countDown();
            });
        }
        
        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        
        // All operations should complete without exceptions
        assertEquals(numThreads * operationsPerThread, successfulOperations.get());
        
        // Cache should be in a consistent state
        assertTrue(cache.size() <= 3); // respects capacity limit
        assertTrue(cache.size() >= 0); // no negative sizes
    }
    
    @Test
    @DisplayName("High-frequency concurrent reads perform well")
    void testConcurrentReadPerformance() throws InterruptedException {
        // Pre-populate cache
        cache.put("hot_key1", "value1");
        cache.put("hot_key2", "value2");
        cache.put("hot_key3", "value3");
        
        int numReaderThreads = 8;
        int readsPerThread = 10000;
        ExecutorService executor = Executors.newFixedThreadPool(numReaderThreads);
        CountDownLatch latch = new CountDownLatch(numReaderThreads);
        
        long startTime = System.nanoTime();
        
        for (int i = 0; i < numReaderThreads; i++) {
            executor.submit(() -> {
                Random random = new Random();
                String[] keys = {"hot_key1", "hot_key2", "hot_key3"};
                
                for (int j = 0; j < readsPerThread; j++) {
                    String key = keys[random.nextInt(keys.length)];
                    cache.get(key);
                }
                latch.countDown();
            });
        }
        
        latch.await();
        long endTime = System.nanoTime();
        
        executor.shutdown();
        
        double durationMs = (endTime - startTime) / 1_000_000.0;
        double readsPerSecond = (numReaderThreads * readsPerThread) / (durationMs / 1000);
        
        System.out.printf("Concurrent read performance: %.0f reads/sec\n", readsPerSecond);
        
        // Should handle significant read throughput
        assertTrue(readsPerSecond > 100000, "Read performance should exceed 100k ops/sec");
    }
    
    @Test
    @DisplayName("Mixed read-write workload maintains correctness")
    void testMixedWorkload() throws InterruptedException {
        int numThreads = 6;
        int operationsPerThread = 5000;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                Random random = new Random(threadId);
                
                for (int j = 0; j < operationsPerThread; j++) {
                    int keyNum = random.nextInt(20); // Limited key space for conflicts
                    String key = "key_" + keyNum;
                    String value = "thread" + threadId + "_value" + j;
                    
                    if (random.nextDouble() < 0.7) {
                        // 70% reads
                        cache.get(key);
                    } else {
                        // 30% writes
                        cache.put(key, value);
                    }
                }
                latch.countDown();
            });
        }
        
        latch.await(15, TimeUnit.SECONDS);
        executor.shutdown();
        
        // Verify cache is in consistent state
        assertTrue(cache.size() <= 3);
        assertFalse(cache.size() < 0);
        
        // All values should be retrievable
        for (int i = 0; i < cache.size(); i++) {
            // Cache should respond to gets without throwing exceptions
            assertDoesNotThrow(() -> cache.get("key_" + i));
        }
    }
    
    @Test
    @DisplayName("Edge case: zero capacity cache")
    void testZeroCapacityCache() {
        ConcurrentLRUCache<String, String> zeroCache = new ConcurrentLRUCache<>(0);
        
        zeroCache.put("key1", "value1");
        
        assertEquals(0, zeroCache.size());
        assertNull(zeroCache.get("key1"));
        assertTrue(zeroCache.isEmpty());
    }
    
    @Test
    @DisplayName("Edge case: single capacity cache")
    void testSingleCapacityCache() {
        ConcurrentLRUCache<String, String> singleCache = new ConcurrentLRUCache<>(1);
        
        singleCache.put("key1", "value1");
        assertEquals("value1", singleCache.get("key1"));
        
        singleCache.put("key2", "value2");
        assertNull(singleCache.get("key1")); // evicted
        assertEquals("value2", singleCache.get("key2"));
        assertEquals(1, singleCache.size());
    }
}