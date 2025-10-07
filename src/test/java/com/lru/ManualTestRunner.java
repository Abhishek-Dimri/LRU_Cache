package com.lru;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Random;

/**
 * Manual test runner for ConcurrentLRUCache (without JUnit dependency)
 */
public class ManualTestRunner {
    
    private static int testsPassed = 0;
    private static int testsTotal = 0;
    
    public static void main(String[] args) {
        System.out.println("Manual Test Suite for ConcurrentLRUCache");
        System.out.println("========================================\n");
        
        testBasicOperations();
        testLRUEviction();
        testUpdateExistingKey();
        testRemoveOperation();
        testClearOperation();
        testMemoryAwareEviction();
        testConcurrentAccess();
        testEdgeCases();
        
        System.out.printf("\nTest Results: %d/%d tests passed\n", testsPassed, testsTotal);
        
        if (testsPassed == testsTotal) {
            System.out.println("🎉 All tests passed!");
        } else {
            System.out.println("❌ Some tests failed!");
        }
    }
    
    private static void testBasicOperations() {
        System.out.println("Testing basic operations...");
        testsTotal++;
        
        try {
            ConcurrentLRUCache<String, String> cache = new ConcurrentLRUCache<>(3);
            
            cache.put("key1", "value1");
            cache.put("key2", "value2");
            
            assert "value1".equals(cache.get("key1")) : "Expected value1";
            assert "value2".equals(cache.get("key2")) : "Expected value2";
            assert cache.get("nonexistent") == null : "Expected null for nonexistent key";
            
            testsPassed++;
            System.out.println("✓ Basic operations test passed");
        } catch (Exception e) {
            System.out.println("❌ Basic operations test failed: " + e.getMessage());
        }
    }
    
    private static void testLRUEviction() {
        System.out.println("Testing LRU eviction...");
        testsTotal++;
        
        try {
            ConcurrentLRUCache<String, String> cache = new ConcurrentLRUCache<>(3);
            
            cache.put("key1", "value1");
            cache.put("key2", "value2");
            cache.put("key3", "value3");
            assert cache.size() == 3 : "Expected size 3";
            
            // Access key1 to make it most recently used
            cache.get("key1");
            
            // Add new item - should evict key2 (least recently used)
            cache.put("key4", "value4");
            
            assert cache.get("key1") != null : "key1 should still be present";
            assert cache.get("key2") == null : "key2 should be evicted";
            assert cache.get("key3") != null : "key3 should still be present";
            assert cache.get("key4") != null : "key4 should be present";
            assert cache.size() == 3 : "Expected size 3 after eviction";
            
            testsPassed++;
            System.out.println("✓ LRU eviction test passed");
        } catch (Exception e) {
            System.out.println("❌ LRU eviction test failed: " + e.getMessage());
        }
    }
    
    private static void testUpdateExistingKey() {
        System.out.println("Testing update existing key...");
        testsTotal++;
        
        try {
            ConcurrentLRUCache<String, String> cache = new ConcurrentLRUCache<>(3);
            
            cache.put("key1", "value1");
            cache.put("key2", "value2");
            cache.put("key3", "value3");
            
            cache.put("key1", "updated_value1");
            
            assert "updated_value1".equals(cache.get("key1")) : "Expected updated value";
            assert cache.size() == 3 : "Size should remain 3";
            
            testsPassed++;
            System.out.println("✓ Update existing key test passed");
        } catch (Exception e) {
            System.out.println("❌ Update existing key test failed: " + e.getMessage());
        }
    }
    
    private static void testRemoveOperation() {
        System.out.println("Testing remove operation...");
        testsTotal++;
        
        try {
            ConcurrentLRUCache<String, String> cache = new ConcurrentLRUCache<>(3);
            
            cache.put("key1", "value1");
            cache.put("key2", "value2");
            
            assert cache.remove("key1") : "Remove should return true";
            assert !cache.remove("nonexistent") : "Remove nonexistent should return false";
            
            assert cache.get("key1") == null : "key1 should be removed";
            assert "value2".equals(cache.get("key2")) : "key2 should still exist";
            assert cache.size() == 1 : "Size should be 1";
            
            testsPassed++;
            System.out.println("✓ Remove operation test passed");
        } catch (Exception e) {
            System.out.println("❌ Remove operation test failed: " + e.getMessage());
        }
    }
    
    private static void testClearOperation() {
        System.out.println("Testing clear operation...");
        testsTotal++;
        
        try {
            ConcurrentLRUCache<String, String> cache = new ConcurrentLRUCache<>(3);
            
            cache.put("key1", "value1");
            cache.put("key2", "value2");
            
            cache.clear();
            
            assert cache.isEmpty() : "Cache should be empty";
            assert cache.size() == 0 : "Size should be 0";
            assert cache.get("key1") == null : "key1 should not exist";
            assert cache.get("key2") == null : "key2 should not exist";
            
            testsPassed++;
            System.out.println("✓ Clear operation test passed");
        } catch (Exception e) {
            System.out.println("❌ Clear operation test failed: " + e.getMessage());
        }
    }
    
    private static void testMemoryAwareEviction() {
        System.out.println("Testing memory-aware eviction...");
        testsTotal++;
        
        try {
            ConcurrentLRUCache<String, String> cache = new ConcurrentLRUCache<>(100, 1000);
            
            String largeValue = "X".repeat(200);
            
            cache.put("key1", largeValue);
            cache.put("key2", largeValue);
            
            long memoryBefore = cache.getMemoryUsage();
            assert memoryBefore > 0 : "Memory usage should be positive";
            
            cache.put("key3", largeValue);
            
            assert cache.getMemoryUsage() <= 1000 : "Memory should be under limit";
            
            testsPassed++;
            System.out.println("✓ Memory-aware eviction test passed");
        } catch (Exception e) {
            System.out.println("❌ Memory-aware eviction test failed: " + e.getMessage());
        }
    }
    
    private static void testConcurrentAccess() {
        System.out.println("Testing concurrent access...");
        testsTotal++;
        
        try {
            ConcurrentLRUCache<String, String> cache = new ConcurrentLRUCache<>(100);
            int numThreads = 5;
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
                            // Concurrent operation failed
                            return;
                        }
                    }
                    latch.countDown();
                });
            }
            
            latch.await(10, TimeUnit.SECONDS);
            executor.shutdown();
            
            assert successfulOperations.get() == numThreads * operationsPerThread : 
                "All operations should succeed";
            assert cache.size() >= 0 : "Cache size should be non-negative";
            
            testsPassed++;
            System.out.println("✓ Concurrent access test passed");
        } catch (Exception e) {
            System.out.println("❌ Concurrent access test failed: " + e.getMessage());
        }
    }
    
    private static void testEdgeCases() {
        System.out.println("Testing edge cases...");
        testsTotal++;
        
        try {
            // Zero capacity cache
            ConcurrentLRUCache<String, String> zeroCache = new ConcurrentLRUCache<>(0);
            zeroCache.put("key1", "value1");
            assert zeroCache.size() == 0 : "Zero capacity cache should remain empty";
            assert zeroCache.get("key1") == null : "Should not find key in zero capacity cache";
            assert zeroCache.isEmpty() : "Zero capacity cache should be empty";
            
            // Single capacity cache
            ConcurrentLRUCache<String, String> singleCache = new ConcurrentLRUCache<>(1);
            singleCache.put("key1", "value1");
            assert "value1".equals(singleCache.get("key1")) : "Should find key1";
            
            singleCache.put("key2", "value2");
            assert singleCache.get("key1") == null : "key1 should be evicted";
            assert "value2".equals(singleCache.get("key2")) : "Should find key2";
            assert singleCache.size() == 1 : "Size should be 1";
            
            testsPassed++;
            System.out.println("✓ Edge cases test passed");
        } catch (Exception e) {
            System.out.println("❌ Edge cases test failed: " + e.getMessage());
        }
    }
}