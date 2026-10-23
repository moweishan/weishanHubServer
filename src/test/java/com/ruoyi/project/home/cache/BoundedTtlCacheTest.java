package com.ruoyi.project.home.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * 有界 TTL 缓存：容量淘汰、访问顺序、过期。
 */
class BoundedTtlCacheTest
{
    @Test
    void cacheIsBoundedAndEvictsLeastRecentlyUsed()
    {
        AtomicLong now = new AtomicLong(1000L);
        BoundedTtlCache<String, String> cache = new BoundedTtlCache<>(2, 10_000L, now::get);
        cache.put("a", "A");
        cache.put("b", "B");
        assertEquals("A", cache.get("a"));
        cache.put("c", "C");
        assertNull(cache.get("b"));
        assertEquals("A", cache.get("a"));
        assertEquals("C", cache.get("c"));
        assertEquals(2, cache.size());
    }

    @Test
    void expiredEntryIsNotReturned()
    {
        AtomicLong now = new AtomicLong(1000L);
        BoundedTtlCache<String, String> cache = new BoundedTtlCache<>(2, 500L, now::get);
        cache.put("a", "A");
        now.addAndGet(501L);
        assertNull(cache.get("a"));
    }

    @Test
    void zeroCapacityDisablesCaching()
    {
        BoundedTtlCache<String, String> cache = new BoundedTtlCache<>(0, 10_000L);
        cache.put("a", "A");
        assertNull(cache.get("a"));
    }
}
