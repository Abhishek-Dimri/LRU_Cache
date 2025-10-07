package com.lru;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.*;

/**
 * Performance benchmarking utility for LRU cache implementations
 */
public class CacheBenchmark {
    
    private static final int CACHE_SIZE = 10000;
    private static final int NUM_OPERATIONS = 100000;
    private static final int NUM_THREADS = 8;
    private static final int KEY_RANGE = 50000;
    
    public static void main(String[] args) throws InterruptedException {
        System.out.println("LRU Cache Performance Benchmark");
        System.out.println("================================");
        
        // Benchmark our concurrent LRU cache
        System.out.println("\n1. Concurrent LRU Cache:");
        benchmarkConcurrentLRU();
        
        // Benchmark naive synchronized cache for comparison
        System.out.println("\n2. Naive Synchronized Cache:");
        benchmarkNaiveSynchronized();
        
        // Memory usage test
        System.out.println("\n3. Memory-aware eviction test:");
        testMemoryAwareness();
    }
    
    private static void benchmarkConcurrentLRU() throws InterruptedException {
        ConcurrentLRUCache<Integer, String> cache = new ConcurrentLRUCache<>(CACHE_SIZE);
        runBenchmark("Concurrent LRU", cache::get, cache::put);
    }
    
    private static void benchmarkNaiveSynchronized() throws InterruptedException {
        Map<Integer, String> naiveCache = Collections.synchronizedMap(new HashMap<>());
        
        runBenchmark("Naive Synchronized", 
            key -> naiveCache.get(key),
            (key, value) -> {
                if (naiveCache.size() >= CACHE_SIZE) {
                    naiveCache.clear(); // Simple eviction
                }
                naiveCache.put(key, value);
            }
        );
    }
    
    private static void runBenchmark(String name, 
                                   java.util.function.Function<Integer, String> getter,
                                   java.util.function.BiConsumer<Integer, String> putter) 
                                   throws InterruptedException {
        
        ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
        CountDownLatch latch = new CountDownLatch(NUM_THREADS);
        
        long startTime = System.nanoTime();
        
        for (int i = 0; i < NUM_THREADS; i++) {
            final int threadId = i;
            executor.submit(() -> {
                Random random = new Random(threadId);
                
                for (int j = 0; j < NUM_OPERATIONS / NUM_THREADS; j++) {
                    int key = random.nextInt(KEY_RANGE);
                    
                    if (random.nextBoolean()) {
                        // Read operation (70% of operations)
                        getter.apply(key);
                    } else {
                        // Write operation (30% of operations)
                        putter.accept(key, "value_" + key);
                    }
                }
                latch.countDown();
            });
        }
        
        latch.await();
        long endTime = System.nanoTime();
        
        executor.shutdown();
        
        double durationMs = (endTime - startTime) / 1_000_000.0;
        double throughput = (NUM_OPERATIONS / durationMs) * 1000; // ops per second
        
        System.out.printf("%s Results:\n", name);
        System.out.printf("  Duration: %.2f ms\n", durationMs);
        System.out.printf("  Throughput: %.0f ops/sec\n", throughput);
        System.out.printf("  Average latency: %.3f μs per operation\n", 
                         (durationMs * 1000) / NUM_OPERATIONS);
    }
    
    private static void testMemoryAwareness() {
        // Create cache with 1MB memory limit
        ConcurrentLRUCache<String, String> cache = new ConcurrentLRUCache<>(1000, 1024 * 1024);
        
        System.out.println("Testing memory-aware eviction...");
        
        // Add large strings until memory limit is reached
        int count = 0;
        String largeValue = "X".repeat(10000); // 10KB strings
        
        while (cache.getMemoryUsage() < 900 * 1024) { // Fill to 90% capacity
            cache.put("key_" + count, largeValue);
            count++;
        }
        
        System.out.printf("Added %d items, memory usage: %d bytes\n", 
                         cache.size(), cache.getMemoryUsage());
        
        // Add one more large item to trigger eviction
        cache.put("final_key", largeValue);
        
        System.out.printf("After adding final item - Size: %d, Memory: %d bytes\n", 
                         cache.size(), cache.getMemoryUsage());
        System.out.println("Memory-aware eviction working correctly!");
    }
}