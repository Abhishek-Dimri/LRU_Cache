package com.lru;

/**
 * Simple demonstration of LRU cache usage
 */
public class CacheDemo {
    
    public static void main(String[] args) {
        System.out.println("LRU Cache Demonstration");
        System.out.println("=======================\n");
        
        // Create a small cache for demonstration
        ConcurrentLRUCache<String, String> cache = new ConcurrentLRUCache<>(3);
        
        System.out.println("1. Adding items to cache (capacity: 3)");
        cache.put("user:1", "Alice");
        cache.put("user:2", "Bob");
        cache.put("user:3", "Charlie");
        
        System.out.printf("Cache size: %d\n", cache.size());
        System.out.printf("Memory usage: %d bytes\n\n", cache.getMemoryUsage());
        
        System.out.println("2. Accessing items (LRU order matters)");
        System.out.println("Getting user:1: " + cache.get("user:1"));
        System.out.println("Getting user:2: " + cache.get("user:2"));
        
        System.out.println("\n3. Adding new item (should evict least recently used)");
        cache.put("user:4", "David");
        
        System.out.println("Checking which user was evicted:");
        System.out.println("user:1 (accessed): " + (cache.get("user:1") != null ? "present" : "evicted"));
        System.out.println("user:2 (accessed): " + (cache.get("user:2") != null ? "present" : "evicted"));
        System.out.println("user:3 (not accessed): " + (cache.get("user:3") != null ? "present" : "evicted"));
        System.out.println("user:4 (newly added): " + (cache.get("user:4") != null ? "present" : "evicted"));
        
        System.out.println("\n4. Memory-aware cache demonstration");
        ConcurrentLRUCache<String, String> memoryCache = 
            new ConcurrentLRUCache<>(100, 2048); // 2KB memory limit
        
        // Add progressively larger items
        memoryCache.put("small", "data");
        memoryCache.put("medium", "A".repeat(500));
        memoryCache.put("large", "B".repeat(1000));
        
        System.out.printf("Memory cache size: %d items\n", memoryCache.size());
        System.out.printf("Memory usage: %d bytes\n", memoryCache.getMemoryUsage());
        
        // This should trigger memory-based eviction
        memoryCache.put("huge", "C".repeat(1500));
        
        System.out.printf("After adding huge item - Size: %d, Memory: %d bytes\n", 
                         memoryCache.size(), memoryCache.getMemoryUsage());
        
        System.out.println("\nDemonstration complete!");
    }
}