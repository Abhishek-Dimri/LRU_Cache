package com.lru;

/**
 * Simple test to debug edge cases
 */
public class EdgeCaseDebugger {
    
    public static void main(String[] args) {
        System.out.println("Debugging Edge Cases");
        System.out.println("====================");
        
        // Test zero capacity cache
        System.out.println("\n1. Testing zero capacity cache:");
        ConcurrentLRUCache<String, String> zeroCache = new ConcurrentLRUCache<>(0);
        zeroCache.put("key1", "value1");
        System.out.println("Size after put: " + zeroCache.size());
        System.out.println("Get key1: " + zeroCache.get("key1"));
        System.out.println("Is empty: " + zeroCache.isEmpty());
        
        // Test single capacity cache  
        System.out.println("\n2. Testing single capacity cache:");
        ConcurrentLRUCache<String, String> singleCache = new ConcurrentLRUCache<>(1);
        singleCache.put("key1", "value1");
        System.out.println("After putting key1:");
        System.out.println("  Size: " + singleCache.size());
        System.out.println("  Get key1: " + singleCache.get("key1"));
        
        singleCache.put("key2", "value2");
        System.out.println("After putting key2:");
        System.out.println("  Size: " + singleCache.size());
        System.out.println("  Get key1: " + singleCache.get("key1"));
        System.out.println("  Get key2: " + singleCache.get("key2"));
        
        System.out.println("\nDebug completed!");
    }
}