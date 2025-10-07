package com.lru;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.*;

/**
 * Focused performance comparison showing read performance advantages
 */
public class ReadPerformanceTest {
    
    private static final int CACHE_SIZE = 1000;
    private static final int NUM_READ_THREADS = 8;
    private static final int READS_PER_THREAD = 50000;
    private static final int KEY_RANGE = 100; // Small range for high hit rate
    
    public static void main(String[] args) throws InterruptedException {
        System.out.println("Read Performance Comparison");
        System.out.println("===========================");
        
        // Pre-populate both caches with same data
        ConcurrentLRUCache<Integer, String> concurrentCache = 
            new ConcurrentLRUCache<>(CACHE_SIZE);
        Map<Integer, String> synchronizedCache = 
            Collections.synchronizedMap(new HashMap<>());
            
        // Warm up both caches
        for (int i = 0; i < KEY_RANGE; i++) {
            String value = "value_" + i;
            concurrentCache.put(i, value);
            synchronizedCache.put(i, value);
        }
        
        System.out.println("Both caches pre-populated with " + KEY_RANGE + " items");
        System.out.println("Testing with " + NUM_READ_THREADS + " concurrent read threads");
        System.out.println("Each thread performing " + READS_PER_THREAD + " reads\n");
        
        // Test concurrent LRU cache
        long concurrentTime = benchmarkReads("Concurrent LRU Cache", 
            key -> concurrentCache.get(key));
            
        // Test synchronized cache
        long synchronizedTime = benchmarkReads("Synchronized HashMap", 
            key -> synchronizedCache.get(key));
            
        // Calculate speedup
        double speedup = (double) synchronizedTime / concurrentTime;
        
        System.out.println("\nPerformance Summary:");
        System.out.println("===================");
        System.out.printf("Concurrent LRU Cache: %.2f ms\n", concurrentTime / 1_000_000.0);
        System.out.printf("Synchronized HashMap: %.2f ms\n", synchronizedTime / 1_000_000.0);
        System.out.printf("Speedup: %.1fx faster reads\n", speedup);
        
        if (speedup >= 2.0) {
            System.out.println("✅ Achieved significant read performance improvement!");
        } else {
            System.out.println("⚠️  Performance gain is modest under this workload");
        }
    }
    
    private static long benchmarkReads(String name, 
                                     java.util.function.Function<Integer, String> reader) 
                                     throws InterruptedException {
        
        ExecutorService executor = Executors.newFixedThreadPool(NUM_READ_THREADS);
        CountDownLatch latch = new CountDownLatch(NUM_READ_THREADS);
        
        long startTime = System.nanoTime();
        
        for (int i = 0; i < NUM_READ_THREADS; i++) {
            final int threadId = i;
            executor.submit(() -> {
                Random random = new Random(threadId);
                
                for (int j = 0; j < READS_PER_THREAD; j++) {
                    int key = random.nextInt(KEY_RANGE);
                    reader.apply(key);
                }
                latch.countDown();
            });
        }
        
        latch.await();
        long endTime = System.nanoTime();
        long duration = endTime - startTime;
        
        executor.shutdown();
        
        double durationMs = duration / 1_000_000.0;
        double readsPerSecond = (NUM_READ_THREADS * READS_PER_THREAD) / (durationMs / 1000);
        
        System.out.printf("%s Results:\n", name);
        System.out.printf("  Duration: %.2f ms\n", durationMs);
        System.out.printf("  Throughput: %.0f reads/sec\n", readsPerSecond);
        
        return duration;
    }
}