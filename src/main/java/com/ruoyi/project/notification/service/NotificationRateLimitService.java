package com.ruoyi.project.notification.service;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.project.notification.config.NotificationProperties;

/**
 * 开放接口基础限流：进程内固定窗口，分别按调用凭证与来源 IP 限制。
 *
 * <p>实现不依赖 Redis，避免公共接口在主密钥/Redis 异常时不可用；多实例部署时每个实例独立计数，
 * 生产应在反向代理层再叠加全局限流。</p>
 *
 * @author weishanHub
 */
@Component
public class NotificationRateLimitService
{
    private static final int CLEANUP_THRESHOLD = 5000;

    private final NotificationProperties properties;

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public NotificationRateLimitService(NotificationProperties properties)
    {
        this.properties = properties;
    }

    public void checkIp(String ip)
    {
        check("ip:" + (ip == null ? "unknown" : ip), properties.getRateLimit().getPerIp());
    }

    public void checkCredential(Long credentialId)
    {
        check("credential:" + credentialId, properties.getRateLimit().getPerCredential());
    }

    private void check(String key, int limit)
    {
        NotificationProperties.RateLimit config = properties.getRateLimit();
        if (config.getWindowSeconds() <= 0 || limit <= 0)
        {
            return;
        }
        long windowMillis = config.getWindowSeconds() * 1000L;
        if (!tryAcquire(key, limit, windowMillis))
        {
            throw new ServiceException("请求过于频繁，请稍后重试", 429);
        }
    }

    boolean tryAcquire(String key, int limit, long windowMillis)
    {
        cleanupIfNeeded(windowMillis);
        long now = System.currentTimeMillis();
        Window window = windows.computeIfAbsent(key, k -> new Window(now));
        synchronized (window)
        {
            if (now - window.start >= windowMillis)
            {
                window.start = now;
                window.count = 0;
            }
            if (window.count >= limit)
            {
                return false;
            }
            window.count++;
            return true;
        }
    }

    private void cleanupIfNeeded(long windowMillis)
    {
        if (windows.size() < CLEANUP_THRESHOLD)
        {
            return;
        }
        long expireBefore = System.currentTimeMillis() - windowMillis * 2;
        Iterator<Map.Entry<String, Window>> iterator = windows.entrySet().iterator();
        while (iterator.hasNext())
        {
            Map.Entry<String, Window> entry = iterator.next();
            if (entry.getValue().start < expireBefore)
            {
                iterator.remove();
            }
        }
    }

    private static final class Window
    {
        private long start;

        private int count;

        private Window(long start)
        {
            this.start = start;
        }
    }
}
