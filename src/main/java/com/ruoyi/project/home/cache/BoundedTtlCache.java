package com.ruoyi.project.home.cache;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * 进程内有界 TTL 缓存：按访问顺序淘汰，超过上限时移除最久未使用的条目。
 *
 * <p>只用于 IP 定位、城市天气和城市搜索这类可重新获取的数据；不缓存失败结果。
 * 多实例部署时每个实例独立缓存，失效窗口可接受。</p>
 *
 * @author weishanHub
 */
public class BoundedTtlCache<K, V>
{
    private final int maxSize;

    private final long ttlMillis;

    private final LongSupplier clock;

    private final LinkedHashMap<K, Entry<V>> entries = new LinkedHashMap<>(16, 0.75f, true);

    public BoundedTtlCache(int maxSize, long ttlMillis)
    {
        this(maxSize, ttlMillis, System::currentTimeMillis);
    }

    public BoundedTtlCache(int maxSize, long ttlMillis, LongSupplier clock)
    {
        this.maxSize = Math.max(0, maxSize);
        this.ttlMillis = Math.max(0L, ttlMillis);
        this.clock = clock;
    }

    public synchronized V get(K key)
    {
        if (key == null || ttlMillis == 0L)
        {
            return null;
        }
        Entry<V> entry = entries.get(key);
        if (entry == null)
        {
            return null;
        }
        if (entry.expireAt <= clock.getAsLong())
        {
            entries.remove(key);
            return null;
        }
        return entry.value;
    }

    public synchronized void put(K key, V value)
    {
        if (key == null || value == null || maxSize == 0 || ttlMillis == 0L)
        {
            return;
        }
        long now = clock.getAsLong();
        removeExpired(now);
        entries.put(key, new Entry<>(value, now + ttlMillis));
        while (entries.size() > maxSize)
        {
            Iterator<K> iterator = entries.keySet().iterator();
            if (!iterator.hasNext())
            {
                break;
            }
            iterator.next();
            iterator.remove();
        }
    }

    public synchronized int size()
    {
        return entries.size();
    }

    private void removeExpired(long now)
    {
        Iterator<Map.Entry<K, Entry<V>>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext())
        {
            if (iterator.next().getValue().expireAt <= now)
            {
                iterator.remove();
            }
        }
    }

    private static final class Entry<V>
    {
        private final V value;

        private final long expireAt;

        private Entry(V value, long expireAt)
        {
            this.value = value;
            this.expireAt = expireAt;
        }
    }
}
